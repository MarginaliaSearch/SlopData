package nu.marginalia.slop.column.array;

import nu.marginalia.slop.column.AbstractColumn;
import nu.marginalia.slop.column.AbstractObjectColumn;
import nu.marginalia.slop.column.ObjectColumnReader;
import nu.marginalia.slop.column.ObjectColumnWriter;
import nu.marginalia.slop.column.dynamic.VarintColumn;
import nu.marginalia.slop.desc.ColumnFunction;
import nu.marginalia.slop.desc.StorageType;
import nu.marginalia.slop.storage.Storage;
import nu.marginalia.slop.storage.StorageReader;
import nu.marginalia.slop.storage.StorageWriter;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Path;

public class IntArrayColumn extends AbstractObjectColumn<int[], IntArrayColumn.Reader, IntArrayColumn.Writer> {

    private final VarintColumn lengthColumn;

    public IntArrayColumn(String name) {
        this(name, StorageType.PLAIN);
    }

    public IntArrayColumn(String name, StorageType storageType) {
        this(name, ByteOrder.nativeOrder(), storageType);
    }

    public IntArrayColumn(String name, ByteOrder byteOrder, StorageType storageType) {
        super(name,
                "s32" + (byteOrder == ByteOrder.BIG_ENDIAN ? "be" : "le") + "[]",
                byteOrder,
                ColumnFunction.DATA,
                storageType);

        lengthColumn = new VarintColumn(name, ColumnFunction.DATA_LEN, StorageType.PLAIN);
    }


    @Override
    public IntArrayColumn.Reader openUnregistered(Path path, int page) throws IOException {
        return new IntArrayColumn.Reader(
                Storage.reader(path, this, page, true),
                lengthColumn.openUnregistered(path, page)
                );
    }

    @Override
    public IntArrayColumn.Writer createUnregistered(Path path, int page) throws IOException {
        return new IntArrayColumn.Writer(
                Storage.writer(path, this, page),
                lengthColumn.createUnregistered(path, page)
        );
    }

    public class Writer implements ObjectColumnWriter<int[]> {
        private final StorageWriter storage;
        private final VarintColumn.Writer lengthsWriter;

        Writer(StorageWriter storage, VarintColumn.Writer lengthsWriter) {
            this.storage = storage;
            this.lengthsWriter = lengthsWriter;
        }

        @Override
        public AbstractColumn<?, ?> columnDesc() {
            return IntArrayColumn.this;
        }

        public void put(int[] value) throws IOException {
            storage.putInts(value);
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

    public class Reader implements ObjectColumnReader<int[]> {
        private final StorageReader storage;
        private final VarintColumn.Reader lengthsReader;

        Reader(StorageReader storage, VarintColumn.Reader lengthsReader) {
            this.storage = storage;
            this.lengthsReader = lengthsReader;
        }

        @Override
        public AbstractColumn<?, ?> columnDesc() {
            return IntArrayColumn.this;
        }

        public int[] get() throws IOException {
            int length = (int) lengthsReader.get();
            int[] ret = new int[length];
            storage.getInts(ret);
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
                storage.skip(size, Integer.BYTES);
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
