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

public class ShortColumn extends AbstractColumn<ShortColumn.Reader, ShortColumn.Writer> {

    public ShortColumn(String name) {
        this(name, ByteOrder.nativeOrder(), ColumnFunction.DATA, StorageType.PLAIN);
    }

    public ShortColumn(String name, StorageType storageType) {
        this(name, ByteOrder.nativeOrder(), ColumnFunction.DATA, storageType);
    }

    public ShortColumn(String name, ByteOrder byteOrder, StorageType storageType) {
        this(name, byteOrder, ColumnFunction.DATA, storageType);
    }

    public ShortColumn(String name, ByteOrder byteOrder, ColumnFunction function, StorageType storageType) {
        super(name, "s16" + (byteOrder == ByteOrder.BIG_ENDIAN ? "be" : "le"), byteOrder, function, storageType);
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

        Writer(StorageWriter storageWriter) {
            this.storage = storageWriter;
        }

        @Override
        public AbstractColumn<?,?> columnDesc() {
            return ShortColumn.this;
        }


        public void put(short value) throws IOException {
            storage.putShort(value);
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

        public Reader(StorageReader storage) throws IOException {
            this.storage = storage;
        }

        public short get() throws IOException {
            return storage.getShort();
        }

        @Override
        public AbstractColumn<?,?> columnDesc() {
            return ShortColumn.this;
        }


        @Override
        public long position() throws IOException {
            return storage.position() / Short.BYTES;
        }

        @Override
        public void skip(long positions) throws IOException {
            storage.skip(positions, Short.BYTES);
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
