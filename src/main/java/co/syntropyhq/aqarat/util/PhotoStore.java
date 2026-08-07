package co.syntropyhq.aqarat.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class PhotoStore {

    private static final Path UPLOADS_DIR = Path.of("uploads");
    private static final Path IMAGES_DIR = UPLOADS_DIR.resolve("images");

    private PhotoStore() {
    }

    /**
     * Stores a photo for a property under uploads/images as
     * p{propertyId}-{index}.{ext}, so two owners picking the same filename
     * can never overwrite each other. Returns the path relative to the
     * uploads root, which is what property_photo.file_path stores and what
     * the gallery renders.
     */
    public static String store(int propertyId, int index, Path source) throws IOException {
        Files.createDirectories(IMAGES_DIR);
        String extension = extension(source);
        String filename = "p" + propertyId + "-" + index + "." + extension;
        Files.copy(source, IMAGES_DIR.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        return "images/" + filename;
    }

    private static String extension(Path source) throws IOException {
        String name = source.getFileName().toString().toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "jpg";
        }
        if (name.endsWith(".png")) {
            return "png";
        }
        throw new IOException("Only JPG or PNG photos are accepted.");
    }
}
