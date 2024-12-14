package nu.marginalia.slop.column.string;

import nu.marginalia.slop.column.AbstractColumn;
import nu.marginalia.slop.column.AbstractObjectColumn;
import nu.marginalia.slop.column.ObjectColumnReader;
import nu.marginalia.slop.column.ObjectColumnWriter;
import nu.marginalia.slop.column.array.LargeByteArrayColumn;
import nu.marginalia.slop.desc.ColumnFunction;
import nu.marginalia.slop.desc.StorageType;
import nu.marginalia.slop.storage.LargeItem;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** String column with per-record zstd compression.
 * Useful when storing extremely large strings. */
public class LargeStringColumn extends AbstractObjectColumn<String, LargeStringColumn.Reader, LargeStringColumn.Writer> {
    private final LargeByteArrayColumn backingColumn;
    private final Charset charset;

    public LargeStringColumn(String name) {
        this(name, StandardCharsets.UTF_8);
    }

    public LargeStringColumn(String name, Charset charset) {
        super(name, "s8[]+str"+charset.displayName()+"+zstd", ByteOrder.nativeOrder(), ColumnFunction.DATA, StorageType.PLAIN);

        this.backingColumn = new LargeByteArrayColumn(name, function);
        this.charset = charset;
    }

    public LargeStringColumn(String name, Charset charset, ColumnFunction function) {
        super(name, "s8[]+str+"+charset.displayName()+"+zstd", ByteOrder.nativeOrder(), function, StorageType.PLAIN);

        this.backingColumn = new LargeByteArrayColumn(name, function);
        this.charset = charset;
    }

    @Override
    public int alignmentSize() {
        return 1;
    }

    @Override
    public LargeStringColumn.Reader openUnregistered(URI uri, int page) throws IOException {
        return new LargeStringColumn.Reader(backingColumn.openUnregistered(uri, page));
    }

    @Override
    public LargeStringColumn.Writer createUnregistered(Path path, int page) throws IOException {
        return new LargeStringColumn.Writer(backingColumn.createUnregistered(path, page));
    }


    public class Writer implements ObjectColumnWriter<String> {
        private final LargeByteArrayColumn.Writer backingColumn;

        Writer(LargeByteArrayColumn.Writer backingColumn) {
            this.backingColumn = backingColumn;
        }

        @Override
        public AbstractColumn<?,?> columnDesc() {
            return LargeStringColumn.this;
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
        private final LargeByteArrayColumn.Reader backingColumn;

        Reader(LargeByteArrayColumn.Reader backingColumn) throws IOException {
            this.backingColumn = backingColumn;
        }
        @Override
        public boolean isDirect() {
            return backingColumn.isDirect();
        }

        @Override
        public AbstractColumn<?, ?> columnDesc() {
            return LargeStringColumn.this;
        }

        public String get() throws IOException {
            return new String(backingColumn.get(), charset);
        }

        public LargeItem<String> getLarge() throws IOException{
            return backingColumn.getLarge().map(bytes -> new String(bytes, charset));
        }

        public LargeItem<byte[]> getLargeBytes() throws IOException{
            return backingColumn.getLarge();
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
