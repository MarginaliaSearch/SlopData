package nu.marginalia.slop.desc;

import nu.marginalia.slop.SlopTable;
import nu.marginalia.slop.column.array.ByteArrayColumn;
import nu.marginalia.slop.column.array.IntArrayColumn;
import nu.marginalia.slop.column.array.LongArrayColumn;
import nu.marginalia.slop.column.dynamic.VarintColumn;
import nu.marginalia.slop.column.primitive.*;
import nu.marginalia.slop.column.string.CStringColumn;
import nu.marginalia.slop.column.string.StringColumn;
import nu.marginalia.slop.column.string.TxtStringColumn;
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
        var cd1 = new IntColumn("test1", StorageType.PLAIN);
        var cd2 = new IntColumn("test2", StorageType.PLAIN);

        try (SlopTable writerTable = new SlopTable(0)) {
            var column1 = cd1.create(writerTable, tempDir);
            var column2 = cd2.create(writerTable, tempDir);

            column1.put(42);
            column2.put(43);
        }


        try (SlopTable readerTable = new SlopTable(0)) {
            var column1 = cd1.open(readerTable, tempDir);
            var column2 = cd2.open(readerTable, tempDir);

            assertEquals(42, column1.get());
            assertEquals(43, column2.get());
        }
    }


    @Test
    public void testPositionsMisaligned() throws IOException {
        var cd1 = new IntColumn("test1", StorageType.PLAIN);
        var cd2 = new IntColumn("test2", StorageType.PLAIN);

        boolean sawException = false;
        try (SlopTable writerTable = new SlopTable(0)) {
            var column1 = cd1.create(writerTable, tempDir);
            var column2 = cd2.create(writerTable, tempDir);

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
        var byteCol = new ByteColumn("byte");
        var charCol = new CharColumn("char");
        var intCol = new IntColumn("int");
        var longCol = new LongColumn("long");
        var floatCol = new FloatColumn("float");
        var doubleCol = new DoubleColumn("double");
        var byteArrayCol = new ByteArrayColumn("byteArray");
        var intArrayCol = new IntArrayColumn("intArray");
        var longArrayCol = new LongArrayColumn("longArray");
        var cstringCol = new CStringColumn("cstring");
        var txtStringCol = new TxtStringColumn("txtString");
        var arrayStringCol = new StringColumn("arrayString");
        var varintCol = new VarintColumn("varint");
        var enumCol = new StringColumn("enum");


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
