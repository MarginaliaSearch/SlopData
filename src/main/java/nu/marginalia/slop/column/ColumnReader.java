package nu.marginalia.slop.column;

import java.io.IOException;

public interface ColumnReader {

    AbstractColumn<?, ?> columnDesc();

    boolean isDirect();
    long position() throws IOException;
    void skip(long positions) throws IOException;

    /** Advance the current reader to the position of the target.
     * @throws IOException on I/O errors or if the target is behind this reader
     */
    default void align(ColumnReader target) throws IOException {
        long toSkip = target.position() - position();

        if (toSkip > 0)
            skip(toSkip);
        else if (toSkip < 0)
            throw new IOException("Target reader is behind alignment reader");
    }

    boolean hasRemaining() throws IOException;

    void close() throws IOException;
}
