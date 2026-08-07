package co.syntropyhq.aqarat.model;

import java.nio.file.Path;

public class NewPhoto {

    private final Path path;

    public NewPhoto(Path path) {
        this.path = path;
    }

    public Path getPath() {
        return path;
    }
}
