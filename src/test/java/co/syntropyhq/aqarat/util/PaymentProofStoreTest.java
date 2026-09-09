package co.syntropyhq.aqarat.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A payment proof is evidence somebody's money rests on, so the two things
 * that matter are that a file which is not what it claims to be never reaches
 * the uploads folder, and that one upload never lands on top of another.
 */
class PaymentProofStoreTest {

    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '4'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private final List<Path> stored = new ArrayList<>();

    @AfterEach
    void removeWhatWasStored() throws IOException {
        for (Path path : stored) {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void aRealPdfIsStoredUnderProofsAndKeepsItsExtension(@TempDir Path temp) throws IOException {
        Path source = write(temp, "slip.pdf", PDF);

        String path = record(PaymentProofStore.store(314, source));

        assertTrue(path.startsWith("proofs/"), path);
        assertTrue(path.endsWith(".pdf"), path);
        assertTrue(Files.exists(Uploads.resolve(path)));
    }

    @Test
    void theStoredNameCarriesNothingTheUserChose(@TempDir Path temp) throws IOException {
        Path source = write(temp, "../../etc/passwd.png", PNG);

        String path = record(PaymentProofStore.store(7, source));

        assertEquals("proofs/", path.substring(0, 7));
        assertTrue(path.contains("pay-7-"), path);
        assertTrue(!path.contains("passwd") && !path.contains(".."), path);
    }

    @Test
    void twoUploadsNeverLandOnTheSameFile(@TempDir Path temp) throws IOException {
        Path first = write(temp, "one.pdf", PDF);
        Path second = write(temp, "two.pdf", PDF);

        String a = record(PaymentProofStore.store(42, first));
        String b = record(PaymentProofStore.store(42, second));

        assertNotEquals(a, b);
        assertTrue(Files.exists(Uploads.resolve(a)));
        assertTrue(Files.exists(Uploads.resolve(b)));
    }

    @Test
    void anExecutableRenamedToPdfIsRefused(@TempDir Path temp) throws IOException {
        Path source = write(temp, "invoice.pdf", new byte[] {'M', 'Z', 0x0, 0x0, 0x0, 0x0, 0x0, 0x0});

        IOException refused = assertThrows(IOException.class,
            () -> PaymentProofStore.store(1, source));

        assertTrue(refused.getMessage().contains("do not match"), refused.getMessage());
    }

    @Test
    void validatingCopiesNothing(@TempDir Path temp) throws IOException {
        Path source = write(temp, "slip.pdf", PDF);
        long before = countProofs();

        PaymentProofStore.validate(source);

        assertEquals(before, countProofs());
    }

    private String record(String storedPath) {
        stored.add(Uploads.resolve(storedPath));
        return storedPath;
    }

    private long countProofs() throws IOException {
        Path dir = Uploads.root().resolve("proofs");
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (var entries = Files.list(dir)) {
            return entries.count();
        }
    }

    private Path write(Path temp, String name, byte[] content) throws IOException {
        // The name is only ever a claim; what matters is that nothing built
        // from it reaches the uploads folder.
        Path file = temp.resolve(name.replace("/", "_"));
        Files.write(file, content);
        return file;
    }
}
