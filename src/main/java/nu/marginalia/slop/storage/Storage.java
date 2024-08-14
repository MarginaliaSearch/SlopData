package nu.marginalia.slop.storage;

import nu.marginalia.slop.column.AbstractColumn;
import nu.marginalia.slop.desc.StorageType;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Path;

public interface Storage {

    /** Create a reader for the given column.
     *
     * @param path the directory containing the column data
     * @param abstractColumn the column descriptor
     * @param page the page number to read
     * @param aligned whether the data is aligned to the storage type, which can be used to optimize reading
     * */
    static StorageReader reader(Path path, AbstractColumn<?,?> abstractColumn, int page, boolean aligned) throws IOException {
        ByteOrder byteOrder = abstractColumn.byteOrder;
        StorageType storageType = abstractColumn.storageType;

        Path filePath = path.resolve(abstractColumn.fileName(page));

        if (aligned && byteOrder.equals(ByteOrder.LITTLE_ENDIAN) && storageType.equals(StorageType.PLAIN)) {
            // mmap is only supported for little-endian plain storage, but it's generally worth it in this case
            return new MmapStorageReader(filePath);
        } else {
            final int bufferSize = switch(abstractColumn.function) {
                case DATA -> 4096;
                default -> 1024;
            };

            return switch (storageType) {
                case PLAIN -> new SimpleStorageReader(filePath, byteOrder, bufferSize);
                case GZIP, ZSTD -> new CompressingStorageReader(filePath, storageType, byteOrder, bufferSize);
            };
        }
    }

    /** Create a writer for the given column.
     *
     * @param path the directory containing the column data
     * @param page the page number to read
     * @param abstractColumn the column descriptor
     * */
    static StorageWriter writer(Path path, AbstractColumn<?,?> abstractColumn, int page) throws IOException {
        ByteOrder byteOrder = abstractColumn.byteOrder;
        StorageType storageType = abstractColumn.storageType;

        Path filePath = path.resolve(abstractColumn.fileName(page));

        final int bufferSize = switch(abstractColumn.function) {
            case DATA -> 4096;
            default -> 1024;
        };

        return switch (storageType) {
            case PLAIN -> new SimpleStorageWriter(filePath, byteOrder, bufferSize);
            case GZIP, ZSTD -> new CompressingStorageWriter(filePath, storageType, byteOrder, bufferSize);
        };
    }
}
