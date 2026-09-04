package co.syntropyhq.aqarat.model;

import java.nio.file.Path;

/**
 * A file the owner has chosen but which is not stored yet, paired with what
 * they say it is. The mirror of {@link NewPhoto}, plus the type - a document
 * with no type is not evidence of anything in particular.
 */
public class NewDocument {

    private final Path path;
    private final DocumentType docType;

    public NewDocument(Path path, DocumentType docType) {
        this.path = path;
        this.docType = docType;
    }

    public Path getPath() {
        return path;
    }

    public DocumentType getDocType() {
        return docType;
    }
}
