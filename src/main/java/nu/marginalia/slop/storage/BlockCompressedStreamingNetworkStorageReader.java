package nu.marginalia.slop.storage;

import com.github.luben.zstd.Zstd;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.time.Duration;

/** StorageReader that fetches the data section of a block-compressed file as a single
 *  HTTP stream, decompressing blocks sequentially as they arrive.  This avoids the
 *  per-block round-trip overhead of {@link BlockCompressedNetworkStorageReader} at the
 *  cost of not supporting {@link #seek}.
 *
 *  <p>On open: two range requests are issued (footer suffix, block index), identical to
 *  the seeking reader.  A third request then opens a streaming GET for the entire data
 *  section (bytes 0 to indexOffset-1), which is kept open for the lifetime of this reader.
 *  Each call to nextBlock() reads the next block's compressed bytes from that stream.</p>
 *
 *  <p>Forward {@link #skip} across block boundaries is supported efficiently: compressed
 *  bytes for skipped blocks are consumed from the stream without decompressing them.</p>
 *
 *  @see BlockCompressedStorageWriter for the file format description.
 *  @see BlockCompressedNetworkStorageReader for the seeking variant.
 */
public class BlockCompressedStreamingNetworkStorageReader implements StorageReader {
    private static final int FOOTER_SIZE = 20;

    private final HttpClient client;

    private final int blockSize;
    private final int numBlocks;
    private final int[] compressedSizes;

    private int currentBlockIdx = -1;

    // Direct buffers required by Zstd JNI
    private final ByteBuffer compressedBB;
    private final ByteBuffer decompressedBuf;

    // Streaming connection over the data section; null when numBlocks == 0
    private final ReadableByteChannel dataChannel;

    public BlockCompressedStreamingNetworkStorageReader(URL url) throws IOException {
        URI uri = URI.create(url.toString());
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        try {
            // Fetch footer (suffix range) to get index offset, block size, and block count
            byte[] footerBytes = fetchBytes(client, uri, "bytes=-" + FOOTER_SIZE);
            ByteBuffer footer = ByteBuffer.wrap(footerBytes).order(ByteOrder.LITTLE_ENDIAN);

            long indexOffset = footer.getLong();
            int bs = footer.getInt();
            int nb = footer.getInt();
            int magic = footer.getInt();

            if (magic != BlockCompressedStorageWriter.MAGIC) {
                throw new IOException("Invalid magic in block-compressed file at " + url
                        + ": 0x" + Integer.toHexString(magic));
            }

            int[] cs = new int[nb];

            if (nb > 0) {
                // Fetch just the index section
                long indexEnd = indexOffset + (long) nb * Integer.BYTES - 1;
                byte[] indexBytes = fetchBytes(client, uri, "bytes=" + indexOffset + "-" + indexEnd);
                ByteBuffer indexBuf = ByteBuffer.wrap(indexBytes).order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i < nb; i++) {
                    cs[i] = indexBuf.getInt();
                }
            }

            int maxCompressed = (int) Zstd.compressBound(bs);
            ByteBuffer dBuf = ByteBuffer.allocateDirect(bs).order(ByteOrder.LITTLE_ENDIAN);
            dBuf.limit(0);

            // Open streaming connection over the data section
            ReadableByteChannel channel = null;
            if (nb > 0) {
                long dataEnd = indexOffset - 1;
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(uri)
                        .header("Range", "bytes=0-" + dataEnd)
                        .GET()
                        .build();
                HttpResponse<InputStream> response;
                try {
                    response = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
                } catch (InterruptedException e) {
                    throw new IOException("Interrupted while opening data stream from " + uri, e);
                }
                if (response.statusCode() != 206) {
                    try (InputStream body = response.body()) {
                        throw new IOException("Server does not support range requests for " + uri
                                + " (data stream -> HTTP " + response.statusCode() + ")");
                    }
                }
                channel = Channels.newChannel(response.body());
            }

            this.client = client;
            this.blockSize = bs;
            this.numBlocks = nb;
            this.compressedSizes = cs;
            this.compressedBB = ByteBuffer.allocateDirect(maxCompressed);
            this.decompressedBuf = dBuf;
            this.dataChannel = channel;
        } catch (Throwable t) {
            client.close();
            throw t;
        }

