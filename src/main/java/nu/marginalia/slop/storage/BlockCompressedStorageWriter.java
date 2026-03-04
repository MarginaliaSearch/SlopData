package nu.marginalia.slop.storage;

import com.github.luben.zstd.Zstd;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/** StorageWriter that compresses data in fixed-size blocks using Zstd.
 *  A block offset index is appended to the file on close, enabling random-access
 *  seeking in the corresponding {@link BlockCompressedStorageReader}.
 *
 *  <p>File format:</p>
 *  <pre>
 *  [Data section]
 *    block_0_compressed_bytes
 *    block_1_compressed_bytes
 *    ...
 *  [Index section]  &lt;- starts at index_offset
 *    compressedSizes[0] : int32 little-endian
 *    ...
 *  [Footer: 20 bytes]
 *    index_offset : int64 little-endian
 *    block_size   : int32 little-endian
 *    num_blocks   : int32 little-endian
 *    MAGIC        : int32 = 0x535A5342
 *  </pre>
 */
public class BlockCompressedStorageWriter implements StorageWriter {
    static final int MAGIC = 0x535A5342;

    private final int blockSize;

    // Direct buffers required by Zstd JNI
    private final ByteBuffer buffer;       // blockSize, little-endian, accumulates data
    private final ByteBuffer compressedBB; // compressBound(blockSize), compression output

    private final List<Integer> blockSizes = new ArrayList<>();
    private final FileChannel channel;
    private long flushedLogicalBytes = 0;

    private final Path tempPath;
    private final Path destPath;

    public BlockCompressedStorageWriter(Path path, int blockSize) throws IOException {
        this.blockSize = blockSize;
        this.tempPath = path.resolveSibling(path.getFileName() + ".tmp");
        this.destPath = path;

        int maxCompressed = (int) Zstd.compressBound(blockSize);

        this.channel = FileChannel.open(tempPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        this.buffer = ByteBuffer.allocateDirect(blockSize).order(ByteOrder.LITTLE_ENDIAN);
        this.compressedBB = ByteBuffer.allocateDirect(maxCompressed);
    }

    private void flushBlock() throws IOException {
        buffer.flip();
        int uncompressedSize = buffer.remaining();
        if (uncompressedSize == 0) return;

        compressedBB.clear();
        long result = Zstd.compress(compressedBB, buffer, 3);
        if (Zstd.isError(result)) {
            throw new IOException("Zstd compression error: " + Zstd.getErrorName(result));
        }
        int compressedSize = (int) result;
        compressedBB.flip();

        while (compressedBB.hasRemaining()) {
            channel.write(compressedBB);
        }

        blockSizes.add(compressedSize);
        flushedLogicalBytes += uncompressedSize;
        buffer.clear();
    }

    @Override
    public void putByte(byte b) throws IOException {
        if (buffer.remaining() < Byte.BYTES) {
            flushBlock();
        }
        buffer.put(b);
    }

    @Override
    public void putShort(short s) throws IOException {
        if (buffer.remaining() < Short.BYTES) {
            flushBlock();
        }
        buffer.putShort(s);
    }

    @Override
    public void putChar(char c) throws IOException {
        if (buffer.remaining() < Character.BYTES) {
            flushBlock();
        }
        buffer.putChar(c);
    }

    @Override
    public void putInt(int i) throws IOException {
        if (buffer.remaining() < Integer.BYTES) {
            flushBlock();
        }
        buffer.putInt(i);
    }

    @Override
    public void putLong(long l) throws IOException {
        if (buffer.remaining() < Long.BYTES) {
            flushBlock();
        }
        buffer.putLong(l);
    }

    @Override
    public void putFloat(float f) throws IOException {
        if (buffer.remaining() < Float.BYTES) {
            flushBlock();
        }
        buffer.putFloat(f);
    }

    @Override
    public void putDouble(double d) throws IOException {
        if (buffer.remaining() < Double.BYTES) {
            flushBlock();
        }
        buffer.putDouble(d);
    }

    @Override
    public void putInts(int[] values) throws IOException {
        if (buffer.remaining() >= Integer.BYTES * values.length) {
            for (int value : values) {
                buffer.putInt(value);
            }
        } else {
            for (int value : values) {
                putInt(value);
            }
        }
    }

    @Override
    public void putLongs(long[] values) throws IOException {
        if (buffer.remaining() >= Long.BYTES * values.length) {
            for (long value : values) {
                buffer.putLong(value);
            }
        } else {
            for (long value : values) {
                putLong(value);
            }
        }
    }

    @Override
    public void putBytes(byte[] bytes) throws IOException {
        putBytes(bytes, 0, bytes.length);
    }

    @Override
    public void putBytes(byte[] bytes, int offset, int length) throws IOException {
        int totalToWrite = length;
        if (totalToWrite < buffer.remaining()) {
            buffer.put(bytes, offset, totalToWrite);
        } else {
            while (totalToWrite > 0) {
                if (!buffer.hasRemaining()) {
                    flushBlock();
                }
                int toWriteNow = Math.min(totalToWrite, buffer.remaining());
                buffer.put(bytes, offset, toWriteNow);
                totalToWrite -= toWriteNow;
                offset += toWriteNow;
            }
        }
    }

    @Override
    public void putBytes(ByteBuffer data) throws IOException {
        if (data.remaining() < buffer.remaining()) {
            buffer.put(data);
        } else {
            while (data.hasRemaining()) {
                if (!buffer.hasRemaining()) {
                    flushBlock();
                }
                int lim = data.limit();
                data.limit(Math.min(data.position() + buffer.remaining(), lim));
                buffer.put(data);
                data.limit(lim);
            }
        }
    }

    @Override
    public long position() throws IOException {
        return flushedLogicalBytes + buffer.position();
    }

    @Override
    public void close() throws IOException {
        try {
            flushBlock();

            // Record index offset (current channel position)
            long indexOffset = channel.position();

            // Write index: compressed size of each block as int32 little-endian
            ByteBuffer indexBuf = ByteBuffer.allocate(blockSizes.size() * Integer.BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN);
            for (int size : blockSizes) {
                indexBuf.putInt(size);
            }
            indexBuf.flip();
            while (indexBuf.hasRemaining()) {
                channel.write(indexBuf);
            }

            // Write 20-byte footer (all little-endian)
            ByteBuffer footer = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
            footer.putLong(indexOffset);
            footer.putInt(blockSize);
            footer.putInt(blockSizes.size());
            footer.putInt(MAGIC);
            footer.flip();
            while (footer.hasRemaining()) {
                channel.write(footer);
            }

            channel.force(false);
        } finally {
            channel.close();
        }

        Files.move(tempPath, destPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
