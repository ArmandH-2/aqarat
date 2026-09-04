package co.syntropyhq.aqarat.util;

import java.nio.file.Path;

/**
 * The one place that knows where uploaded files live.
 *
 * <p>Paths in the database are stored relative to this root - {@code
 * images/p42-0.jpg}, {@code proofs/d42-0.pdf} - so the folder can move
 * without a data migration. Seven places used to rebuild the same prefix
 * themselves, three of them by string concatenation, which is exactly the
 * kind of thing that works until someone runs the application from a
 * different working directory.
 */
public final class Uploads {

    private static final Path ROOT = Path.of("uploads");

    private Uploads() {
    }

    public static Path root() {
        return ROOT;
    }

    /** Resolves a path stored in the database against the uploads root. */
    public static Path resolve(String storedPath) {
        return ROOT.resolve(storedPath);
    }
}
