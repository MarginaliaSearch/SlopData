package nu.marginalia.slop.storage;

import com.github.luben.zstd.Zstd;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** StorageReader that decompresses data from fixed-size Zstd-compressed blocks,
 *  enabling random-access seeking via a block offset index stored at the end of the file.
 *
 *  @see BlockCompressedStorageWriter for the file format description.
 */
public class BlockCompressedStorageReader implements StorageReader {
    private static final int FOOTER_SIZE = 20;

    private final int blockSize;
    private final int numBlocks;
    private final long[] blockOffsets;    // file byte offset of each block's compressed data
    private final int[] compressedSizes;

    private final FileChannel channel;
    private int currentBlockIdx = -1;

    // Direct buffers required by Zstd JNI
    private final ByteBuffer compressedBB;    // holds compressed bytes read from file
    private final ByteBuffer decompressedBuf; // little-endian, holds current decompressed block

    public BlockCompressedStorageReader(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new NoSuchColumnException(path.toString());
        }

        FileChannel ch = FileChannel.open(path, StandardOpenOption.READ);
        try {
            long fileSize = ch.size();
            if (fileSize < FOOTER_SIZE) {
                throw new IOException("File is too small to contain a valid block-compressed footer: " + path);
            }

            // Read 20-byte footer
            ByteBuffer footer = ByteBuffer.allocate(FOOTER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
            ch.position(fileSize - FOOTER_SIZE);
            readFully(ch, footer);
            footer.flip();

            long indexOffset = footer.getLong();
            int bs = footer.getInt();
            int nb = footer.getInt();
            int magic = footer.getInt();

            if (magic != BlockCompressedStorageWriter.MAGIC) {
                throw new IOException("Invalid magic in block-compressed file: 0x"
                        + Integer.toHexString(magic));
            }

            int[] cs = new int[nb];
            long[] bo = new long[nb];

            if (nb > 0) {
                ByteBuffer indexBuf = ByteBuffer.allocate(nb * Integer.BYTES)
                        .order(ByteOrder.LITTLE_ENDIAN);
                ch.position(indexOffset);
                readFully(ch, indexBuf);
                indexBuf.flip();

                long offset = 0;
                for (int i = 0; i < nb; i++) {
                    bo[i] = offset;
                    cs[i] = indexBuf.getInt();
                    offset += cs[i];
                }
            }

            int maxCompressed = (int) Zstd.compressBound(bs);

            // All init succeeded; assign finals
            this.channel = ch;
            this.blockSize = bs;
            this.numBlocks = nb;
            this.compressedSizes = cs;
            this.blockOffsets = bo;
            this.compressedBB = ByteBuffer.allocateDirect(maxCompressed);
            this.decompressedBuf = ByteBuffer.allocateDirect(bs).order(ByteOrder.LITTLE_ENDIAN);
            this.decompressedBuf.limit(0);
        } catch (Throwable t) {
            ch.close();
            throw t;
        }

        if (numBlocks > 0) {
            loadBlock(0);
        }
    }

    private static void readFully(FileChannel ch, ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
            int n = ch.read(buf);
            if (n < 0) throw new IOException("Unexpected end of file");
        }
    }

    private void loadBlock(int blockIdx) throws IOException {
        int compressedSize = compressedSizes[blockIdx];
        channel.position(blockOffsets[blockIdx]);

        compressedBB.clear();
        compressedBB.limit(compressedSize);
        readFully(channel, compressedBB);
        compressedBB.flip();

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
        if (!decompressedBuf.hasRemaining()) {
            nextBlock();
        }
        return decompressedBuf.get();
    }

    @Override
    public short getShort() throws IOException {
        if (decompressedBuf.remaining() < Short.BYTES) {
            nextBlock();
        }
        return decompressedBuf.getShort();
    }

    @Override
    public char getChar() throws IOException {
        if (decompressedBuf.remaining() < Character.BYTES) {
            nextBlock();
        }
        return decompressedBuf.getChar();
    }

    @Override
    public int getInt() throws IOException {
        if (decompressedBuf.remaining() < Integer.BYTES) {
            nextBlock();
        }
        return decompressedBuf.getInt();
    }

    @Override
    public long getLong() throws IOException {
        if (decompressedBuf.remaining() < Long.BYTES) {
            nextBlock();
        }
        return decompressedBuf.getLong();
    }

    @Override
    public float getFloat() throws IOException {
        if (decompressedBuf.remaining() < Float.BYTES) {
            nextBlock();
        }
        return decompressedBuf.getFloat();
    }

    @Override
    public double getDouble() throws IOException {
        if (decompressedBuf.remaining() < Double.BYTES) {
            nextBlock();
        }
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
                if (!decompressedBuf.hasRemaining()) {
                    nextBlock();
                }
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
                if (!decompressedBuf.hasRemaining()) {
                    nextBlock();
                }
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
            for (int i = 0; i < ints.length; i++) {
                ints[i] = decompressedBuf.getInt();
            }
        } else {
            for (int i = 0; i < ints.length; i++) {
                ints[i] = getInt();
            }
        }
    }

    @Override
    public void getLongs(long[] longs) throws IOException {
        if (decompressedBuf.remaining() >= longs.length * Long.BYTES) {
            for (int i = 0; i < longs.length; i++) {
                longs[i] = decompressedBuf.getLong();
            }
        } else {
            for (int i = 0; i < longs.length; i++) {
                longs[i] = getLong();
            }
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
        channel.close();
    }
}
