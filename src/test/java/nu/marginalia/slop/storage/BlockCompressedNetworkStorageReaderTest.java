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

/** Tests for {@link BlockCompressedNetworkStorageReader}.
 *
 *  An embedded HTTP server handles Range requests so we can test the block fetch
 *  and seeking logic without a real remote server.  Port 9998 is used to avoid
 *  colliding with {@link NetworkStorageReaderTest} (9999).
 */
class BlockCompressedNetworkStorageReaderTest {

    private static final int BLOCK_SIZE = 63; // small to stress block boundaries
    private static final int PORT = 9998;

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
                    // delete files before directories, deeper paths first
                    if (Files.isDirectory(a) != Files.isDirectory(b))
                        return Files.isDirectory(a) ? 1 : -1;
                    return b.getNameCount() - a.getNameCount();
                })
                .forEach(p -> {
                    try { Files.delete(p); } catch (IOException e) { throw new RuntimeException(e); }
                });
    }

    /** Start an HTTP server on PORT that serves the given file with correct Range support. */
    private URL serveFile(Path filePath) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT), 1);
        server.createContext("/data", context -> {
            try (context) {
                byte[] data = Files.readAllBytes(filePath);
                long fileSize = data.length;
                String rangeHeader = context.getRequestHeaders().getFirst("Range");

                if (rangeHeader == null) {
                    // No Range header: serve the whole file
                    context.sendResponseHeaders(200, fileSize);
                    context.getResponseBody().write(data);
                    return;
                }

                long start, end;
                if (rangeHeader.startsWith("bytes=-")) {
                    // Suffix range: bytes=-N means last N bytes
                    long suffixLen = Long.parseLong(rangeHeader.substring(7));
                    start = fileSize - suffixLen;
                    end = fileSize - 1;
                } else {
                    // Explicit range: bytes=start-end
                    String range = rangeHeader.substring(6);
                    String[] parts = range.split("-");
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

    /** Start an HTTP server that always returns 200 (no Range support). */
    private URL serveWithoutRangeSupport(Path filePath) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT), 1);
        server.createContext("/data", context -> {
            try (context) {
                byte[] data = Files.readAllBytes(filePath);
                context.sendResponseHeaders(200, data.length);
                context.getResponseBody().write(data);
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

        try (var reader = new BlockCompressedNetworkStorageReader(url)) {
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

        try (var reader = new BlockCompressedNetworkStorageReader(url)) {
            for (int i = 0; i < 200; i++) {
                assertEquals((byte) i, reader.getByte(), "wrong value at i=" + i);
            }
            assertFalse(reader.hasRemaining());
        }
    }

    @Test
    void seekForwardAndBackward() throws IOException {
        // Use block size = 64 so 10 000 ints span many blocks
        Path file = tempDir.resolve("seek.zstdb");

        try (var writer = new BlockCompressedStorageWriter(file, 64, 3)) {
            for (int i = 0; i < 10_000; i++) {
                writer.putInt(i);
            }
        }

        URL url = serveFile(file);

        try (var reader = new BlockCompressedNetworkStorageReader(url)) {
            assertEquals(0, reader.getInt());

            // Seek forward past several block boundaries
            reader.seek(5000L, Integer.BYTES);
            assertEquals(5000, reader.getInt());

            // Seek backward
            reader.seek(2000L, Integer.BYTES);
            assertEquals(2000, reader.getInt());

            // Seek to last element
            reader.seek(9999L, Integer.BYTES);
            assertEquals(9999, reader.getInt());
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

        try (var reader = new BlockCompressedNetworkStorageReader(url)) {
            assertEquals(0, reader.getInt());
            // skip 100 ints across multiple blocks
            reader.skip(100, Integer.BYTES);
            assertEquals(101L * Integer.BYTES, reader.position());
            assertEquals(101, reader.getInt());
        }
    }

    @Test
    void emptyFile() throws IOException {
        Path file = tempDir.resolve("empty.zstdb");

        try (var writer = new BlockCompressedStorageWriter(file, BLOCK_SIZE, 3)) {
            // write nothing
        }

        URL url = serveFile(file);

        try (var reader = new BlockCompressedNetworkStorageReader(url)) {
            assertFalse(reader.hasRemaining());
            assertEquals(0, reader.position());
        }
    }

    @Test
    void serverWithoutRangeSupportThrows() throws IOException {
        // Write a valid file so the server has something to serve
        Path file = tempDir.resolve("norange.zstdb");
        try (var writer = new BlockCompressedStorageWriter(file, BLOCK_SIZE, 3)) {
            writer.putInt(42);
        }

        URL url = serveWithoutRangeSupport(file);

        try {
            new BlockCompressedNetworkStorageReader(url).close();
            fail("Expected IOException when server does not support range requests");
        } catch (IOException e) {
            // Expected: server returned 200 instead of 206
        }
    }
}
