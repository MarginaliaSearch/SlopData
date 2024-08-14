package nu.marginalia.slop.column;

import java.io.IOException;

public interface ObjectColumnWriter<T> extends ColumnWriter {
    AbstractColumn<?, ?> columnDesc();

    void put(T value) throws IOException;

    /** Return the current record index in the column */
    long position();

    void close() throws IOException;
}
