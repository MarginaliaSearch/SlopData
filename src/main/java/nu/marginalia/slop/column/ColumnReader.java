package nu.marginalia.slop.column;

import java.io.IOException;

public interface ColumnReader {

    AbstractColumn<?, ?> columnDesc();

    long position() throws IOException;
    void skip(long positions) throws IOException;

    boolean hasRemaining() throws IOException;

    void close() throws IOException;
}
