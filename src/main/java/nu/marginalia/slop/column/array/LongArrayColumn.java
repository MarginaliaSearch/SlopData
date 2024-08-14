package nu.marginalia.slop.column.array;

import nu.marginalia.slop.column.*;
import nu.marginalia.slop.column.dynamic.VarintColumn;
import nu.marginalia.slop.desc.ColumnFunction;
import nu.marginalia.slop.desc.StorageType;
import nu.marginalia.slop.storage.Storage;
import nu.marginalia.slop.storage.StorageReader;
import nu.marginalia.slop.storage.StorageWriter;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Path;

public class LongArrayColumn extends AbstractObjectColumn<long[], LongArrayColumn.Reader, LongArrayColumn.Writer> {
    private final VarintColumn lengthsColumn;

    public LongArrayColumn(String name) {
        this(name, StorageType.PLAIN);
    }

    public LongArrayColumn(String name, StorageType storageType) {
        this(name, ByteOrder.nativeOrder(), storageType);
    }

    public LongArrayColumn(String name, ByteOrder byteOrder, StorageType storageType) {
        super(name,
                "s64" + (byteOrder == ByteOrder.BIG_ENDIAN ? "be" : "le") + "[]",
                byteOrder,
                ColumnFunction.DATA,
                storageType);
        lengthsColumn = new VarintColumn(name, ColumnFunction.DATA_LEN, StorageType.PLAIN);
    }

    @Override
    public LongArrayColumn.Reader openUnregistered(Path path, int page) throws IOException {
        return new LongArrayColumn.Reader(
                Storage.reader(path, this, page, true),
                lengthsColumn.openUnregistered(path, page)
        );
    }

    @Override
    public LongArrayColumn.Writer createUnregistered(Path path, int page) throws IOException {
        return new LongArrayColumn.Writer(
                Storage.writer(path, this, page),
                lengthsColumn.createUnregistered(path, page)
                );
    }


    public class Writer implements ObjectColumnWriter<long[]> {
        private final StorageWriter storage;
        private final VarintColumn.Writer lengthsWriter;

        Writer(StorageWriter storage, VarintColumn.Writer lengthsWriter) {
            this.storage = storage;
            this.lengthsWriter = lengthsWriter;
        }

        @Override
        public AbstractColumn<?, ?> columnDesc() {
            return LongArrayColumn.this;
        }

        public void put(long[] value) throws IOException {
            storage.putLongs(value);
            lengthsWriter.put(value.length);
        }

        public long position() {
            return lengthsWriter.position();
        }

        public void close() throws IOException {
            storage.close();
            lengthsWriter.close();
        }
    }

    public class Reader implements ObjectColumnReader<long[]> {
        private final StorageReader storage;
        private final VarintColumn.Reader lengthsReader;

        Reader(StorageReader storage, VarintColumn.Reader lengthsReader) {
            this.storage = storage;
            this.lengthsReader = lengthsReader;
        }

        @Override
        public AbstractColumn<?, ?> columnDesc() {
            return LongArrayColumn.this;
        }

        public long[] get() throws IOException {
            int length = (int) lengthsReader.get();
            long[] ret = new long[length];
            storage.getLongs(ret);
            return ret;
        }

        @Override
        public long position() throws IOException {
            return lengthsReader.position();
        }

        @Override
        public void skip(long positions) throws IOException {
            for (int i = 0; i < positions; i++) {
                int size = (int) lengthsReader.get();
                storage.skip(size, Long.BYTES);
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
