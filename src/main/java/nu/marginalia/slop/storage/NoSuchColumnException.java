package nu.marginalia.slop.storage;

import java.io.IOException;

/**
 * Exception thrown when a column is not found in the storage,
 * thrown when attempting to open a column.
 */
public class NoSuchColumnException extends IOException {
    public NoSuchColumnException(String message) {
        super(message);
    }

    public NoSuchColumnException(String message, Throwable cause) {
        super(message, cause);
    }

    public NoSuchColumnException(Throwable cause) {
        super(cause);
    }
}
