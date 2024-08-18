package nu.marginalia.slop.column.array;

import nu.marginalia.slop.column.*;
import nu.marginalia.slop.column.dynamic.VarintColumn;
import nu.marginalia.slop.desc.ColumnFunction;
import nu.marginalia.slop.desc.StorageType;
import nu.marginalia.slop.storage.Storage;
import nu.marginalia.slop.storage.StorageReader;
import nu.marginalia.slop.storage.StorageWriter;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteOrder;
import java.nio.file.Path;

public class ByteArrayColumn extends AbstractObjectColumn<byte[], ByteArrayColumn.Reader, ByteArrayColumn.Writer> {

    private final VarintColumn lengthColumn;

    public ByteArrayColumn(String name) {
        this(name, StorageType.PLAIN);
    }

    public ByteArrayColumn(String name, StorageType storageType) {
        this(name, ColumnFunction.DATA, storageType);
    }

    public ByteArrayColumn(String name, ColumnFunction function, StorageType storageType) {
        super(name, "s8[]", ByteOrder.nativeOrder(), function, storageType);

        lengthColumn = new VarintColumn(name, function.lengthsTable(), StorageType.PLAIN);
    }

    @Override
    public ByteArrayColumn.Reader openUnregistered(URI uri, int page) throws IOException {
        return new ByteArrayColumn.Reader(
                Storage.reader(uri, this, page,true),
                lengthColumn.openUnregistered(uri, page)
                );
    }

    @Override
    public ByteArrayColumn.Writer createUnregistered(Path path, int page) throws IOException {
        return new ByteArrayColumn.Writer(
                Storage.writer(path, this, page),
                lengthColumn.createUnregistered(path, page)
        );
    }


    public class Writer implements ObjectColumnWriter<byte[]> {
        private final StorageWriter storage;
        private final VarintColumn.Writer lengthsWriter;

        private long position = 0;

        Writer(StorageWriter storage, VarintColumn.Writer lengthsWriter) {
            this.storage = storage;
            this.lengthsWriter = lengthsWriter;
        }

        @Override
        public AbstractColumn<?, ?> columnDesc() {
            return ByteArrayColumn.this;
        }

        public void put(byte[] value) throws IOException {
            position ++;
            storage.putBytes(value);
            lengthsWriter.put(value.length);
        }

        public long position() {
            return position;
        }

        public void close() throws IOException {
            storage.close();
            lengthsWriter.close();
        }
    }

    public class Reader implements ObjectColumnReader<byte[]> {
        private final StorageReader storage;
        private final VarintColumn.Reader lengthsReader;

        public Reader(StorageReader storage, VarintColumn.Reader lengthsReader) throws IOException {
            this.storage = storage;
            this.lengthsReader = lengthsReader;
        }

        @Override
        public AbstractColumn<?, ?> columnDesc() {
            return ByteArrayColumn.this;
        }

        public byte[] get() throws IOException {
            int length = lengthsReader.get();
            byte[] ret = new byte[length];
            storage.getBytes(ret);
            return ret;
        }

        @Override
        public long position() throws IOException {
            return lengthsReader.position();
        }

        @Override
        public void skip(long positions) throws IOException {
            for (int i = 0; i < positions; i++) {
                int size = lengthsReader.get();
                storage.skip(size, 1);
            }
        }

        @Override
        public boolean hasRemaining() throws IOException {
            return lengthsReader.hasRemaining();
        }

        @Override
        public void close() throws IOException {
            storage.close();
            lengthsReader.close();
        }
    }

}
