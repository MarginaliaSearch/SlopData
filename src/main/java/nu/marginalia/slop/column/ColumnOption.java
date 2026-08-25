package nu.marginalia.slop.column;

public sealed interface ColumnOption {
    record ZstdCompressionLevel(int value) implements ColumnOption { }
}
