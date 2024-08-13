package nu.marginalia.slop.desc;

import nu.marginalia.slop.ColumnTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SlopTableTest {
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
    public void testEmpty() throws IOException {
        SlopTable slopTable = new SlopTable(0);
        slopTable.close();
    }

    @Test
    public void testPositionsGood() throws IOException {
        var name1 = new ColumnDesc<>("test1",
                0,
                ColumnFunction.DATA,
                ColumnTypes.INT_LE,
                StorageType.PLAIN
        );
        var name2 = new ColumnDesc<>("test2",
                0,
                ColumnFunction.DATA,
                ColumnTypes.INT_LE,
                StorageType.PLAIN
        );

        try (SlopTable writerTable = new SlopTable(0)) {
            var column1 = name1.create(writerTable, tempDir);
            var column2 = name2.create(writerTable, tempDir);

            column1.put(42);
            column2.put(43);
        }


        try (SlopTable readerTable = new SlopTable(0)) {
            var column1 = name1.open(readerTable, tempDir);
            var column2 = name2.open(readerTable, tempDir);

            assertEquals(42, column1.get());
            assertEquals(43, column2.get());
        }
    }


    @Test
    public void testPositionsMisaligned() throws IOException {
        var name1 = new ColumnDesc<>("test1",
                0,
                ColumnFunction.DATA,
                ColumnTypes.INT_LE,
                StorageType.PLAIN
        );
        var name2 = new ColumnDesc<>("test2",
                0,
                ColumnFunction.DATA,
                ColumnTypes.INT_LE,
                StorageType.PLAIN
        );

        boolean sawException = false;
        try (SlopTable writerTable = new SlopTable(0)) {
            var column1 = name1.create(writerTable, tempDir);
            var column2 = name2.create(writerTable, tempDir);

            column1.put(42);
            column2.put(43);
            column2.put(44);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            sawException = true;
        }
        assertEquals(true, sawException);

    }


    // Sanity check for the implementation of position() in the column classes
    @Test
    public void testPositionsMegatest() throws IOException {
        var byteCol = new ColumnDesc<>("byte", ColumnTypes.BYTE, StorageType.PLAIN);
        var charCol = new ColumnDesc<>("char", ColumnTypes.CHAR_LE, StorageType.PLAIN);
        var intCol = new ColumnDesc<>("int", ColumnTypes.INT_LE, StorageType.PLAIN);
        var longCol = new ColumnDesc<>("long", ColumnTypes.LONG_LE, StorageType.PLAIN);
        var floatCol = new ColumnDesc<>("float", ColumnTypes.FLOAT_LE, StorageType.PLAIN);
        var doubleCol = new ColumnDesc<>("double", ColumnTypes.DOUBLE_LE, StorageType.PLAIN);
        var byteArrayCol = new ColumnDesc<>("byteArray", ColumnTypes.BYTE_ARRAY, StorageType.PLAIN);
        var intArrayCol = new ColumnDesc<>("intArray", ColumnTypes.INT_ARRAY_LE, StorageType.PLAIN);
        var longArrayCol = new ColumnDesc<>("longArray", ColumnTypes.LONG_ARRAY_LE, StorageType.PLAIN);
        var cstringCol = new ColumnDesc<>("cstring", ColumnTypes.CSTRING, StorageType.PLAIN);
        var txtStringCol = new ColumnDesc<>("txtString", ColumnTypes.TXTSTRING, StorageType.PLAIN);
        var arrayStringCol = new ColumnDesc<>("arrayString", ColumnTypes.STRING, StorageType.PLAIN);
        var varintCol = new ColumnDesc<>("varint", ColumnTypes.VARINT_LE, StorageType.PLAIN);
        var enumCol = new ColumnDesc<>("enum", ColumnTypes.ENUM_LE, StorageType.PLAIN);

        try (SlopTable writerTable = new SlopTable(0)) {
            var byteColumn = byteCol.create(writerTable, tempDir);
            var charColumn = charCol.create(writerTable, tempDir);
            var intColumn = intCol.create(writerTable, tempDir);
            var longColumn = longCol.create(writerTable, tempDir);
            var floatColumn = floatCol.create(writerTable, tempDir);
            var doubleColumn = doubleCol.create(writerTable, tempDir);
            var byteArrayColumn = byteArrayCol.create(writerTable, tempDir);

            var intArrayColumn = intArrayCol.create(writerTable, tempDir);
            var longArrayColumn = longArrayCol.create(writerTable, tempDir);
            var cstringColumn = cstringCol.create(writerTable, tempDir);
            var txtStringColumn = txtStringCol.create(writerTable, tempDir);
            var arrayStringColumn = arrayStringCol.create(writerTable, tempDir);
            var enumColumn = enumCol.create(writerTable, tempDir);
            var varintColumn = varintCol.create(writerTable, tempDir);

            byteColumn.put((byte) 42);
            charColumn.put('a');
            intColumn.put(42);
            longColumn.put(42L);
            floatColumn.put(42.0f);
            doubleColumn.put(42.0);

            byteArrayColumn.put(new byte[] { 42, 43, 44 });
            intArrayColumn.put(new int[] { 42, 43, 44 });
            longArrayColumn.put(new long[] { 42, 43, 44 });

            cstringColumn.put("Hello");
            txtStringColumn.put("Hello");
            arrayStringColumn.put("Hello");
            enumColumn.put("Hello");

            varintColumn.put(10000000);
        }

        try (SlopTable readerTable = new SlopTable(0)) {
            var byteColumn = byteCol.open(readerTable, tempDir);
            var charColumn = charCol.open(readerTable, tempDir);
            var intColumn = intCol.open(readerTable, tempDir);
            var longColumn = longCol.open(readerTable, tempDir);
            var floatColumn = floatCol.open(readerTable, tempDir);
            var doubleColumn = doubleCol.open(readerTable, tempDir);
            var byteArrayColumn = byteArrayCol.open(readerTable, tempDir);
            var intArrayColumn = intArrayCol.open(readerTable, tempDir);
            var longArrayColumn = longArrayCol.open(readerTable, tempDir);
            var cstringColumn = cstringCol.open(readerTable, tempDir);
            var txtStringColumn = txtStringCol.open(readerTable, tempDir);
            var arrayStringColumn = arrayStringCol.open(readerTable, tempDir);
            var enumColumn = enumCol.open(readerTable, tempDir);
            var varintColumn = varintCol.open(readerTable, tempDir);

            assertEquals(42, byteColumn.get());
            assertEquals('a', charColumn.get());
            assertEquals(42, intColumn.get());
            assertEquals(42L, longColumn.get());
            assertEquals(42.0f, floatColumn.get());
            assertEquals(42.0, doubleColumn.get());

            assertArrayEquals(new byte[] {42, 43, 44}, byteArrayColumn.get());
            assertArrayEquals(new int[] {42, 43, 44}, intArrayColumn.get());
            assertArrayEquals(new long[] {42, 43, 44}, longArrayColumn.get());

            assertEquals("Hello", cstringColumn.get());
            assertEquals("Hello", txtStringColumn.get());
            assertEquals("Hello", arrayStringColumn.get());
            assertEquals("Hello", enumColumn.get());

            assertEquals(10000000, varintColumn.get());
        }

    }
}
