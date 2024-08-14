package nu.marginalia.slop.column.primitive;

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

public class ByteColumn extends AbstractColumn<ByteColumn.Reader, ByteColumn.Writer> {

    public ByteColumn(String name) {
        this(name, ColumnFunction.DATA, StorageType.PLAIN);
    }

    public ByteColumn(String name, StorageType storageType) {
        this(name, ColumnFunction.DATA, storageType);
    }

    public ByteColumn(String name, ColumnFunction function, StorageType storageType) {
        super(name,"s8", ByteOrder.nativeOrder(), function, storageType);
    }

    @Override
    public Reader openUnregistered(Path path, int page) throws IOException {
        return new Reader(Storage.reader(path, this, page, true));
    }

    @Override
    public Writer createUnregistered(Path path, int page) throws IOException {
        return new Writer(Storage.writer(path, this, page));
    }

    public class Writer implements ColumnWriter {
        private final StorageWriter storage;
        private long position = 0;

        Writer(StorageWriter storageWriter) throws IOException {
            this.storage = storageWriter;
        }

        @Override
        public AbstractColumn<?,?> columnDesc() {
            return ByteColumn.this;
        }

        public void put(byte value) throws IOException {
            storage.putByte(value);
            position++;
        }

        public long position() {
            return position;
        }

        public void close() throws IOException {
            storage.close();
        }
    }

    public class Reader implements ColumnReader {
        private final StorageReader storage;

        Reader(StorageReader storage) throws IOException {
            this.storage = storage;
        }

        public byte get() throws IOException {
            return storage.getByte();
        }

        @Override
        public AbstractColumn<?, ?> columnDesc() {
            return ByteColumn.this;
        }

        @Override
        public long position() throws IOException {
            return storage.position();
        }

        @Override
        public void skip(long positions) throws IOException {
            storage.skip(positions, Byte.BYTES);
        }

        @Override
        public boolean hasRemaining() throws IOException {
            return storage.hasRemaining();
        }

        @Override
        public void close() throws IOException {
            storage.close();
        }
    }
}
