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
import java.time.Duration;

/** StorageReader that fetches individual Zstd-compressed blocks from an HTTP server
 *  using range requests, enabling random-access seeking without downloading the full file.
 *
 *  <p>On open: two range requests are issued, one for the 20-byte footer (suffix range),
 *  one for the block index. Each subsequent block load issues one range request for
 *  the compressed bytes of that block only.</p>
 *
 *  <p>The server must support HTTP range requests (RFC 7233). A 206 response is required;
 *  any other status causes an {@link IOException}.</p>
 *
 *  @see BlockCompressedStorageWriter for the file format description.
 */
public class BlockCompressedNetworkStorageReader implements StorageReader {
    private static final int FOOTER_SIZE = 20;

    private final URI uri;
    private final HttpClient client;

    private final int blockSize;
    private final int numBlocks;
    private final long[] blockOffsets;
    private final int[] compressedSizes;

    private int currentBlockIdx = -1;

    // Direct buffers required by Zstd JNI
    private final ByteBuffer compressedBB;
    private final ByteBuffer decompressedBuf;

    public BlockCompressedNetworkStorageReader(URL url) throws IOException {
        URI uri = URI.create(url.toString());
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        try {
            // Fetch the last 20 bytes via suffix range; the Content-Range response header
            // also gives us the total file size without a separate HEAD request.
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
            long[] bo = new long[nb];

            if (nb > 0) {
                // Fetch index section
                long indexEnd = indexOffset + (long) nb * Integer.BYTES - 1;
                byte[] indexBytes = fetchBytes(client, uri, "bytes=" + indexOffset + "-" + indexEnd);
                ByteBuffer indexBuf = ByteBuffer.wrap(indexBytes).order(ByteOrder.LITTLE_ENDIAN);

                long offset = 0;
                for (int i = 0; i < nb; i++) {
                    bo[i] = offset;
                    cs[i] = indexBuf.getInt();
                    offset += cs[i];
                }
            }

            int maxCompressed = (int) Zstd.compressBound(bs);
            ByteBuffer dBuf = ByteBuffer.allocateDirect(bs).order(ByteOrder.LITTLE_ENDIAN);
            dBuf.limit(0);

            // All init succeeded; assign finals
            this.uri = uri;
            this.client = client;
            this.blockSize = bs;
            this.numBlocks = nb;
            this.compressedSizes = cs;
            this.blockOffsets = bo;
            this.compressedBB = ByteBuffer.allocateDirect(maxCompressed);
            this.decompressedBuf = dBuf;
        } catch (Throwable t) {
            client.close();
            throw t;
        }

        if (numBlocks > 0) {
            loadBlock(0);
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

    private void loadBlock(int blockIdx) throws IOException {
        int compressedSize = compressedSizes[blockIdx];
        long start = blockOffsets[blockIdx];
        long end = start + compressedSize - 1;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Range", "bytes=" + start + "-" + end)
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 206) {
                try (InputStream body = response.body()) {
                    throw new IOException("Server does not support range requests for " + uri
                            + " (block " + blockIdx + " -> HTTP " + response.statusCode() + ")");
                }
            }
            compressedBB.clear();
            compressedBB.limit(compressedSize);
            try (InputStream body = response.body();
                 var channel = Channels.newChannel(body)) {
                while (compressedBB.hasRemaining()) {
                    int n = channel.read(compressedBB);
                    if (n < 0) throw new IOException("Unexpected end of stream for block " + blockIdx);
                }
            }
            compressedBB.flip();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to fetch block " + blockIdx + " from " + uri, e);
        }

        decompressedBuf.clear();
        long result = Zstd.decompress(decompressedBuf, compressedBB);
        if (Zstd.isError(result)) {
            throw new IOException("Zstd decompression error: " + Zstd.getErrorName(result));
        }
        decompressedBuf.flip();

        currentBlockIdx = blockIdx;
    }

    private void nextBlock() throws IOException {
        if (currentBlockIdx + 1 >= numBlocks) {
            throw new IOException("No more blocks to read");
        }
        loadBlock(currentBlockIdx + 1);
    }

    private void seekBytes(long bytePos) throws IOException {
        int blockIdx = (int) (bytePos / blockSize);
        int offsetInBlock = (int) (bytePos % blockSize);

        if (blockIdx != currentBlockIdx) {
            loadBlock(blockIdx);
        }
        decompressedBuf.position(offsetInBlock);
    }

    @Override
    public byte getByte() throws IOException {
        if (!decompressedBuf.hasRemaining()) nextBlock();
        return decompressedBuf.get();
    }

    @Override
    public short getShort() throws IOException {
        if (decompressedBuf.remaining() < Short.BYTES) nextBlock();
        return decompressedBuf.getShort();
    }

    @Override
    public char getChar() throws IOException {
        if (decompressedBuf.remaining() < Character.BYTES) nextBlock();
        return decompressedBuf.getChar();
    }

    @Override
    public int getInt() throws IOException {
        if (decompressedBuf.remaining() < Integer.BYTES) nextBlock();
        return decompressedBuf.getInt();
    }

    @Override
    public long getLong() throws IOException {
        if (decompressedBuf.remaining() < Long.BYTES) nextBlock();
        return decompressedBuf.getLong();
    }

    @Override
    public float getFloat() throws IOException {
        if (decompressedBuf.remaining() < Float.BYTES) nextBlock();
        return decompressedBuf.getFloat();
    }

    @Override
    public double getDouble() throws IOException {
        if (decompressedBuf.remaining() < Double.BYTES) nextBlock();
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
                if (!decompressedBuf.hasRemaining()) nextBlock();
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
                if (!decompressedBuf.hasRemaining()) nextBlock();
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
        if (toSkip <= decompressedBuf.remaining()) {
            decompressedBuf.position(decompressedBuf.position() + (int) toSkip);
        } else {
            seekBytes(position() + toSkip);
        }
    }

    @Override
    public void seek(long position, int stepSize) throws IOException {
        seekBytes(position * stepSize);
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
        client.close();
    }
}
