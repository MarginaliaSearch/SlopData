package nu.marginalia.slop.column;

import nu.marginalia.slop.desc.ColumnFunction;
import nu.marginalia.slop.SlopTable;
import nu.marginalia.slop.desc.StorageType;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Path;

public abstract class AbstractColumn<R extends ColumnReader, W extends ColumnWriter> {
    public final String name;
    public final ByteOrder byteOrder;
    public final ColumnFunction function;
    public final StorageType storageType;
    public final String typeMnemonic;

    public AbstractColumn(String columnName,
                          String typeMnemonic,
                          ByteOrder byteOrder,
                          ColumnFunction function,
                          StorageType storageType)
    {
        this.name = columnName;
        this.byteOrder = byteOrder;
        this.function = function;
        this.storageType = storageType;
        this.typeMnemonic = typeMnemonic;
    }

    public String fileName(int page) {
        return name + "." + page + "." +  function.nmnemonic + "." + typeMnemonic + "." + storageType.nmnemonic;
    }

    public abstract R openUnregistered(Path path, int page) throws IOException;
    public abstract W createUnregistered(Path path, int page) throws IOException;

    public R open(SlopTable table, Path path) throws IOException {
        return table.register(openUnregistered(path, table.page));
    }

    public W create(SlopTable table, Path path) throws IOException {
        return table.register(createUnregistered(path, table.page));
    }

    public String toString() {
        return getClass().getSimpleName() + "[name=" + name + ", type=" + typeMnemonic + ", storage=" + storageType.nmnemonic + "]";
    }
}
