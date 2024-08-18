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
import java.net.URI;
import java.nio.ByteOrder;
import java.nio.file.Path;

public class CustomBinaryColumn extends AbstractColumn<CustomBinaryColumn.Reader, CustomBinaryColumn.Writer> {

    private final VarintColumn lengthColumn;

    public CustomBinaryColumn(String name) {
        this(name, ColumnFunction.DATA, StorageType.PLAIN);
    }

    public CustomBinaryColumn(String name, StorageType storageType) {
        this(name, ColumnFunction.DATA, storageType);
    }

    public CustomBinaryColumn(String name, ColumnFunction function, StorageType storageType) {
        super(name,
                "s8[]+custom",
                ByteOrder.nativeOrder(),
                function,
                storageType);
        lengthColumn = new VarintColumn(name, ColumnFunction.DATA_LEN, StorageType.PLAIN);
    }

    @Override
    public Reader openUnregistered(URI uri, int page) throws IOException {
        return new CustomBinaryColumn.Reader(
                Storage.reader(uri, this, page, true),
                lengthColumn.openUnregistered(uri, page)
                );
    }

    @Override
    public CustomBinaryColumn.Writer createUnregistered(Path path, int page) throws IOException {
        return new CustomBinaryColumn.Writer(
                Storage.writer(path, this, page),
                lengthColumn.createUnregistered(path, page)
                );
    }

    public class Writer implements ColumnWriter {
        private final VarintColumn.Writer indexWriter;
        private final StorageWriter storage;

        public Writer(StorageWriter storage,
                      VarintColumn.Writer indexWriter)
        {
            this.storage = storage;
            this.indexWriter = indexWriter;
        }


        @Override
        public AbstractColumn<?, ?> columnDesc() {
            return CustomBinaryColumn.this;
        }

        public RecordWriter next() throws IOException {
            return new RecordWriter() {
                long pos = storage.position();

                @Override
                public StorageWriter writer() {
                    return storage;
                }

                @Override
                public void close() throws IOException {
                    indexWriter.put((int) (storage.position() - pos));
                }
            };
        }

        public long position() {
            return indexWriter.position();
        }

        public void close() throws IOException {
            indexWriter.close();
            storage.close();
        }
    }

    public class Reader implements ColumnReader {
        private final VarintColumn.Reader indexReader;
        private final StorageReader storage;

        Reader(StorageReader reader, VarintColumn.Reader indexReader) throws IOException {
            this.storage = reader;
            this.indexReader = indexReader;
        }


        @Override
        public AbstractColumn<?, ?> columnDesc() {
            return CustomBinaryColumn.this;
        }

        @Override
        public void skip(long positions) throws IOException {
            for (int i = 0; i < positions; i++) {
                int size = (int) indexReader.get();
                storage.skip(size, 1);
            }
        }

        @Override
        public boolean hasRemaining() throws IOException {
            return indexReader.hasRemaining();
        }

        public long position() throws IOException {
            return indexReader.position();
        }

        public RecordReader next() throws IOException {
            int size = (int) indexReader.get();

            return new RecordReader() {
                long origPos = storage.position();

                @Override
                public int size() {
                    return size;
                }

                @Override
                public StorageReader reader() {
                    return storage;
                }

                @Override
                public void close() throws IOException {
                    assert storage.position() - origPos == size : "column reader caller did not read the entire record";
                }
            };
        }

        public void close() throws IOException {
            indexReader.close();
            storage.close();
        }

    }

    interface RecordWriter extends AutoCloseable {
        StorageWriter writer();
        void close() throws IOException;
    }

    interface RecordReader extends AutoCloseable {
        int size();
        StorageReader reader();
        void close() throws IOException;
    }
}
