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
import java.net.URI;
import java.nio.ByteOrder;
import java.nio.file.Path;

public class FloatColumn extends AbstractColumn<FloatColumn.Reader, FloatColumn.Writer> {

    public FloatColumn(String name) {
        this(name, ByteOrder.nativeOrder(), ColumnFunction.DATA, StorageType.PLAIN);
    }

    public FloatColumn(String name, StorageType storageType) {
        this(name, ByteOrder.nativeOrder(), ColumnFunction.DATA, storageType);
    }

    public FloatColumn(String name, ByteOrder byteOrder, StorageType storageType) {
        this(name, byteOrder, ColumnFunction.DATA, storageType);
    }

    public FloatColumn(String name, ByteOrder byteOrder, ColumnFunction function, StorageType storageType) {
        super(name,
                "fp32" + (byteOrder == ByteOrder.BIG_ENDIAN ? "be" : "le"),
                byteOrder,
                function,
                storageType);
    }

    @Override
    public Reader openUnregistered(URI uri, int page) throws IOException {
        return new Reader(Storage.reader(uri, this, page, true));
    }

    @Override
    public Writer createUnregistered(Path path, int page) throws IOException {
        return new Writer(Storage.writer(path, this, page));
    }


    public class Writer implements ColumnWriter {
        private final StorageWriter storage;
        private long position = 0;

        public Writer(StorageWriter storageWriter) throws IOException {
            this.storage = storageWriter;
        }


        @Override
        public AbstractColumn<?,?> columnDesc() {
            return FloatColumn.this;
        }

        public void put(float value) throws IOException {
            storage.putFloat(value);
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

        @Override
        public AbstractColumn<?,?> columnDesc() {
            return FloatColumn.this;
        }

        public float get() throws IOException {
            return storage.getFloat();
        }

        @Override
        public long position() throws IOException {
            return storage.position() / Float.BYTES;
        }

        @Override
        public void skip(long positions) throws IOException {
            storage.skip(positions, Float.BYTES);
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
