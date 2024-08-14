package nu.marginalia.slop.column;

import java.io.IOException;

public interface ColumnWriter {
    AbstractColumn<?, ?> columnDesc();
    
    /** Return the current record index in the column */
    long position();

    void close() throws IOException;
}
