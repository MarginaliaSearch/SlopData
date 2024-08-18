package nu.marginalia.slop.column;

import nu.marginalia.slop.desc.ColumnFunction;
import nu.marginalia.slop.SlopTable;
import nu.marginalia.slop.desc.StorageType;

import java.io.IOException;
import java.net.URI;
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

    /** Open table for reading, without registering it meaning it's on the caller's
     * responsibility to ensure it is closed
     */
    public abstract R openUnregistered(URI uri, int page) throws IOException;

    /** Open table for writing, without registering it meaning it's on the caller's
     * responsibility to ensure it is closed
     */
    public abstract W createUnregistered(Path path, int page) throws IOException;

    /** Open a column for reading, registering it to the table */
    public R open(SlopTable table) throws IOException {
        return table.register(openUnregistered(table.uri, table.page));
    }

    /** Open a column for writing, registering it to the table */
    public W create(SlopTable table) throws IOException {
        return table.register(createUnregistered(Path.of(table.uri), table.page));
    }

    public String toString() {
        return getClass().getSimpleName() + "[name=" + name + ", type=" + typeMnemonic + ", storage=" + storageType.nmnemonic + "]";
    }
}
