package nu.marginalia.slop.desc;

import nu.marginalia.slop.column.ColumnReader;
import nu.marginalia.slop.column.ColumnWriter;
import nu.marginalia.slop.column.array.*;
import nu.marginalia.slop.column.dynamic.*;
import nu.marginalia.slop.column.primitive.*;
import nu.marginalia.slop.column.string.*;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public abstract class ColumnType<
        R extends ColumnReader,
        W extends ColumnWriter>
{

    public abstract String mnemonic();
    public abstract ByteOrder byteOrder();

    protected abstract R open(Path path, ColumnDesc<R, W> desc) throws IOException;
    protected abstract W create(Path path, ColumnDesc<R, W> desc) throws IOException;

    public int hashCode() {
        return mnemonic().hashCode();
    }

    public boolean equals(Object o) {
        return o instanceof ColumnType ct &&  Objects.equals(ct.mnemonic(), mnemonic());
    }

    public String toString() {
        return mnemonic();
    }
}
