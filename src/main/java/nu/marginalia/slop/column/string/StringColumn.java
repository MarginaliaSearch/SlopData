package nu.marginalia.slop.column.string;

import nu.marginalia.slop.column.*;
import nu.marginalia.slop.column.array.*;
import nu.marginalia.slop.desc.ColumnFunction;
import nu.marginalia.slop.desc.StorageType;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteOrder;
import java.nio.file.Path;

public class StringColumn extends AbstractObjectColumn<String, StringColumn.Reader, StringColumn.Writer> {
    ByteArrayColumn backingColumn;

    public StringColumn(String name) {
        this(name, StorageType.PLAIN);
    }

    public StringColumn(String name, StorageType storageType) {
        super(name, "s8[]+str", ByteOrder.nativeOrder(), ColumnFunction.DATA, storageType);

        backingColumn = new ByteArrayColumn(name, function, storageType);
    }

    public StringColumn(String name, ColumnFunction function, StorageType storageType) {
        super(name, "s8[]+str", ByteOrder.nativeOrder(), function, storageType);

        backingColumn = new ByteArrayColumn(name, function, storageType);
    }

    @Override
    public StringColumn.Reader openUnregistered(URI uri, int page) throws IOException {
        return new StringColumn.Reader(backingColumn.openUnregistered(uri, page));
    }

    @Override
    public StringColumn.Writer createUnregistered(Path path, int page) throws IOException {
        return new StringColumn.Writer(backingColumn.createUnregistered(path, page));
    }


    public class Writer implements ObjectColumnWriter<String> {
        private final ByteArrayColumn.Writer backingColumn;

        Writer(ByteArrayColumn.Writer backingColumn) {
            this.backingColumn = backingColumn;
        }

        @Override
        public AbstractColumn<?,?> columnDesc() {
            return StringColumn.this;
        }

        public void put(String value) throws IOException {
            if (null == value) {
                value = "";
            }

            backingColumn.put(value.getBytes());
        }

        public long position() {
            return backingColumn.position();
        }

        public void close() throws IOException {
            backingColumn.close();
        }
    }

    public class Reader implements ObjectColumnReader<String> {
        private final ByteArrayColumn.Reader backingColumn;

        Reader(ByteArrayColumn.Reader backingColumn) throws IOException {
            this.backingColumn = backingColumn;
        }

        @Override
        public AbstractColumn<?, ?> columnDesc() {
            return StringColumn.this;
        }

        public String get() throws IOException {
            return new String(backingColumn.get());
        }

        @Override
        public long position() throws IOException {
            return backingColumn.position();
        }

        @Override
        public void skip(long positions) throws IOException {
            backingColumn.skip(positions);
        }

        @Override
        public boolean hasRemaining() throws IOException {
            return backingColumn.hasRemaining();
        }

        @Override
        public void close() throws IOException {
            backingColumn.close();
        }
    }
}
