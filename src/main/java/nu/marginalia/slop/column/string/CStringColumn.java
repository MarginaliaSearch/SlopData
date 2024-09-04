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

public class CStringColumn extends AbstractObjectColumn<String, CStringColumn.Reader, CStringColumn.Writer> {

    public CStringColumn(String name) {
        this(name, StandardCharsets.UTF_8, ColumnFunction.DATA, StorageType.PLAIN);
    }

    public CStringColumn(String name, Charset charset) {
        this(name, charset, ColumnFunction.DATA, StorageType.PLAIN);
    }

    public CStringColumn(String name, Charset charset, StorageType storageType) {
        this(name, charset, ColumnFunction.DATA, storageType);
    }

    public CStringColumn(String name, Charset charset, ColumnFunction function, StorageType storageType) {
        super(name, "s8+cstr+"+charset.displayName(), ByteOrder.nativeOrder(), function, storageType);
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
            return CStringColumn.this;
        }

        public void put(String value) throws IOException {
            if (null == value) {
                value = "";
            }
            assert value.indexOf('\0') == -1 : "Null byte not allowed in cstring";
            storageWriter.putBytes(value.getBytes());
            storageWriter.putByte((byte) 0);
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
            return CStringColumn.this;
        }

        public String get() throws IOException {
            StringBuilder sb = new StringBuilder();
            byte b;
            while (storageReader.hasRemaining() && (b = storageReader.getByte()) != 0) {
                sb.append((char) b);
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

            while (i < positions && storageReader.hasRemaining()) {
                if (storageReader.getByte() == 0) {
                    i++;
                }
            }
            position += positions;
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