        if (numBlocks > 0) {
            loadNextBlock();
        }
    }

    private static byte[] fetchBytes(HttpClient client, URI uri, String rangeHeader) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Range", rangeHeader)
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 206) {
                throw new IOException("Server does not support range requests for " + uri
                        + " (Range: " + rangeHeader + " -> HTTP " + response.statusCode() + ")");
            }
            return response.body();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to fetch range " + rangeHeader + " from " + uri, e);
        }
    }

    private void loadNextBlock() throws IOException {
        int blockIdx = currentBlockIdx + 1;
        if (blockIdx >= numBlocks) {
            throw new IOException("No more blocks to read");
        }

        int compressedSize = compressedSizes[blockIdx];
        compressedBB.clear();
        compressedBB.limit(compressedSize);
        while (compressedBB.hasRemaining()) {
            int n = dataChannel.read(compressedBB);
            if (n < 0) throw new IOException("Unexpected end of stream while loading block " + blockIdx);
        }
        compressedBB.flip();

        decompressedBuf.clear();
        long result = Zstd.decompress(decompressedBuf, compressedBB);
        if (Zstd.isError(result)) {
            throw new IOException("Zstd decompression error: " + Zstd.getErrorName(result));
        }
        decompressedBuf.flip();

        currentBlockIdx = blockIdx;
    }

    /** Consume compressed bytes for blocks up to (but not including) targetBlockIdx
     *  from the stream, then load that block.  This lets skip() cross block boundaries
     *  without decompressing intermediate blocks.
     *
     *  compressedBB is reused as a scratch buffer here; this is safe because
     *  compressBound(blockSize) is an upper bound on any block's compressed size. */
    private void advanceToBlock(int targetBlockIdx) throws IOException {
        for (int i = currentBlockIdx + 1; i < targetBlockIdx; i++) {
            int toDiscard = compressedSizes[i];
            compressedBB.clear();
            while (toDiscard > 0) {
                compressedBB.limit(Math.min(toDiscard, compressedBB.capacity()));
                int n = dataChannel.read(compressedBB);
                if (n < 0) throw new IOException("Unexpected end of stream while discarding block " + i);
                toDiscard -= n;
                compressedBB.clear();
            }
        }
        // Update currentBlockIdx so loadNextBlock() reads the correct compressedSizes entry
        currentBlockIdx = targetBlockIdx - 1;
        loadNextBlock();
    }

    @Override
    public byte getByte() throws IOException {
        if (!decompressedBuf.hasRemaining()) loadNextBlock();
        return decompressedBuf.get();
    }

    @Override
    public short getShort() throws IOException {
        if (decompressedBuf.remaining() < Short.BYTES) loadNextBlock();
        return decompressedBuf.getShort();
    }

    @Override
    public char getChar() throws IOException {
        if (decompressedBuf.remaining() < Character.BYTES) loadNextBlock();
        return decompressedBuf.getChar();
    }

    @Override
    public int getInt() throws IOException {
        if (decompressedBuf.remaining() < Integer.BYTES) loadNextBlock();
        return decompressedBuf.getInt();
    }

    @Override
    public long getLong() throws IOException {
        if (decompressedBuf.remaining() < Long.BYTES) loadNextBlock();
        return decompressedBuf.getLong();
    }

    @Override
    public float getFloat() throws IOException {
        if (decompressedBuf.remaining() < Float.BYTES) loadNextBlock();
        return decompressedBuf.getFloat();
    }

    @Override
    public double getDouble() throws IOException {
        if (decompressedBuf.remaining() < Double.BYTES) loadNextBlock();
        return decompressedBuf.getDouble();
    }

    @Override
    public void getBytes(byte[] bytes) throws IOException {
        getBytes(bytes, 0, bytes.length);
    }

    @Override
    public void getBytes(byte[] bytes, int offset, int length) throws IOException {
        if (decompressedBuf.remaining() >= length) {
            decompressedBuf.get(bytes, offset, length);
        } else {
            int totalToRead = length;
            while (totalToRead > 0) {
                if (!decompressedBuf.hasRemaining()) loadNextBlock();
                int toRead = Math.min(decompressedBuf.remaining(), totalToRead);
                decompressedBuf.get(bytes, offset + (length - totalToRead), toRead);
                totalToRead -= toRead;
            }
        }
    }

    @Override
    public void getBytes(ByteBuffer data) throws IOException {
        if (data.remaining() <= decompressedBuf.remaining()) {
            int lim = decompressedBuf.limit();
            decompressedBuf.limit(decompressedBuf.position() + data.remaining());
            data.put(decompressedBuf);
            decompressedBuf.limit(lim);
        } else {
            while (data.hasRemaining()) {
                if (!decompressedBuf.hasRemaining()) loadNextBlock();
                int lim = decompressedBuf.limit();
                decompressedBuf.limit(Math.min(decompressedBuf.position() + data.remaining(), lim));
                data.put(decompressedBuf);
                decompressedBuf.limit(lim);
            }
        }
    }

    @Override
    public void getInts(int[] ints) throws IOException {
        if (decompressedBuf.remaining() >= ints.length * Integer.BYTES) {
            for (int i = 0; i < ints.length; i++) ints[i] = decompressedBuf.getInt();
        } else {
            for (int i = 0; i < ints.length; i++) ints[i] = getInt();
        }
    }

    @Override
    public void getLongs(long[] longs) throws IOException {
        if (decompressedBuf.remaining() >= longs.length * Long.BYTES) {
            for (int i = 0; i < longs.length; i++) longs[i] = decompressedBuf.getLong();
        } else {
            for (int i = 0; i < longs.length; i++) longs[i] = getLong();
        }
    }

    @Override
    public void skip(long bytes, int stepSize) throws IOException {
        long toSkip = bytes * stepSize;
        long pos = position();
        long targetPos = pos + toSkip;
        int targetBlock = (int) (targetPos / blockSize);
        int offsetInBlock = (int) (targetPos % blockSize);

        if (targetBlock == currentBlockIdx) {
            decompressedBuf.position(offsetInBlock);
        } else if (targetBlock > currentBlockIdx) {
            advanceToBlock(targetBlock);
            decompressedBuf.position(offsetInBlock);
        } else {
            throw new IOException("Cannot skip backwards in a streaming reader");
        }
    }

    @Override
    public void seek(long position, int stepSize) {
        throw new UnsupportedOperationException(
                "seek() is not supported by BlockCompressedStreamingNetworkStorageReader; "
                + "use BlockCompressedNetworkStorageReader for random access");
    }

    @Override
    public long position() throws IOException {
        if (currentBlockIdx < 0) return 0;
        return (long) currentBlockIdx * blockSize + decompressedBuf.position();
    }

    @Override
    public boolean hasRemaining() throws IOException {
        return decompressedBuf.hasRemaining() || currentBlockIdx < numBlocks - 1;
    }

    @Override
    public boolean isDirect() {
        return false;
    }

    @Override
    public void close() throws IOException {
        try {
            if (dataChannel != null) dataChannel.close();
        } finally {
            client.close();
        }
    }
}
