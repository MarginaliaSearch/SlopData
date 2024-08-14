package nu.marginalia.slop.column.dynamic;

import nu.marginalia.slop.column.AbstractColumn;
import nu.marginalia.slop.column.ColumnReader;
import nu.marginalia.slop.column.ColumnWriter;
import nu.marginalia.slop.desc.ColumnFunction;
import nu.marginalia.slop.desc.StorageType;
import nu.marginalia.slop.storage.Storage;
import nu.marginalia.slop.storage.StorageReader;
import nu.marginalia.slop.storage.StorageWriter;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Path;

public class VarintColumn extends AbstractColumn<VarintColumn.Reader, VarintColumn.Writer> {

    public VarintColumn(String name) {
        this(name, ColumnFunction.DATA, StorageType.PLAIN);
    }

    public VarintColumn(String name, StorageType storageType) {
        this(name, ColumnFunction.DATA, storageType);
    }

    public VarintColumn(String name, ColumnFunction function, StorageType storageType) {
        super(name, "varint", ByteOrder.nativeOrder(), function, storageType);
    }

    @Override
    public Reader openUnregistered(Path path, int page) throws IOException {
        return new VarintColumn.Reader(Storage.reader(path, this, page, true));
    }

    @Override
    public Writer createUnregistered(Path path, int page) throws IOException {
        return new VarintColumn.Writer(Storage.writer(path, this, page));
    }

    public class Writer implements ColumnWriter {
        private final StorageWriter writer;
        private long position = 0;

        Writer(StorageWriter writer) {
            this.writer = writer;
        }

        @Override
        public AbstractColumn<?, ?> columnDesc() {
            return VarintColumn.this;
        }

        public void put(long value) throws IOException {
            position++;

            if (value < 0)
                throw new IllegalArgumentException("Value must be positive");

            if (value < (1<<7)) {
                writer.putByte((byte) value);
            }
            else if (value < (1<<14)) {
                writer.putByte((byte) (value >>> (7) | 0x80));
                writer.putByte((byte) (value & 0x7F));
            }
            else if (value < (1<<21)) {
                writer.putByte((byte) ((value >>> 14) | 0x80));
                writer.putByte((byte) ((value >>> 7) | 0x80));
                writer.putByte((byte) (value & 0x7F));
            }
            else if (value < (1<<28)) {
                writer.putByte((byte) ((value >>> 21) | 0x80));
                writer.putByte((byte) ((value >>> 14) | 0x80));
                writer.putByte((byte) ((value >>> 7) | 0x80));
                writer.putByte((byte) (value & 0x7F));
            }
            else if (value < (1L<<35)) {
                writer.putByte((byte) ((value >>> 28) | 0x80));
                writer.putByte((byte) ((value >>> 21) | 0x80));
                writer.putByte((byte) ((value >>> 14) | 0x80));
                writer.putByte((byte) ((value >>> 7) | 0x80));
                writer.putByte((byte) (value & 0x7F));
            }
            else if (value < (1L<<42)) {
                writer.putByte((byte) ((value >>> 35) | 0x80));
                writer.putByte((byte) ((value >>> 28) | 0x80));
                writer.putByte((byte) ((value >>> 21) | 0x80));
                writer.putByte((byte) ((value >>> 14) | 0x80));
                writer.putByte((byte) ((value >>> 7) | 0x80));
                writer.putByte((byte) (value & 0x7F));
            }
            else if (value < (1L<<49)) {
                writer.putByte((byte) ((value >>> 42) | 0x80));
                writer.putByte((byte) ((value >>> 35) | 0x80));
                writer.putByte((byte) ((value >>> 28) | 0x80));
                writer.putByte((byte) ((value >>> 21) | 0x80));
                writer.putByte((byte) ((value >>> 14) | 0x80));
                writer.putByte((byte) ((value >>> 7) | 0x80));
                writer.putByte((byte) (value & 0x7F));
            }
            else if (value < (1L<<56)) {
                writer.putByte((byte) ((value >>> 49) | 0x80));
                writer.putByte((byte) ((value >>> 42) | 0x80));
                writer.putByte((byte) ((value >>> 35) | 0x80));
                writer.putByte((byte) ((value >>> 28) | 0x80));
                writer.putByte((byte) ((value >>> 21) | 0x80));
                writer.putByte((byte) ((value >>> 14) | 0x80));
                writer.putByte((byte) ((value >>> 7) | 0x80));
                writer.putByte((byte) (value & 0x7F));
            }
            else {
                writer.putByte((byte) ((value >>> 56) | 0x80));
                writer.putByte((byte) ((value >>> 49) | 0x80));
                writer.putByte((byte) ((value >>> 42) | 0x80));
                writer.putByte((byte) ((value >>> 35) | 0x80));
                writer.putByte((byte) ((value >>> 28) | 0x80));
                writer.putByte((byte) ((value >>> 21) | 0x80));
                writer.putByte((byte) ((value >>> 14) | 0x80));
                writer.putByte((byte) ((value >>> 7) | 0x80));
                writer.putByte((byte) (value & 0x7F));
            }
        }

        public void put(long[] values) throws IOException {
            for (long val : values) {
                put(val);
            }
        }

        public long position() {
            return position;
        }

        public void close() throws IOException {
            writer.close();
        }
    }

    public class Reader implements ColumnReader {
        private final StorageReader reader;

        private long position = 0;

        Reader(StorageReader reader) {
            this.reader = reader;
        }

        @Override
        public AbstractColumn<?, ?> columnDesc() {
            return VarintColumn.this;
        }

        public int get() throws IOException {
            position++;

            byte b = reader.getByte();
            if ((b & 0x80) == 0) {
                return b;
            }

            int value = b & 0x7F;
            do {
                b = reader.getByte();
                value = (value << 7) | (b & 0x7F);
            } while ((b & 0x80) != 0);


            return value;
        }

        public long getLong() throws IOException {
            position++;

            byte b = reader.getByte();
            if ((b & 0x80) == 0) {
                return b;
            }

            long value = b & 0x7F;
            do {
                b = reader.getByte();
                value = value << 7 | (b & 0x7F);
            } while ((b & 0x80) != 0);

            return value;
        }

        @Override
        public long position() {
            return position;
        }

        @Override
        public void skip(long positions) throws IOException {
            for (long i = 0; i < positions; i++) {
                get();
            }
        }

        @Override
        public boolean hasRemaining() throws IOException {
            return reader.hasRemaining();
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }
}
