package nu.marginalia.slop.column;

import nu.marginalia.slop.column.array.DoubleArrayColumn;
import nu.marginalia.slop.column.array.FloatArrayColumn;
import nu.marginalia.slop.column.array.IntArrayColumn;
import nu.marginalia.slop.SlopTable;
import nu.marginalia.slop.desc.StorageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ArrayColumnTest {
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

  @Test
  public void testFloatArray() throws IOException {
    var arrayCol = new FloatArrayColumn("test", ByteOrder.LITTLE_ENDIAN, StorageType.PLAIN);

    try (var table = new SlopTable(tempDir)) {
      var column = arrayCol.create(table);

      column.put(new float[] { 1.5f, 2.5f, 3.5f });
      column.put(new float[] { 0.25f });
      column.put(new float[] { 100.0f });
    }

    try (var table = new SlopTable(tempDir)) {
      var column = arrayCol.open(table);

      assertArrayEquals(new float[] { 1.5f, 2.5f, 3.5f }, column.get());
      assertArrayEquals(new float[] { 0.25f }, column.get());
      assertArrayEquals(new float[] { 100.0f }, column.get());
    }
  }

  @Test
  public void testDoubleArray() throws IOException {
    var arrayCol = new DoubleArrayColumn("test", ByteOrder.LITTLE_ENDIAN, StorageType.PLAIN);

    try (var table = new SlopTable(tempDir)) {
      var column = arrayCol.create(table);

      column.put(new double[] { 1.5, 2.5, 3.5 });
      column.put(new double[] { 0.25 });
      column.put(new double[] { 100.0 });
    }

    try (var table = new SlopTable(tempDir)) {
      var column = arrayCol.open(table);

      assertArrayEquals(new double[] { 1.5, 2.5, 3.5 }, column.get());
      assertArrayEquals(new double[] { 0.25 }, column.get());
      assertArrayEquals(new double[] { 100.0 }, column.get());
    }
  }

  @Test
  public void test() throws IOException {
    var arrayCol = new IntArrayColumn("test", ByteOrder.LITTLE_ENDIAN, StorageType.PLAIN);

    try (var table = new SlopTable(tempDir)) {

      var column = arrayCol.create(table);

      column.put(new int[] { 11, 22, 33 });
      column.put(new int[] { 2 });
      column.put(new int[] { 444 });
    }

    try (var table = new SlopTable(tempDir)) {
      var column = arrayCol.open(table);

      assertArrayEquals(new int[] { 11, 22, 33 }, column.get());
      assertArrayEquals(new int[] { 2 }, column.get());
      assertArrayEquals(new int[] { 444 }, column.get());
    }
  }

}
