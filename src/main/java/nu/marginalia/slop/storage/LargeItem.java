package nu.marginalia.slop.storage;

import java.io.IOException;

public class LargeItem<T> implements AutoCloseable {
    private T value = null;
    private boolean realized = false;

    private final Realizer<T> realizer;
    private final Omitter omitter;

    public LargeItem(Realizer<T> realizer, Omitter omitter) {
        this.realizer = realizer;
        this.omitter = omitter;
    }

    public T get() throws IOException {
        if (realized) {
            return value;
        } else {
            realized = true;
            return (value = realizer.realize());
        }
    }

    public <T2> LargeItem<T2> map(RealizerMapper<T, T2> mapper) {
        return new LargeItem<>(realizer.map(mapper), omitter);
    }

    @Override
    public void close() throws IOException {
        if (!realized) {
            omitter.omit();
        }
    }

    public interface Omitter {
        void omit() throws IOException;
    }
    public interface Realizer<T> {
        T realize() throws IOException;

        default <T2> Realizer<T2> map(RealizerMapper<T, T2> mapper) {
            return () -> mapper.apply(realize());
        }
    }

    public interface RealizerMapper<T, T2> {
        T2 apply(T val) throws IOException;
    }
}


