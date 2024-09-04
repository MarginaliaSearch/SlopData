package nu.marginalia.slop.column.string;

import nu.marginalia.slop.column.*;
import nu.marginalia.slop.desc.ColumnFunction;
import nu.marginalia.slop.desc.StorageType;
import nu.marginalia.slop.storage.Storage;
import nu.marginalia.slop.storage.StorageReader;
import nu.marginalia.slop.storage.StorageWriter;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class TxtStringColumn extends AbstractObjectColumn<String, TxtStringColumn.Reader, TxtStringColumn.Writer> {

    final Charset charset;

    public TxtStringColumn(String name) {
        this(name, StandardCharsets.UTF_8, ColumnFunction.DATA, StorageType.PLAIN);
    }
    public TxtStringColumn(Charset charset, String name) {
        this(name, charset, ColumnFunction.DATA, StorageType.PLAIN);
    }

    public TxtStringColumn(String name, Charset charset, StorageType storageType) {
        this(name, charset, ColumnFunction.DATA, storageType);
    }

    public TxtStringColumn(String name, Charset charset, ColumnFunction function, StorageType storageType) {
        super(name, "s8[]+txt+"+charset.displayName(), ByteOrder.nativeOrder(), function, storageType);

        this.charset = charset;
    }

    @Override
    public Reader openUnregistered(URI uri, int page) throws IOException {
        return new Reader(Storage.reader(uri, this, page, true));
    }

    @Override
    public Writer createUnregistered(Path path, int page) throws IOException {
        return new Writer(Storage.writer(path, this, page));
    }

    public class Writer implements ObjectColumnWriter<String> {
        private final StorageWriter storageWriter;
        private long position = 0;

        Writer(StorageWriter storageWriter) {
            this.storageWriter = storageWriter;
        }

        @Override
        public AbstractColumn<?,?> columnDesc() {
            return TxtStringColumn.this;
        }

        public void put(String value) throws IOException {
            if (null == value) {
                value = "";
            }

            assert value.indexOf('\n') == -1 : "Newline not allowed in txtstring";

            storageWriter.putBytes(value.getBytes());
            storageWriter.putByte((byte) '\n');
            position++;
        }

        public long position() {
            return position;
        }

        public void close() throws IOException {
            storageWriter.close();
        }
    }

    public class Reader implements ObjectColumnReader<String> {
        private final StorageReader storageReader;
        private long position = 0;

        Reader(StorageReader storageReader) {
            this.storageReader = storageReader;
        }

        @Override
        public AbstractColumn<?,?> columnDesc() {
            return TxtStringColumn.this;
        }

        public String get() throws IOException {
            StringBuilder sb = new StringBuilder();
            byte b;
            while (storageReader.hasRemaining()) {
                b = storageReader.getByte();
                if (b == '\n') {
                    break;
                }
                else {
                    sb.append((char) b);
                }
            }
            position++;
            return sb.toString();
        }

        @Override
        public long position() throws IOException {
            return position;
        }

        @Override
        public void skip(long positions) throws IOException {
            int i = 0;

            position+=positions;

            while (i < positions && storageReader.hasRemaining()) {
                if (storageReader.getByte() == '\n') {
                    i++;
                }
            }
        }

        @Override
        public boolean hasRemaining() throws IOException {
            return storageReader.hasRemaining();
        }

        @Override
        public void close() throws IOException {
            storageReader.close();
        }
    }

}
