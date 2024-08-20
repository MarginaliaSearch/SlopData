package nu.marginalia.slop;

import nu.marginalia.slop.column.AbstractColumn;
import nu.marginalia.slop.column.ColumnReader;
import nu.marginalia.slop.column.ColumnWriter;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** SlopTable is a utility class for managing a group of columns that are
 * read and written together.  It is used to ensure that the reader and writer
 * positions are maintained correctly between the columns, and to ensure that
 * the columns are closed correctly.
 * <p></p>
 * It is often a good idea to let the reader or writer class for a particular
 * table inherit from SlopTable, so that the table is automatically closed when
 * the reader or writer is closed.
 */

public class SlopTable implements AutoCloseable {
    private final Set<ColumnReader> readerList = new HashSet<>();
    private final Set<ColumnWriter> writerList = new HashSet<>();

    public final URI uri;
    public final int page;

    public SlopTable(Path path) { this(path.toUri(), 0); }
    public SlopTable(URI uri) { this(uri, 0); }
    public SlopTable(Path path, int page) { this(path.toUri(), page); }
    public SlopTable(SlopTable.Ref<?> ref) { this(ref.uri, ref.page); }

    public SlopTable(URI uri, int page) {
        this.uri = uri;
        this.page = page;
    }

    /** Returns the number of pages for the given reference column */
    public static int getNumPages(Path baseDirectory, AbstractColumn<?,?> referenceColumn) {
        for (int page = 0; ; page++) {
            if (!Files.exists(baseDirectory.resolve(referenceColumn.fileName(page)))) {
                return page;
            }
        }
    }

    public static <T> List<Ref<T>> listPages(Path baseDirectory, AbstractColumn<?,?> referenceColumn) {
        List<Ref<T>> refs = new ArrayList<>();

        for (int page = 0; Files.exists(baseDirectory.resolve(referenceColumn.fileName(page))); page++) {
            refs.add(new Ref<>(baseDirectory, page));
        }

        return refs;
    }

    /** Register a column reader with this table.
     * This is typically done implicitly through Column.open().
     * */
    public <T extends ColumnReader> T register(T reader) {
        if (!readerList.add(reader))
            System.err.println("Double registration of " + reader);
        return reader;
    }

    /** Register a column reader with this table.
     * This is typically done implicitly through Column.create().
     * */
    public <T extends ColumnWriter> T register(T writer) {
        if (!writerList.add(writer))
            System.err.println("Double registration of " + writer);
        return writer;
    }

    public long position() throws IOException {
        for (var reader : readerList) {
            return reader.position();
        }

        for (var writer : writerList) {
            return writer.position();
        }

        return 0;
    }

    public void close() throws IOException {

        Map<Long, List<AbstractColumn<?,?>>> positions = new HashMap<>();

        for (ColumnReader reader : readerList) {
            positions.computeIfAbsent(reader.position(), k -> new ArrayList<>()).add(reader.columnDesc());
            reader.close();
        }
        for (ColumnWriter writer : writerList) {
            positions.computeIfAbsent(writer.position(), k -> new ArrayList<>()).add(writer.columnDesc());
            writer.close();
        }


        // Check for the scenario where we have multiple positions
        // and one of the positions is zero, indicating that we haven't
        // read or written to one of the columns.  This is likely a bug,
        // but not necessarily a severe one, so we just log a warning.

        var zeroPositions = Objects.requireNonNullElseGet(positions.remove(0L), List::of);
        if (!zeroPositions.isEmpty() && !positions.isEmpty()) {
            System.err.println("Zero position found in {}, this is likely development debris" + zeroPositions);
        }

        // If there are more than one position and several are non-zero, then we haven't maintained the
        // position correctly between the columns.  This is a disaster, so we throw an exception.
        if (positions.size() > 1) {
            throw new IllegalStateException("Expected only one reader position, found " + positions);
        }
    }

    /** A reference to a table.  This is used to pass around the information needed to create a table in one unit.
     * <p></p>
     * The T parameter exists to be able to indicate which flavor of table is referenced, providing some level of type
     * safety.
     * */
    public record Ref<T>(URI uri, int page) {
        public Ref(Path path) {
            this(path.toUri(), 0);
        }

        public Ref(URI uri) {
            this(uri, 0);
        }

        public Ref(Path path, int page) {
            this(path.toUri(), page);
        }

        public Ref(URI uri, int page) {
            this.uri = uri;
            this.page = page;
        }
    }
}
