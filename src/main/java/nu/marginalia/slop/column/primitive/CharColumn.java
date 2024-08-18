package nu.marginalia.slop.column.primitive;

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

public class CharColumn extends AbstractColumn<CharColumn.Reader, CharColumn.Writer> {

    public CharColumn(String name) {
        this(name, ByteOrder.nativeOrder(), ColumnFunction.DATA, StorageType.PLAIN);
    }

    public CharColumn(String name, StorageType storageType) {
        this(name, ByteOrder.nativeOrder(), ColumnFunction.DATA, storageType);
    }

    public CharColumn(String name, ByteOrder byteOrder, StorageType storageType) {
        this(name, byteOrder, ColumnFunction.DATA, storageType);
    }

    public CharColumn(String name, ByteOrder byteOrder, ColumnFunction function, StorageType storageType) {
        super(name,
                "u16" + (byteOrder == ByteOrder.BIG_ENDIAN ? "be" : "le"),
                byteOrder,
                function,
                storageType);
    }

    @Override
    public Reader openUnregistered(URI uri, int page) throws IOException {
        return new Reader(Storage.reader(uri, this, page, true));
    }

    @Override
    public Writer createUnregistered(Path path, int page) throws IOException {
        return new Writer(Storage.writer(path, this, page));
    }



    public class Writer implements ColumnWriter {
        private final StorageWriter storage;
        private long position = 0;

        Writer(StorageWriter storageWriter) {
            this.storage = storageWriter;
        }

        @Override
        public AbstractColumn<?,?> columnDesc() {
            return CharColumn.this;
        }

        public void put(char value) throws IOException {
            storage.putChar(value);
            position++;
        }

        public long position() {
            return position;
        }

        public void close() throws IOException {
            storage.close();
        }
    }

    public class Reader implements ColumnReader {
        private final StorageReader storage;

        Reader(StorageReader storage) {
            this.storage = storage;
        }

        public char get() throws IOException {
            return storage.getChar();
        }

        @Override
        public AbstractColumn<?,?> columnDesc() {
            return CharColumn.this;
        }

        @Override
        public long position() throws IOException {
            return storage.position() / Character.BYTES;
        }

        @Override
        public void skip(long positions) throws IOException {
            storage.skip(positions, Character.BYTES);
        }


        @Override
        public boolean hasRemaining() throws IOException {
            return storage.hasRemaining();
        }

        @Override
        public void close() throws IOException {
            storage.close();
        }
    }
}
