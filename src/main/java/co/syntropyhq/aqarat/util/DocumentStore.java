package co.syntropyhq.aqarat.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Stores ownership documents under uploads/proofs.
 *
 * <p>The same shape as {@link PhotoStore}, with two differences that come
 * from what these files are. First, the content is checked and not just the
 * file name: a document is opened by a member of staff later, so accepting
 * an executable that someone renamed to .pdf would be handing them a
 * problem. Second, an existing file is never overwritten - photos can be
 * replaced, evidence cannot.
 */
public final class DocumentStore {

    private static final Path PROOFS_DIR = Uploads.root().resolve("proofs");

    /** Ten megabytes. A phone photograph of a deed is one or two. */
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC =
        {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private DocumentStore() {
    }

    /**
     * Copies a chosen file in and returns its path relative to the uploads
     * root, which is what property_document.file_path stores.
     *
     * <p>The stored name is generated rather than taken from the user, which
     * is what makes directory traversal, null bytes and Windows reserved
     * names structurally impossible instead of merely filtered.
     */
    public static String store(int propertyId, int index, Path source) throws IOException {
        String extension = validate(source);
        Files.createDirectories(PROOFS_DIR);
        String filename = "d" + propertyId + "-" + index + "." + extension;
        // No REPLACE_EXISTING: silently overwriting a stored document would
        // destroy the only copy of something an agency relies on.
        Files.copy(source, PROOFS_DIR.resolve(filename));
        return "proofs/" + filename;
    }

    /**
     * Checks a file the user picked before anything is copied, so the error
     * arrives while they are still looking at the chooser. Returns the
     * extension to store it under.
     */
    public static String validate(Path source) throws IOException {
        if (!Files.isRegularFile(source)) {
            throw new IOException("That is not a file.");
        }
        long size = Files.size(source);
        if (size == 0) {
            throw new IOException("That file is empty.");
        }
        if (size > MAX_BYTES) {
            throw new IOException("Documents must be 10 MB or smaller.");
        }
        String extension = extension(source);
        if (!contentMatches(source, extension)) {
            throw new IOException(
                "That file's contents do not match its ." + extension + " ending.");
        }
        return extension;
    }

    private static String extension(Path source) throws IOException {
        String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) {
            return "pdf";
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "jpg";
        }
        if (name.endsWith(".png")) {
            return "png";
        }
        throw new IOException("Only PDF, JPG or PNG documents are accepted.");
    }

    // An extension is a claim by whoever named the file. The first few bytes
    // are the file itself, which is why both are checked.
    private static boolean contentMatches(Path source, String extension) throws IOException {
        byte[] head = head(source, 8);
        return switch (extension) {
            case "pdf" -> startsWith(head, PDF_MAGIC);
            case "jpg" -> startsWith(head, JPEG_MAGIC);
            case "png" -> startsWith(head, PNG_MAGIC);
            default -> false;
        };
    }

    private static byte[] head(Path source, int count) throws IOException {
        byte[] buffer = new byte[count];
        try (InputStream in = Files.newInputStream(source)) {
            int read = in.readNBytes(buffer, 0, count);
            if (read < count) {
                // A file shorter than any signature cannot match one.
                return new byte[0];
            }
        }
        return buffer;
    }

    private static boolean startsWith(byte[] head, byte[] magic) {
        if (head.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (head[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
