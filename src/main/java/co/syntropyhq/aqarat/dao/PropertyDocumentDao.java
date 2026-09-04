package co.syntropyhq.aqarat.dao;

import co.syntropyhq.aqarat.model.DocumentType;
import co.syntropyhq.aqarat.model.PropertyDocument;
import co.syntropyhq.aqarat.util.Db;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PropertyDocumentDao {

    // The uploader and verifier names are joined here rather than fetched per
    // row by the caller: the dossier lists every document at once, and one
    // query beats a lookup for each.
    private static final String SELECT = """
        SELECT d.id, d.property_id, d.doc_type, d.file_path, d.original_name,
               d.uploaded_by, d.uploaded_at, d.verified_by, d.verified_at,
               up.full_name AS uploader_name, vp.full_name AS verifier_name
        FROM property_document d
        JOIN app_user up ON up.id = d.uploaded_by
        LEFT JOIN app_user vp ON vp.id = d.verified_by
        """;

    public List<PropertyDocument> findByProperty(Connection connection, int propertyId)
            throws SQLException {
        String sql = SELECT + """
            WHERE d.property_id = ?
            ORDER BY d.uploaded_at, d.id
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, propertyId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<PropertyDocument> documents = new ArrayList<>();
                while (resultSet.next()) {
                    documents.add(mapRow(resultSet));
                }
                return documents;
            }
        }
    }

    public List<PropertyDocument> findByProperty(int propertyId) throws SQLException {
        try (Connection connection = Db.get()) {
            return findByProperty(connection, propertyId);
        }
    }

    public PropertyDocument findById(Connection connection, int documentId) throws SQLException {
        String sql = SELECT + "WHERE d.id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, documentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapRow(resultSet) : null;
            }
        }
    }

    public int countVerified(Connection connection, int propertyId) throws SQLException {
        String sql = """
            SELECT COUNT(*) AS verified_count
            FROM property_document
            WHERE property_id = ? AND verified_by IS NOT NULL
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, propertyId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt("verified_count");
            }
        }
    }

    public int insert(Connection connection, PropertyDocument document) throws SQLException {
        String sql = """
            INSERT INTO property_document
                (property_id, doc_type, file_path, original_name, uploaded_by)
            VALUES (?, ?, ?, ?, ?)
            """;
        try (PreparedStatement statement =
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, document.getPropertyId());
            statement.setString(2, document.getDocType().name());
            statement.setString(3, document.getFilePath());
            statement.setString(4, document.getOriginalName());
            statement.setInt(5, document.getUploadedBy());
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                generatedKeys.next();
                return generatedKeys.getInt(1);
            }
        }
    }

    /**
     * Stamps a verification. The {@code verified_by IS NULL} guard makes this
     * the transition itself rather than a read followed by a write: a second
     * agent verifying the same document changes no rows and is told so,
     * instead of quietly replacing the first agent's name on the record.
     */
    public int markVerified(Connection connection, int documentId, int verifierId,
            LocalDateTime verifiedAt) throws SQLException {
        String sql = """
            UPDATE property_document
            SET verified_by = ?, verified_at = ?
            WHERE id = ? AND verified_by IS NULL
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, verifierId);
            statement.setObject(2, verifiedAt);
            statement.setInt(3, documentId);
            return statement.executeUpdate();
        }
    }

    private PropertyDocument mapRow(ResultSet resultSet) throws SQLException {
        PropertyDocument document = new PropertyDocument();
        document.setId(resultSet.getInt("id"));
        document.setPropertyId(resultSet.getInt("property_id"));
        document.setDocType(DocumentType.valueOf(resultSet.getString("doc_type")));
        document.setFilePath(resultSet.getString("file_path"));
        document.setOriginalName(resultSet.getString("original_name"));
        document.setUploadedBy(resultSet.getInt("uploaded_by"));
        document.setUploadedAt(resultSet.getObject("uploaded_at", LocalDateTime.class));
        int verifiedBy = resultSet.getInt("verified_by");
        document.setVerifiedBy(resultSet.wasNull() ? null : verifiedBy);
        document.setVerifiedAt(resultSet.getObject("verified_at", LocalDateTime.class));
        document.setUploaderName(resultSet.getString("uploader_name"));
        document.setVerifierName(resultSet.getString("verifier_name"));
        return document;
    }
}
