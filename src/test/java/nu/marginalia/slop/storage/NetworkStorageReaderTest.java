package nu.marginalia.slop.storage;

import com.sun.net.httpserver.HttpServer;
import nu.marginalia.slop.SlopTable;
import nu.marginalia.slop.column.primitive.ByteColumn;
import nu.marginalia.slop.desc.StorageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class NetworkStorageReaderTest {
    Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory(getClass().getSimpleName());
    }

    @AfterEach
    void cleanup() {
        try {
            Files.walk(tempDir)
                    .sorted(this::deleteOrder)
                    .forEach(p -> {
                        try {
                            if (Files.isRegularFile(p)) {
                                System.out.println("Deleting " + p + " " + Files.size(p));
                            }
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    int deleteOrder(Path a, Path b) {
        if (Files.isDirectory(a) && !Files.isDirectory(b)) {
            return 1;
        } else if (!Files.isDirectory(a) && Files.isDirectory(b)) {
            return -1;
        } else {
            return a.getNameCount() - b.getNameCount();
        }
    }
//
//    @Test
//    void testNetworkMissingColumn() throws Exception {
//        var col = new ByteColumn("test", StorageType.PLAIN);
//
//
//        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 9998), 1);
//        server.start();
//        try (var slop = new SlopTable(new URI("http://localhost:9998/foo/"))) {
//            var reader = col.open(slop);
//            Assertions.fail();
//        } catch (NoSuchColumnException e) {
//            // expected
//        }
//        finally {
//            server.stop(0);
//        }
//    }

    @Test
    void testNetworkRead() throws IOException, InterruptedException {
        var col = new ByteColumn("test", StorageType.PLAIN);

        try (var slop = new SlopTable(tempDir)) {
            var writer = col.create(slop);
            for (int i = 0; i < 127; i++) {
                assertEquals(i, writer.position());
                writer.put((byte) i);
            }
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 9999), 1);

        server.createContext("/foo/", context -> {
            try (context) {
                System.out.println("r:" + context.getRequestURI());

                var path = context.getRequestURI().getPath();
                path = path.substring(path.lastIndexOf('/') + 1);

                var method = context.getRequestMethod();
                System.out.println("m:" + method);
                if (context.getRequestMethod().equals("HEAD")) {
                    context.sendResponseHeaders(200, -1);
                    return;
                }

                context.sendResponseHeaders(200, Files.size(tempDir.resolve(path)));
                try (var is = Files.newInputStream(tempDir.resolve(path))) {
                    is.transferTo(context.getResponseBody());
                    System.out.println("Transferred " + path);
                }
            }
        });

        server.start();

        try (var slop = new SlopTable(new URI("http://localhost:9999/foo/"))) {
            var reader = col.open(slop);
            for (int i = 0; i < 127; i++) {
                assertTrue(reader.hasRemaining());
                assertEquals(i, reader.position());

                assertEquals((byte) i, reader.get());
            }
            assertFalse(reader.hasRemaining());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        finally {
            server.stop(0);
        }
    }

}