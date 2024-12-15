package nu.marginalia.slop;

import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Compresses a slop table directory into an uncompressed zip file while trying to retain
 *  data arranged in such a way it can be mmap:ed and read with zero copies
 * */
public class SlopTablePacker {

    public static void packToSlopZip(Path slopDir, Path out) throws IOException {
        try (var os = new ZipArchiveOutputStream(out)) {
            os.setMethod(ZipArchiveOutputStream.STORED);
            os.setUseZip64(Zip64Mode.Always);

            try (var filesStr = Files.list(slopDir)
                    .filter(Files::isRegularFile))
            {
                var allFiles = filesStr.toList();

                for (var file : allFiles) {
                    try (var is = new BufferedInputStream(Files.newInputStream(file))) {
                        var archiveEntry = os.createArchiveEntry(file, file.toFile().getName());
                        archiveEntry.setAlignment(8); // Align to 8 bytes to ensure we can always safely mmap the data
                        os.putArchiveEntry(archiveEntry);
                        is.transferTo(os);
                        os.closeArchiveEntry();
                    }
                }
            }
            os.finish();
        }
    }

}
