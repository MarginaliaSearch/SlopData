package nu.marginalia.slop.column;

import java.io.IOException;
import java.util.function.Predicate;

public interface ObjectColumnReader<T> extends ColumnReader {

    AbstractColumn<?, ?> columnDesc();

    T get() throws IOException;

    default boolean search(T value) throws IOException {
        while (hasRemaining()) {
            if (get().equals(value)) {
                return true;
            }
        }
        return false;
    }
    default boolean search(Predicate<T> test) throws IOException {
        while (hasRemaining()) {
            if (test.test(get())) {
                return true;
            }
        }
        return false;
    }

    long position() throws IOException;
    void skip(long positions) throws IOException;

    boolean hasRemaining() throws IOException;

    void close() throws IOException;
}
