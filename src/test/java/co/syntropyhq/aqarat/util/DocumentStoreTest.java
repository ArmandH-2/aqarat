package co.syntropyhq.aqarat.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The document checks, which are the only place in the application where a
 * file arrives from outside and is later opened by a member of staff.
 *
 * <p>No database. {@code DocumentStore.validate} is pure file inspection,
 * which is exactly the kind of thing CONVENTIONS.md says is worth a test because
 * you cannot see it working by clicking.
 */
class DocumentStoreTest {

    @TempDir
    Path folder;

    private Path file(String name, byte[] content) throws IOException {
        Path path = folder.resolve(name);
        Files.write(path, content);
        return path;
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void acceptsARealPdf() throws Exception {
        assertEquals("pdf", DocumentStore.validate(file("deed.pdf", bytes("%PDF-1.7 deed"))));
    }

    @Test
    void acceptsARealPng() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01};
        assertEquals("png", DocumentStore.validate(file("scan.png", png)));
    }

    @Test
    void acceptsARealJpegUnderEitherSpelling() throws Exception {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0, 0};
        assertEquals("jpg", DocumentStore.validate(file("id.jpg", jpeg)));
        assertEquals("jpg", DocumentStore.validate(file("id.jpeg", jpeg)));
    }

    // The one that matters: an executable renamed to .pdf is what makes
    // checking the extension alone worthless, because a member of staff opens
    // these files afterwards.
    @Test
    void refusesAFileWhoseContentsContradictItsName() throws Exception {
        Path disguised = file("deed.pdf", bytes("MZ this is a windows executable"));
        IOException error = assertThrows(IOException.class, () -> DocumentStore.validate(disguised));
        assertEquals("That file's contents do not match its .pdf ending.", error.getMessage());
    }

    @Test
    void refusesAnExtensionItDoesNotAccept() throws Exception {
        Path document = file("deed.docx", bytes("PK"));
        IOException error = assertThrows(IOException.class, () -> DocumentStore.validate(document));
        assertEquals("Only PDF, JPG or PNG documents are accepted.", error.getMessage());
    }

    @Test
    void refusesAnEmptyFile() throws Exception {
        Path empty = file("deed.pdf", new byte[0]);
        assertEquals("That file is empty.",
            assertThrows(IOException.class, () -> DocumentStore.validate(empty)).getMessage());
    }

    // Shorter than the signature it claims: the read comes up short rather
    // than matching a prefix that is not there.
    @Test
    void refusesAFileTooShortToCarryASignature() throws Exception {
        Path stub = file("deed.png", bytes("%P"));
        assertThrows(IOException.class, () -> DocumentStore.validate(stub));
    }

    @Test
    void refusesSomethingThatIsNotAFile() {
        assertThrows(IOException.class, () -> DocumentStore.validate(folder));
    }
}
