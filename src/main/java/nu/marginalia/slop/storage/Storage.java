package nu.marginalia.slop.storage;

import nu.marginalia.slop.column.AbstractColumn;
import nu.marginalia.slop.desc.StorageType;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

public interface Storage {

    /** Create a reader for the given column.
     *
     * @param uri the URI containing the column data
     * @param abstractColumn the column descriptor
     * @param page the page number to read
     * @param aligned whether the data is aligned to the storage type, which can be used to optimize reading
     * */
    static StorageReader reader(URI uri, AbstractColumn<?,?> abstractColumn, int page, boolean aligned) throws IOException {
        ByteOrder byteOrder = abstractColumn.byteOrder;
        StorageType storageType = abstractColumn.storageType;

        if (uri.getScheme().equals("file")) {
            Path path = Path.of(uri);

            if (Files.isDirectory(path)) {
                Path filePath = path.resolve(abstractColumn.fileName(page));

                if (aligned && byteOrder.equals(ByteOrder.LITTLE_ENDIAN) && storageType.equals(StorageType.PLAIN)) {
                    // mmap is only supported for little-endian plain storage, but it's generally worth it in this case
                    return new MmapStorageReader(filePath);
                } else {
                    final int bufferSize = switch (abstractColumn.function) {
                        case DATA -> 4096;
                        default -> 1024;
                    };

                    return switch (storageType) {
                        case PLAIN -> new SimpleStorageReader(filePath, byteOrder, bufferSize);
                        case GZIP, ZSTD -> new CompressingStorageReader(filePath, storageType, byteOrder, bufferSize);
                    };
                }
            }
            else if (Files.isRegularFile(Path.of(uri)) && uri.getPath().endsWith(".slop.zip")) {
                // We're technically leaking a file handle here, shouldn't be a problem in any realistic situation though
                ZipFile zf = ZipFile.builder().setFile(path.toFile()).get();
                ZipArchiveEntry entry = zf.getEntry(abstractColumn.fileName(page));

                if (entry == null) {
                    throw new NullPointerException("Missing zip file entry " + abstractColumn.fileName(page));
                }

                if (entry.getMethod() != ZipArchiveEntry.STORED) {
                    throw new IllegalArgumentException("Illegal compression schema for zip file entry in " + uri + " -- must be STORED");
                } else if (!entry.isStreamContiguous()) {
                    throw new IllegalArgumentException("Illegal data layout for zip file entry in " + uri + " -- must be contiguous");
                }

                long start = entry.getDataOffset();
                long size = entry.getCompressedSize();

                if ((start % abstractColumn.alignmentSize() == 0)
                        && aligned
                        && byteOrder.equals(ByteOrder.LITTLE_ENDIAN)
                        && storageType.equals(StorageType.PLAIN))
                {
                    return new MmapStorageReader(path, start, size);
                }
                else {
                    final int bufferSize = switch (abstractColumn.function) {
                        case DATA -> 4096;
                        default -> 1024;
                    };

                    return new CompressingStorageReader(zf.getInputStream(entry), storageType, byteOrder, bufferSize);
                }
            }
            else {
                throw new IllegalArgumentException("Unknown schema " + uri);
            }
        }
        else if (uri.getScheme().equals("http") || uri.getScheme().equals("https")) {

            var url = uri.resolve(abstractColumn.fileName(page)).toURL();

            return new NetworkStorageReader(url, storageType, byteOrder, 65536);
        }
        else {
            throw new IllegalArgumentException("Unsupported URI scheme: " + uri);
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
