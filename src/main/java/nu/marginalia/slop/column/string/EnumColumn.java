package nu.marginalia.slop.column.string;

import nu.marginalia.slop.column.*;
import nu.marginalia.slop.column.dynamic.VarintColumn;
import nu.marginalia.slop.desc.ColumnFunction;
import nu.marginalia.slop.desc.StorageType;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class EnumColumn extends AbstractObjectColumn<String, EnumColumn.Reader, EnumColumn.Writer> {
    private final StringColumn dicionaryColumn;
    private final VarintColumn dataColumn;

    public EnumColumn(String name) {
        this(name, StandardCharsets.UTF_8, StorageType.PLAIN);
    }

    public EnumColumn(String name, Charset charset) {
        this(name, charset, StorageType.PLAIN);
    }

    public EnumColumn(String name, Charset charset, StorageType storageType) {
        super(name, "enum+"+charset.displayName(), ByteOrder.nativeOrder(), ColumnFunction.DATA, storageType);

        dicionaryColumn = new StringColumn(name, charset, ColumnFunction.DICT, StorageType.PLAIN);
        dataColumn = new VarintColumn(name, ColumnFunction.DATA, storageType);
    }

    @Override
    public Reader openUnregistered(URI uri, int page) throws IOException {
        return new EnumColumn.Reader(
                dicionaryColumn.openUnregistered(uri, page),
                dataColumn.openUnregistered(uri, page)
        );
    }

    @Override
    public Writer createUnregistered(Path path, int page) throws IOException {
        return new EnumColumn.Writer(
                dicionaryColumn.createUnregistered(path, page),
                dataColumn.createUnregistered(path, page)
        );
    }


    public class Writer implements ObjectColumnWriter<String> {
        private final StringColumn.Writer dictionaryColumn;
        private final VarintColumn.Writer dataColumn;
        private final HashMap<String, Integer> dictionary = new HashMap<>();

        Writer(StringColumn.Writer dictionaryColumn, VarintColumn.Writer dataColumn)
        {
            this.dictionaryColumn = dictionaryColumn;
            this.dataColumn = dataColumn;
        }

        @Override
        public AbstractColumn<?, ?> columnDesc() {
            return EnumColumn.this;
        }

        public void put(String value) throws IOException {
            Integer index = dictionary.get(value);
            if (index == null) {
                index = dictionary.size();
                dictionary.put(value, index);
                dictionaryColumn.put(value);
            }
            dataColumn.put(index);
        }

        public long position() {
            return dataColumn.position();
        }

        public void close() throws IOException {
            dataColumn.close();
            dictionaryColumn.close();
        }
    }

    public class Reader implements ObjectColumnReader<String> {
        private final VarintColumn.Reader dataColumn;
        private final List<String> dictionary = new ArrayList<>();

        Reader(StringColumn.Reader dicionaryColumn,
                      VarintColumn.Reader dataColumn) throws IOException
        {
            this.dataColumn = dataColumn;

            while (dicionaryColumn.hasRemaining()) {
                dictionary.add(dicionaryColumn.get());
            }

            dicionaryColumn.close();
        }

        @Override
        public AbstractColumn<?, ?> columnDesc() {
            return EnumColumn.this;
        }

        public List<String> getDictionary() throws IOException {
            return Collections.unmodifiableList(dictionary);
        }

        public int getOrdinal() throws IOException {
            return (int) dataColumn.get();
        }

        public String get() throws IOException {
            int index = (int) dataColumn.get();
            return dictionary.get(index);
        }

        @Override
        public long position() throws IOException {
            return dataColumn.position();
        }

        @Override
        public void skip(long positions) throws IOException {
            dataColumn.skip(positions);
        }

        @Override
        public boolean hasRemaining() throws IOException {
            return dataColumn.hasRemaining();
        }

        @Override
        public void close() throws IOException {
            dataColumn.close();
        }
    }

}
