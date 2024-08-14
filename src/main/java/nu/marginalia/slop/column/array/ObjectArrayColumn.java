package nu.marginalia.slop.column.array;

import nu.marginalia.slop.column.AbstractColumn;
import nu.marginalia.slop.column.AbstractObjectColumn;
import nu.marginalia.slop.column.ObjectColumnReader;
import nu.marginalia.slop.column.ObjectColumnWriter;
import nu.marginalia.slop.column.dynamic.VarintColumn;
import nu.marginalia.slop.desc.ColumnFunction;
import nu.marginalia.slop.desc.StorageType;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ObjectArrayColumn<T> extends AbstractObjectColumn<List<T>, ObjectArrayColumn<T>.Reader, ObjectArrayColumn<T>.Writer> {
    private final VarintColumn groupLengthColumn;
    private final AbstractObjectColumn<T, ?, ?> wrappingColumn;

    public ObjectArrayColumn(String name, AbstractObjectColumn<T, ?, ?> wrappingColumn) {
        super(name,
                wrappingColumn.typeMnemonic + "[]",
                ByteOrder.nativeOrder(),
                ColumnFunction.DATA,
                wrappingColumn.storageType);

        this.groupLengthColumn = new VarintColumn(name, ColumnFunction.GROUP_LENGTH, StorageType.PLAIN);
        this.wrappingColumn = wrappingColumn;
    }


    @Override
    public ObjectArrayColumn<T>.Reader openUnregistered(Path path, int page) throws IOException {
        return new ObjectArrayColumn<T>.Reader(
                wrappingColumn.openUnregistered(path, page),
                groupLengthColumn.openUnregistered(path, page)
                );
    }

    @Override
    public ObjectArrayColumn<T>.Writer createUnregistered(Path path, int page) throws IOException {
        return new ObjectArrayColumn<T>.Writer(
                wrappingColumn.createUnregistered(path, page),
                groupLengthColumn.createUnregistered(path, page)
                );
    }


    public class Writer implements ObjectColumnWriter<List<T>> {
        private final ObjectColumnWriter<T> dataWriter;
        private final VarintColumn.Writer groupsWriter;

        Writer(ObjectColumnWriter<T> dataWriter, VarintColumn.Writer groupsWriter) {
            this.dataWriter = dataWriter;
            this.groupsWriter = groupsWriter;
        }

        @Override
        public AbstractColumn<?, ?> columnDesc() {
            return ObjectArrayColumn.this;
        }

        public void put(List<T> value) throws IOException {
            groupsWriter.put(value.size());
            for (T t : value) {
                dataWriter.put(t);
            }
        }

        public long position() {
            return groupsWriter.position();
        }

        public void close() throws IOException {
            dataWriter.close();
            groupsWriter.close();
        }
    }

    public class Reader implements ObjectColumnReader<List<T>> {
        private final ObjectColumnReader<T> dataReader;
        private final VarintColumn.Reader groupsReader;

        Reader(ObjectColumnReader<T> dataReader, VarintColumn.Reader groupsReader) {
            this.dataReader = dataReader;
            this.groupsReader = groupsReader;
        }

        @Override
        public AbstractColumn<?, ?> columnDesc() {
            return ObjectArrayColumn.this;
        }

        public List<T> get() throws IOException {
            int length = groupsReader.get();
            List<T> ret = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                ret.add(dataReader.get());
            }
            return ret;
        }

        @Override
        public long position() throws IOException {
            return groupsReader.position();
        }

        @Override
        public void skip(long positions) throws IOException {
            int toSkip = 0;
            for (int i = 0; i < positions; i++) {
                toSkip += groupsReader.get();
            }
            dataReader.skip(toSkip);
        }

        @Override
        public boolean hasRemaining() throws IOException {
            return groupsReader.hasRemaining();
        }

        @Override
        public void close() throws IOException {
            dataReader.close();
            groupsReader.close();
        }
    }
}
