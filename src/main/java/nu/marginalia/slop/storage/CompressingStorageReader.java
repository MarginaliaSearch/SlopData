package nu.marginalia.slop.storage;

import nu.marginalia.slop.desc.StorageType;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class CompressingStorageReader implements StorageReader {
    private final byte[] arrayBuffer;

    private long position = 0;

    private final List<AutoCloseable> closableResources = new ArrayList<>();

    private final InputStream is;
    private final ByteBuffer buffer;

    public CompressingStorageReader(InputStream stream, StorageType storageType, ByteOrder order, int bufferSize) throws IOException {
        is = switch (storageType) {
            case GZIP -> new GZIPInputStream(stream);
            case ZSTD -> new ZstdCompressorInputStream(stream);
            case PLAIN -> stream;
            case ZSTD_BLOCK, ZSTD_BLOCK_SEQUENTIAL_ACCESS -> throw new IllegalArgumentException("ZSTD_BLOCK is not a streaming format; use BlockCompressedStorageReader instead");
        };

        this.arrayBuffer = new byte[bufferSize];
        this.buffer = ByteBuffer.wrap(arrayBuffer).order(order);

        buffer.position(0);
        buffer.limit(0);

        // read the first chunk, this is needed for InputStream otherwise we don't handle empty files
        // correctly
        refill();
    }

    public CompressingStorageReader(Path path, StorageType storageType, ByteOrder order, int bufferSize) throws IOException {
        this(openInputStreamForFile(path), storageType, order, bufferSize);
    }

    /** Create a new reader for the given path, throwing NoSuchColumnException if it does not exist */
    private static InputStream openInputStreamForFile(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new NoSuchColumnException(path.toString());
        }
        return Files.newInputStream(path, StandardOpenOption.READ);
    }

    /** Add a resource to be closed with this reader */
    CompressingStorageReader withCloseableResource(AutoCloseable resource) {
        closableResources.add(resource);
        return this;
    }

    @Override
    public byte getByte() throws IOException {
        if (buffer.remaining() < Byte.BYTES) {
            refill();
        }

        return buffer.get();
    }

    @Override
    public short getShort() throws IOException {
        if (buffer.remaining() < Short.BYTES) {
            refill();
        }

        return buffer.getShort();
    }

    @Override
    public char getChar() throws IOException {
        if (buffer.remaining() < Character.BYTES) {
            refill();
        }

        return buffer.getChar();
    }

    @Override
    public int getInt() throws IOException {
        if (buffer.remaining() < Integer.BYTES) {
            refill();
        }

        return buffer.getInt();
    }

    @Override
    public long getLong() throws IOException {
        if (buffer.remaining() < Long.BYTES) {
            refill();
        }

        return buffer.getLong();
    }

    @Override
    public float getFloat() throws IOException {
        if (buffer.remaining() < Float.BYTES) {
            refill();
        }

        return buffer.getFloat();
    }

    @Override
    public double getDouble() throws IOException {
        if (buffer.remaining() < Double.BYTES) {
            refill();
        }

        return buffer.getDouble();
    }

    @Override
    public void getBytes(byte[] bytes) throws IOException {
        getBytes(bytes, 0, bytes.length);
    }

    @Override
    public void getBytes(byte[] bytes, int offset, int length) throws IOException {
        if (buffer.remaining() >= length) {
            buffer.get(bytes, offset, length);
        } else {
            int totalToRead = length;

            while (totalToRead > 0) {
                if (!buffer.hasRemaining()) {
                    refill();
                }

                int toRead = Math.min(buffer.remaining(), totalToRead);
                buffer.get(bytes, offset + length - totalToRead, toRead);
                totalToRead -= toRead;
            }
        }
    }

    @Override
    public void getBytes(ByteBuffer data) throws IOException {
        if (data.remaining() < buffer.remaining()) {
            int lim = buffer.limit();
            buffer.limit(buffer.position() + data.remaining());
            data.put(buffer);
            buffer.limit(lim);
        } else {
            while (data.hasRemaining()) {
                if (!buffer.hasRemaining()) {
                    refill();
                }

                int lim = buffer.limit();
                buffer.limit(Math.min(buffer.position() + data.remaining(), lim));
                data.put(buffer);
                buffer.limit(lim);
            }
        }
    }

    public void getInts(int[] ints) throws IOException {
        if (buffer.remaining() >= ints.length * Integer.BYTES) {
            // fast path: if we can read all the ints from the buffer and don't need to check for buffer boundaries
            for (int i = 0; i < ints.length; i++) {
                ints[i] = buffer.getInt();
            }
        }
        else {
            for (int i = 0; i < ints.length; i++) {
                ints[i] = getInt();
            }
        }
    }

    public void getLongs(long[] longs) throws IOException {
        if (buffer.remaining() >= longs.length * Long.BYTES) {
            // fast path: if we can read all the longs from the buffer and don't need to check for buffer boundaries
            for (int i = 0; i < longs.length; i++) {
                longs[i] = buffer.getLong();
            }
        }
        else {
            for (int i = 0; i < longs.length; i++) {
                longs[i] = getLong();
            }
        }
    }

    @Override
    public void skip(long bytes, int stepSize) throws IOException {
        long toSkip = bytes * stepSize;

        if (buffer.remaining() < toSkip) {
            toSkip -= buffer.remaining();

            while (toSkip > 0) {
                long rb = is.skip(toSkip);
                toSkip -= rb;
                position += rb;
            }

            buffer.position(0);
            buffer.limit(0);
        } else {
            buffer.position(buffer.position() + (int) toSkip);
        }
    }

    @Override
    public void seek(long position, int stepSize) throws IOException {
        throw new UnsupportedOperationException("Seek not supported in CompressingStorageReader");
    }

    private void refill() throws IOException {
        buffer.compact();

        while (buffer.hasRemaining()) {
            int rb = is.read(arrayBuffer, buffer.position(), buffer.remaining());
            if (rb < 0) {
                break;
            }
            else {
                position += rb;
                buffer.position(buffer.position() + rb);
            }
        }

        buffer.flip();
    }

    @Override
    public long position() {
        return position - buffer.remaining();
    }

    @Override
    public boolean hasRemaining() throws IOException {
        return buffer.hasRemaining() || is.available() > 0;
    }

    @Override
    public boolean isDirect() {
        return false;
    }

    @Override
    public void close() throws IOException {
        is.close();

        for (var resource : closableResources) {
            try {
                resource.close();
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }
}
