package nu.marginalia.slop.storage;

import nu.marginalia.slop.SlopTable;
import nu.marginalia.slop.SlopTablePacker;
import nu.marginalia.slop.column.primitive.ByteColumn;
import nu.marginalia.slop.desc.StorageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ZipTest {

  Path tempDir1;
  Path tempDir2;

  @BeforeEach
  public void setUp() throws IOException {
    tempDir1 = Files.createTempDirectory(getClass().getSimpleName());
    tempDir2 = Files.createTempDirectory(getClass().getSimpleName());
  }

  public static void main(String[] args) {
    System.out.println("Hello world");
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

  @AfterEach
  void cleanup() {
    try {
      for (Path tempDir : List.of(tempDir1, tempDir2)) {
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
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testAligned() throws Exception {
    ByteColumn byteColumn = new ByteColumn("test", StorageType.PLAIN);

    try (var table = new SlopTable(tempDir1)) {
      var writer = byteColumn.create(table);
      writer.put((byte) 0);
      writer.put((byte) 4);
      writer.put((byte) 5);
      writer.put((byte) 1);
    }

    SlopTablePacker.packToSlopZip(tempDir1, tempDir2.resolve("test.slop.zip"));

    try (var table = new SlopTable(tempDir2.resolve("test.slop.zip"))) {
      var reader = byteColumn.open(table);
      assertEquals(0, reader.get());
      assertEquals(4, reader.get());
      assertEquals(5, reader.get());
      assertEquals(1, reader.get());
      assertFalse(reader.hasRemaining());
    }

  }

  @Test
  public void testCompressed() throws Exception {
    ByteColumn byteColumn = new ByteColumn("test", StorageType.GZIP);

    try (var table = new SlopTable(tempDir1)) {
      var writer = byteColumn.create(table);
      writer.put((byte) 0);
      writer.put((byte) 4);
      writer.put((byte) 5);
      writer.put((byte) 1);
    }

    SlopTablePacker.packToSlopZip(tempDir1, tempDir2.resolve("test.slop.zip"));

    try (var table = new SlopTable(tempDir2.resolve("test.slop.zip"))) {
      var reader = byteColumn.open(table);
      assertEquals(0, reader.get());
      assertEquals(4, reader.get());
      assertEquals(5, reader.get());
      assertEquals(1, reader.get());
      assertFalse(reader.hasRemaining());
    }
  }

}
