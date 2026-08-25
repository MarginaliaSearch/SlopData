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

public class DoubleArrayColumn extends AbstractObjectColumn<double[], DoubleArrayColumn.Reader, DoubleArrayColumn.Writer> {

    private final VarintColumn lengthColumn;

    public DoubleArrayColumn(String name) {
        this(name, StorageType.PLAIN);
    }

    public DoubleArrayColumn(String name, StorageType storageType) {
        this(name, ByteOrder.nativeOrder(), storageType);
    }

    public DoubleArrayColumn(String name, ByteOrder byteOrder, StorageType storageType, ColumnOption... options) {
        super(name,
                "fp64" + (byteOrder == ByteOrder.BIG_ENDIAN ? "be" : "le") + "[]",
                byteOrder,
                ColumnFunction.DATA,
                storageType,
                options);

        lengthColumn = new VarintColumn(name, ColumnFunction.DATA_LEN, StorageType.PLAIN);
    }

    @Override
    public int alignmentSize() {
        return 8;
    }

    @Override
    public DoubleArrayColumn.Reader openUnregistered(URI uri, int page) throws IOException {
        return new DoubleArrayColumn.Reader(
                Storage.reader(uri, this, page, true),
                lengthColumn.openUnregistered(uri, page)
                );
    }

    @Override
    public DoubleArrayColumn.Writer createUnregistered(Path path, int page) throws IOException {
        return new DoubleArrayColumn.Writer(
                Storage.writer(path, this, page),
                lengthColumn.createUnregistered(path, page)
        );
    }

    public class Writer implements ObjectColumnWriter<double[]> {
        private final StorageWriter storage;
        private final VarintColumn.Writer lengthsWriter;

        Writer(StorageWriter storage, VarintColumn.Writer lengthsWriter) {
            this.storage = storage;
            this.lengthsWriter = lengthsWriter;
        }

        @Override
        public AbstractColumn<?, ?> columnDesc() {
            return DoubleArrayColumn.this;
        }

        public void put(double[] value) throws IOException {
            storage.putDoubles(value);
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

    public class Reader implements ObjectColumnReader<double[]> {
        private final StorageReader storage;
        private final VarintColumn.Reader lengthsReader;

        Reader(StorageReader storage, VarintColumn.Reader lengthsReader) {
            this.storage = storage;
            this.lengthsReader = lengthsReader;
        }

        @Override
        public boolean isDirect() {
            return storage.isDirect();
        }

        @Override
        public AbstractColumn<?, ?> columnDesc() {
            return DoubleArrayColumn.this;
        }

        public double[] get() throws IOException {
            int length = (int) lengthsReader.get();
            double[] ret = new double[length];
            storage.getDoubles(ret);
            return ret;
        }

        @Override
        public long position() throws IOException {
            return lengthsReader.position();
        }

        @Override
        public void skip(long positions) throws IOException {
            long toSkip = 0;
            for (int i = 0; i < positions; i++) {
                toSkip += lengthsReader.get();
            }
            storage.skip(toSkip, Double.BYTES);
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
