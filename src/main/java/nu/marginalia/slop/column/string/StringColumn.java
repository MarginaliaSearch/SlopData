package nu.marginalia.slop.column.string;

import nu.marginalia.slop.column.*;
import nu.marginalia.slop.column.array.*;
import nu.marginalia.slop.desc.ColumnFunction;
import nu.marginalia.slop.desc.StorageType;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class StringColumn extends AbstractObjectColumn<String, StringColumn.Reader, StringColumn.Writer> {
    private final ByteArrayColumn backingColumn;
    private final Charset charset;
    public StringColumn(String name) {
        this(name, StandardCharsets.UTF_8, StorageType.PLAIN);
    }
    public StringColumn(String name, Charset charset) {
        this(name, charset, StorageType.PLAIN);
    }

    public StringColumn(String name, Charset charset, StorageType storageType) {
        super(name, "s8[]+str+"+charset.displayName(), ByteOrder.nativeOrder(), ColumnFunction.DATA, storageType);

        this.backingColumn = new ByteArrayColumn(name, function, storageType);
        this.charset = charset;
    }

    public StringColumn(String name, Charset charset, ColumnFunction function, StorageType storageType) {
        super(name, "s8[]+str+"+charset.displayName(), ByteOrder.nativeOrder(), function, storageType);

        this.backingColumn = new ByteArrayColumn(name, function, storageType);
        this.charset = charset;
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

            backingColumn.put(value.getBytes(charset));
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
            return new String(backingColumn.get(), charset);
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
