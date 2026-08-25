package nu.marginalia.slop.storage;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for {@link BlockCompressedStreamingNetworkStorageReader}.
 *
 *  Port 9997 is used to avoid colliding with other network storage tests.
 */
class BlockCompressedStreamingNetworkStorageReaderTest {

    private static final int BLOCK_SIZE = 63; // small to stress block boundaries
    private static final int PORT = 9997;

    Path tempDir;
    HttpServer server;

    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory(getClass().getSimpleName());
    }

    @AfterEach
    void cleanup() throws IOException {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        Files.walk(tempDir)
                .sorted((a, b) -> {
                    if (Files.isDirectory(a) != Files.isDirectory(b))
                        return Files.isDirectory(a) ? 1 : -1;
                    return b.getNameCount() - a.getNameCount();
                })
                .forEach(p -> {
                    try { Files.delete(p); } catch (IOException e) { throw new RuntimeException(e); }
                });
    }

    /** Start an HTTP server on PORT that serves the given file with Range support. */
    private URL serveFile(Path filePath) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT), 1);
        server.createContext("/data", context -> {
            try (context) {
                byte[] data = Files.readAllBytes(filePath);
                long fileSize = data.length;
                String rangeHeader = context.getRequestHeaders().getFirst("Range");

                if (rangeHeader == null) {
                    context.sendResponseHeaders(200, fileSize);
                    context.getResponseBody().write(data);
                    return;
                }

                long start, end;
                if (rangeHeader.startsWith("bytes=-")) {
                    long suffixLen = Long.parseLong(rangeHeader.substring(7));
                    start = fileSize - suffixLen;
                    end = fileSize - 1;
                } else {
                    String[] parts = rangeHeader.substring(6).split("-");
                    start = Long.parseLong(parts[0]);
                    end = Long.parseLong(parts[1]);
                }

                int length = (int) (end - start + 1);
                context.getResponseHeaders().set("Content-Range",
                        "bytes " + start + "-" + end + "/" + fileSize);
                context.sendResponseHeaders(206, length);
                context.getResponseBody().write(data, (int) start, length);
            }
        });
        server.start();
        return new URL("http://127.0.0.1:" + PORT + "/data");
    }

    @Test
    void sequentialReadInts() throws IOException {
        Path file = tempDir.resolve("ints.zstdb");

        try (var writer = new BlockCompressedStorageWriter(file, BLOCK_SIZE, 3)) {
            for (int i = 0; i < 200; i++) {
                writer.putInt(i);
            }
        }

        URL url = serveFile(file);

        try (var reader = new BlockCompressedStreamingNetworkStorageReader(url)) {
            for (int i = 0; i < 200; i++) {
                assertTrue(reader.hasRemaining(), "hasRemaining failed at i=" + i);
                assertEquals(i, reader.getInt(), "wrong value at i=" + i);
            }
            assertFalse(reader.hasRemaining());
        }
    }

    @Test
    void sequentialReadBytes() throws IOException {
        Path file = tempDir.resolve("bytes.zstdb");

        try (var writer = new BlockCompressedStorageWriter(file, BLOCK_SIZE, 3)) {
            for (int i = 0; i < 200; i++) {
                writer.putByte((byte) i);
            }
        }

        URL url = serveFile(file);

        try (var reader = new BlockCompressedStreamingNetworkStorageReader(url)) {
            for (int i = 0; i < 200; i++) {
                assertEquals((byte) i, reader.getByte(), "wrong value at i=" + i);
            }
            assertFalse(reader.hasRemaining());
        }
    }

    @Test
    void skipAcrossBlockBoundary() throws IOException {
        Path file = tempDir.resolve("skip.zstdb");

        try (var writer = new BlockCompressedStorageWriter(file, 64, 3)) {
            for (int i = 0; i < 200; i++) {
                writer.putInt(i);
            }
        }

        URL url = serveFile(file);

        try (var reader = new BlockCompressedStreamingNetworkStorageReader(url)) {
            assertEquals(0, reader.getInt());
            // Skip 100 ints across multiple blocks, consuming compressed bytes without decompressing
            reader.skip(100, Integer.BYTES);
            assertEquals(101L * Integer.BYTES, reader.position());
            assertEquals(101, reader.getInt());
        }
    }

    @Test
    void skipWithinBlock() throws IOException {
        Path file = tempDir.resolve("skipintra.zstdb");

        try (var writer = new BlockCompressedStorageWriter(file, 64, 3)) {
            for (int i = 0; i < 50; i++) {
                writer.putInt(i);
            }
        }

        URL url = serveFile(file);

        try (var reader = new BlockCompressedStreamingNetworkStorageReader(url)) {
            assertEquals(0, reader.getInt());
            // Skip 3 ints within the same block (64 bytes = 16 ints per block)
            reader.skip(3, Integer.BYTES);
            assertEquals(4, reader.getInt());
        }
    }

    @Test
    void emptyFile() throws IOException {
        Path file = tempDir.resolve("empty.zstdb");

        try (var writer = new BlockCompressedStorageWriter(file, BLOCK_SIZE, 3)) {
            // write nothing
        }

        URL url = serveFile(file);

        try (var reader = new BlockCompressedStreamingNetworkStorageReader(url)) {
            assertFalse(reader.hasRemaining());
            assertEquals(0, reader.position());
        }
    }

    @Test
    void seekThrows() throws IOException {
        Path file = tempDir.resolve("seek.zstdb");

        try (var writer = new BlockCompressedStorageWriter(file, BLOCK_SIZE, 3)) {
            writer.putInt(42);
        }

        URL url = serveFile(file);

        try (var reader = new BlockCompressedStreamingNetworkStorageReader(url)) {
            assertThrows(UnsupportedOperationException.class,
                    () -> reader.seek(0, Integer.BYTES));
        }
    }

    @Test
    void positionTracking() throws IOException {
        Path file = tempDir.resolve("pos.zstdb");

        try (var writer = new BlockCompressedStorageWriter(file, BLOCK_SIZE, 3)) {
            for (int i = 0; i < 50; i++) {
                writer.putInt(i);
            }
        }

        URL url = serveFile(file);

        try (var reader = new BlockCompressedStreamingNetworkStorageReader(url)) {
            assertEquals(0, reader.position());
            reader.getInt();
            assertEquals(Integer.BYTES, reader.position());
            reader.getInt();
            assertEquals(2L * Integer.BYTES, reader.position());
        }
    }
}
