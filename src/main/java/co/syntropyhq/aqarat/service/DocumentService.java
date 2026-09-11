package co.syntropyhq.aqarat.service;

import co.syntropyhq.aqarat.dao.PropertyDao;
import co.syntropyhq.aqarat.dao.PropertyDocumentDao;
import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.NewDocument;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.PropertyDocument;
import co.syntropyhq.aqarat.model.Role;
import co.syntropyhq.aqarat.util.Db;
import co.syntropyhq.aqarat.util.DocumentStore;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Ownership evidence: who uploaded what, and which member of staff has looked
 * at it.
 *
 * <p>Every method here takes the {@link AppUser} doing the work rather than
 * an id. Elsewhere in this application a service is handed an id that the
 * controller took from the session, which is fine for a client's own
 * contracts - the worst case is a customer seeing their own data twice. These
 * files are scans of national identity documents, so the check that the
 * caller is entitled to them is made here, against the row, and not left to
 * whichever screen happens to call.
 */
public class DocumentService {

    private final PropertyDocumentDao propertyDocumentDao;
    private final PropertyDao propertyDao;
    private final AuditService auditService;

    public DocumentService(PropertyDocumentDao propertyDocumentDao, PropertyDao propertyDao,
            AuditService auditService) {
        this.propertyDocumentDao = propertyDocumentDao;
        this.propertyDao = propertyDao;
        this.auditService = auditService;
    }

    /**
     * The documents on a property, for someone entitled to see them: any
     * member of staff, or the owner of that property and nobody else.
     */
    public List<PropertyDocument> findForProperty(int propertyId, AppUser viewer)
            throws SQLException, NotPermittedException {
        try (Connection connection = Db.get()) {
            requireCanSee(connection, propertyId, viewer);
            return propertyDocumentDao.findByProperty(connection, propertyId);
        }
    }

    public int countVerified(int propertyId) throws SQLException {
        try (Connection connection = Db.get()) {
            return propertyDocumentDao.countVerified(connection, propertyId);
        }
    }

    /**
     * Stores chosen files and records them, all in one transaction. A copy
     * that fails halfway leaves no rows behind, the same rule photo upload
     * follows in {@code PropertyService.submit}.
     *
     * <p>The files are validated before the transaction opens, so a rejected
     * file never costs a rollback and the owner is told which file and why.
     */
    public int upload(int propertyId, List<NewDocument> documents, AppUser actor)
            throws SQLException, IOException, NotPermittedException {
        if (documents.isEmpty()) {
            return 0;
        }
        for (NewDocument document : documents) {
            DocumentStore.validate(document.getPath());
        }
        try (Connection connection = Db.get()) {
            requireCanSee(connection, propertyId, actor);
            connection.setAutoCommit(false);
            try {
                int index = propertyDocumentDao.findByProperty(connection, propertyId).size();
                for (NewDocument document : documents) {
                    store(connection, propertyId, index, document, actor);
                    index++;
                }
                connection.commit();
                return documents.size();
            } catch (SQLException | IOException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private void store(Connection connection, int propertyId, int index, NewDocument source,
            AppUser actor) throws SQLException, IOException {
        String filePath = DocumentStore.store(propertyId, index, source.getPath());
        PropertyDocument document = new PropertyDocument();
        document.setPropertyId(propertyId);
        document.setDocType(source.getDocType());
        document.setFilePath(filePath);
        document.setOriginalName(source.getPath().getFileName().toString());
        document.setUploadedBy(actor.getId());
        int id = propertyDocumentDao.insert(connection, document);
        auditService.record(connection, "property_document", id, "UPLOAD", null,
            source.getDocType().name());
    }

    /**
     * A member of staff recording that they have looked at a document and it
     * is what it claims to be.
     *
     * <p>The {@code verified_by IS NULL} guard lives in the UPDATE rather than
     * in a read before it, so two agents verifying the same document cannot
     * both succeed: the first changes one row, the second changes none and is
     * refused. Verification is not reversible here - an agency that has told
     * itself a deed was checked should not be able to quietly un-tell itself.
     */
    public void verify(int documentId, AppUser staff)
            throws SQLException, NotPermittedException, AlreadyVerifiedException {
        requireStaff(staff);
        try (Connection connection = Db.get()) {
            connection.setAutoCommit(false);
            try {
                int changed = propertyDocumentDao.markVerified(
                    connection, documentId, staff.getId(), LocalDateTime.now(ZoneOffset.UTC));
                if (changed == 0) {
                    throw new AlreadyVerifiedException(
                        "This document has already been verified.");
                }
                // Not the verifier's name: audit_log already records who acted,
                // and repeating it renders as "Rami Khoury - Rami Khoury".
                auditService.record(connection, "property_document", documentId, "VERIFY",
                    "UNVERIFIED", "VERIFIED");
                connection.commit();
            } catch (SQLException | AlreadyVerifiedException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private void requireCanSee(Connection connection, int propertyId, AppUser viewer)
            throws SQLException, NotPermittedException {
        if (viewer == null) {
            throw new NotPermittedException("Sign in to view ownership documents.");
        }
        if (viewer.getRole() != Role.CUSTOMER) {
            return;
        }
        Property property = propertyDao.findById(connection, propertyId);
        if (property == null || property.getOwnerId() != viewer.getId()) {
            throw new NotPermittedException(
                "Ownership documents are visible only to the owner and to Aqarat staff.");
        }
    }

    private void requireStaff(AppUser actor) throws NotPermittedException {
        if (actor == null || actor.getRole() == Role.CUSTOMER) {
            throw new NotPermittedException("Only Aqarat staff can verify a document.");
        }
    }

    // A refusal to show or change something, distinct from a database failure
    // so a controller can show the reason rather than a generic error
    // (CONVENTIONS.md, Errors).
    public static class NotPermittedException extends Exception {

        public NotPermittedException(String message) {
            super(message);
        }
    }

    public static class AlreadyVerifiedException extends Exception {

        public AlreadyVerifiedException(String message) {
            super(message);
        }
    }
}
