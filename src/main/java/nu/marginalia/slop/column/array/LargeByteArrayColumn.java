package nu.marginalia.slop.column.array;

import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import nu.marginalia.slop.column.AbstractColumn;
import nu.marginalia.slop.column.AbstractObjectColumn;
import nu.marginalia.slop.column.ObjectColumnReader;
import nu.marginalia.slop.column.ObjectColumnWriter;
import nu.marginalia.slop.column.dynamic.VarintColumn;
import nu.marginalia.slop.desc.ColumnFunction;
import nu.marginalia.slop.desc.StorageType;
import nu.marginalia.slop.storage.LargeItem;
import nu.marginalia.slop.storage.Storage;
import nu.marginalia.slop.storage.StorageReader;
import nu.marginalia.slop.storage.StorageWriter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteOrder;
import java.nio.file.Path;

public class LargeByteArrayColumn extends AbstractObjectColumn<byte[], LargeByteArrayColumn.Reader, LargeByteArrayColumn.Writer> {

    private final VarintColumn lengthColumn;

    public LargeByteArrayColumn(String name) {
        this(name, ColumnFunction.DATA);
    }

    public LargeByteArrayColumn(String name, ColumnFunction function) {
        super(name, "s8[]+zstd", ByteOrder.nativeOrder(), function, StorageType.PLAIN);

        lengthColumn = new VarintColumn(name, function.lengthsTable(), StorageType.PLAIN);
    }

    @Override
    public int alignmentSize() {
        return 1;
    }

    @Override
    public LargeByteArrayColumn.Reader openUnregistered(URI uri, int page) throws IOException {
        return new LargeByteArrayColumn.Reader(
                Storage.reader(uri, this, page,true),
                lengthColumn.openUnregistered(uri, page)
                );
    }

    @Override
    public LargeByteArrayColumn.Writer createUnregistered(Path path, int page) throws IOException {
        return new LargeByteArrayColumn.Writer(
                Storage.writer(path, this, page),
                lengthColumn.createUnregistered(path, page)
        );
    }


    public class Writer implements ObjectColumnWriter<byte[]> {
        private final StorageWriter storage;
        private final VarintColumn.Writer lengthsWriter;

        private long position = 0;

        Writer(StorageWriter storage, VarintColumn.Writer lengthsWriter) {
            this.storage = storage;
            this.lengthsWriter = lengthsWriter;
        }

        @Override
        public AbstractColumn<?, ?> columnDesc() {
            return LargeByteArrayColumn.this;
        }

        public void put(byte[] value) throws IOException {
            position ++;

            ByteArrayOutputStream baos = new ByteArrayOutputStream(value.length/2);
            try (var zos = new ZstdOutputStream(baos)) {
                zos.write(value);
            }

            byte[] compressed = baos.toByteArray();
            storage.putBytes(compressed);
            lengthsWriter.put(compressed.length);
        }

        public long position() {
            return position;
        }

        public void close() throws IOException {
            storage.close();
            lengthsWriter.close();
        }
    }

    public class Reader implements ObjectColumnReader<byte[]> {
        private final StorageReader storage;
        private final VarintColumn.Reader lengthsReader;

        public Reader(StorageReader storage, VarintColumn.Reader lengthsReader) throws IOException {
            this.storage = storage;
            this.lengthsReader = lengthsReader;
        }

        @Override
        public boolean isDirect() {
            return storage.isDirect();
        }

        @Override
        public AbstractColumn<?, ?> columnDesc() {
            return LargeByteArrayColumn.this;
        }

        public byte[] get() throws IOException {
            int length = lengthsReader.get();
            byte[] ret = new byte[length];

            storage.getBytes(ret);

            return decompress(ret);
        }

        public LargeItem<byte[]> getLarge() throws IOException {
            int length = lengthsReader.get();
            return storage.getLarge(length).map(LargeByteArrayColumn.Reader::decompress);
        }

        private static byte[] decompress(byte[] raw) throws IOException {
            ByteArrayInputStream bais = new ByteArrayInputStream(raw);
            try (var zis = new ZstdInputStream(bais)) {
                return zis.readAllBytes();
            }
        }

        @Override
        public long position() throws IOException {
            return lengthsReader.position();
        }

        @Override
        public void skip(long positions) throws IOException {
            for (int i = 0; i < positions; i++) {
                int size = lengthsReader.get();
                storage.skip(size, 1);
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
