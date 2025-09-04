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
        long toSkip = target.position() - this.position();

        if (toSkip > 0)
            skip(toSkip);
        else if (toSkip < 0)
            throw new IOException("Target reader is behind alignment reader");
    }

    /** Advance the current reader to the position behind the target.  If the target is the same object
     * as this, nothing is done.
     *
     * @throws IOException on I/O errors or if the target is behind or aligned with this reader and this reader is not the target
     */
    default void prealign(ColumnReader target) throws IOException {
        if (target == this) return;

        long toSkip = target.position() - this.position() - 1;

        if (toSkip > 0)
            skip(toSkip);
        else if (toSkip < 0)
            throw new IOException("Target reader is behind alignment reader");
    }

    boolean hasRemaining() throws IOException;

    void close() throws IOException;
}
