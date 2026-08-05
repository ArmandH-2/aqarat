package co.syntropyhq.aqarat.service;

import co.syntropyhq.aqarat.dao.PropertyDao;
import co.syntropyhq.aqarat.dao.PropertyPhotoDao;
import co.syntropyhq.aqarat.dao.PropertySearch;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.PropertyPhoto;
import co.syntropyhq.aqarat.model.PropertyStatus;
import co.syntropyhq.aqarat.util.Db;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

public class PropertyService {

    private final PropertyDao propertyDao;
    private final PropertyPhotoDao propertyPhotoDao;
    private final AuditService auditService;

    public PropertyService(PropertyDao propertyDao, PropertyPhotoDao propertyPhotoDao,
            AuditService auditService) {
        this.propertyDao = propertyDao;
        this.propertyPhotoDao = propertyPhotoDao;
        this.auditService = auditService;
    }

    public List<PropertyPhoto> findPhotos(int propertyId) throws SQLException {
        return propertyPhotoDao.findByProperty(propertyId);
    }

    // Guests and clients only ever search AVAILABLE stock. Passing the status
    // here, rather than letting a caller choose it, is what keeps a draft or
    // a rejected submission from ever reaching a public screen.
    public List<Property> searchPublished(PropertySearch filters, int offset, int pageSize)
            throws SQLException {
        try (Connection connection = Db.get()) {
            return propertyDao.search(
                connection, List.of(PropertyStatus.AVAILABLE), filters, offset, pageSize);
        }
    }

    public List<Property> searchForStaff(List<PropertyStatus> statuses, PropertySearch filters,
            int offset, int pageSize) throws SQLException {
        try (Connection connection = Db.get()) {
            return propertyDao.search(connection, statuses, filters, offset, pageSize);
        }
    }

    public int count(List<PropertyStatus> statuses, PropertySearch filters) throws SQLException {
        try (Connection connection = Db.get()) {
            return propertyDao.count(connection, statuses, filters);
        }
    }

    public Property findById(int id) throws SQLException {
        return propertyDao.findById(id);
    }

    // ownerId is bound into the WHERE clause by PropertyDao, not applied by
    // filtering a full result set afterwards - a customer can only ever see
    // rows the query itself was restricted to (DESIGN.md section 8).
    public List<Property> findByOwner(int ownerId) throws SQLException {
        return propertyDao.findByOwner(ownerId);
    }

    public List<Property> findByAgent(int agentId) throws SQLException {
        return propertyDao.findByAgent(agentId);
    }

    /**
     * Submits a new property. A submission is created and reviewed in the
     * same step - there is no persisted DRAFT row to save and come back to -
     * so this writes the property straight in at PENDING_REVIEW and stamps
     * submitted_at, rather than inserting a DRAFT row and immediately
     * transitioning it. Both writes (the property and its audit entry) are
     * one transaction.
     */
    public int submit(Property property) throws SQLException {
        property.setStatus(PropertyStatus.PENDING_REVIEW);
        property.setSubmittedAt(LocalDateTime.now(ZoneOffset.UTC));
        try (Connection connection = Db.get()) {
            connection.setAutoCommit(false);
            try {
                int id = propertyDao.insert(connection, property);
                auditService.record(connection, "property", id, "CREATE", null,
                    PropertyStatus.PENDING_REVIEW.name());
                connection.commit();
                return id;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    public void withdraw(int propertyId) throws SQLException, InvalidTransitionException {
        changeStatus(propertyId, PropertyStatus.WITHDRAWN);
    }

    /**
     * An owner asking for a published listing to be taken down. The listing
     * stays visible and reservable until an agent answers, because nothing has
     * been decided yet (DESIGN.md section 6). The reason is kept as the review
     * note so the agent sees it in the queue.
     */
    public void requestWithdrawal(int propertyId, String reason)
            throws SQLException, InvalidTransitionException {
        review(propertyId, PropertyStatus.WITHDRAWAL_REQUESTED, reason);
    }

    /**
     * Records an agent's decision on a submission: the new status and the note
     * explaining it are written together, so a rejection or a request for more
     * information can never reach the owner without its reason. Pass a null
     * note for an approval, which needs none.
     */
    public void review(int propertyId, PropertyStatus decision, String reviewNote)
            throws SQLException, InvalidTransitionException {
        try (Connection connection = Db.get()) {
            connection.setAutoCommit(false);
            try {
                Property property = requireProperty(connection, propertyId);
                PropertyStatus oldStatus = property.getStatus();
                requireLegalTransition(oldStatus, decision);
                property.setStatus(decision);
                stampTimestamp(property, decision);
                propertyDao.updateStatus(connection, property);
                propertyDao.updateReviewNote(connection, propertyId, reviewNote);
                auditService.record(connection, "property", propertyId, "REVIEW",
                    oldStatus.name(), decision.name());
                connection.commit();
            } catch (SQLException | InvalidTransitionException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /**
     * Claims an unassigned submission for an agent. DESIGN.md section 6 says
     * any agent may take a property from the unassigned queue and there is no
     * approval step, but a submission already claimed by someone else is not
     * quietly reassigned - that is an admin action.
     */
    public void claim(int propertyId, int agentId) throws SQLException, AlreadyClaimedException {
        try (Connection connection = Db.get()) {
            connection.setAutoCommit(false);
            try {
                Property property = requireProperty(connection, propertyId);
                if (property.getAgentId() != null && property.getAgentId() != agentId) {
                    throw new AlreadyClaimedException(
                        "Another agent has already taken this submission.");
                }
                propertyDao.updateAgent(connection, propertyId, agentId);
                auditService.record(connection, "property", propertyId, "CLAIM",
                    String.valueOf(property.getAgentId()), String.valueOf(agentId));
                connection.commit();
            } catch (SQLException | AlreadyClaimedException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private Property requireProperty(Connection connection, int propertyId) throws SQLException {
        Property property = propertyDao.findById(connection, propertyId);
        if (property == null) {
            throw new IllegalArgumentException("No property with id " + propertyId + ".");
        }
        return property;
    }

    /**
     * Moves a property to a new status as part of a larger piece of work. The
     * caller owns the connection, so the status change, whatever else it
     * belongs with, and the audit entry all commit or all roll back together.
     * A reservation that exists while its property still reads AVAILABLE is
     * the failure this prevents.
     */
    public void changeStatus(Connection connection, int propertyId, PropertyStatus newStatus)
            throws SQLException, InvalidTransitionException {
        Property property = requireProperty(connection, propertyId);
        PropertyStatus oldStatus = property.getStatus();
        requireLegalTransition(oldStatus, newStatus);
        property.setStatus(newStatus);
        stampTimestamp(property, newStatus);
        propertyDao.updateStatus(connection, property);
        auditService.record(connection, "property", propertyId, "STATUS_CHANGE",
            oldStatus.name(), newStatus.name());
    }

    /**
     * Moves a property to a new status on its own. This is the only place
     * property.status is ever written (DESIGN.md section 6) - every screen,
     * in every phase, comes through here rather than writing the column
     * itself. The transition is checked against the state machine first;
     * an illegal move is refused, not silently ignored.
     */
    public void changeStatus(int propertyId, PropertyStatus newStatus)
            throws SQLException, InvalidTransitionException {
        try (Connection connection = Db.get()) {
            connection.setAutoCommit(false);
            try {
                changeStatus(connection, propertyId, newStatus);
                connection.commit();
            } catch (SQLException | InvalidTransitionException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    // published_at is re-stamped every time a property returns to the market,
    // because the reports measure time on market from the most recent listing
    // rather than from the first one.
    private void stampTimestamp(Property property, PropertyStatus newStatus) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (newStatus == PropertyStatus.PENDING_REVIEW) {
            property.setSubmittedAt(now);
        } else if (newStatus == PropertyStatus.AVAILABLE) {
            property.setPublishedAt(now);
        } else if (newStatus == PropertyStatus.CLOSED) {
            property.setClosedAt(now);
        }
    }

    private void requireLegalTransition(PropertyStatus from, PropertyStatus to)
            throws InvalidTransitionException {
        if (!isLegalTransition(from, to)) {
            throw new InvalidTransitionException(
                "Cannot move a property from " + from + " to " + to + ".");
        }
    }

    // The property lifecycle from DESIGN.md section 6: a submission moves
    // through review, a published listing moves through reservation and
    // contract to close, and the design calls out three loops that must
    // work - NEEDS_INFO back to review, a reservation lapsing back to
    // AVAILABLE, and a lease ending back to AVAILABLE. REJECTED, WITHDRAWN
    // and CLOSED are terminal - nothing moves out of them.
    private boolean isLegalTransition(PropertyStatus from, PropertyStatus to) {
        switch (from) {
            case DRAFT:
                return to == PropertyStatus.PENDING_REVIEW || to == PropertyStatus.WITHDRAWN;
            case PENDING_REVIEW:
                return to == PropertyStatus.NEEDS_INFO || to == PropertyStatus.REJECTED
                    || to == PropertyStatus.AVAILABLE || to == PropertyStatus.WITHDRAWN;
            case NEEDS_INFO:
                return to == PropertyStatus.PENDING_REVIEW || to == PropertyStatus.WITHDRAWN;
            case AVAILABLE:
                return to == PropertyStatus.RESERVED
                    || to == PropertyStatus.WITHDRAWAL_REQUESTED;
            case WITHDRAWAL_REQUESTED:
                return to == PropertyStatus.WITHDRAWN || to == PropertyStatus.AVAILABLE;
            case RESERVED:
                return to == PropertyStatus.UNDER_CONTRACT || to == PropertyStatus.AVAILABLE;
            case UNDER_CONTRACT:
                return to == PropertyStatus.CLOSED || to == PropertyStatus.AVAILABLE;
            default:
                return false;
        }
    }

    // A distinct, checked type so a controller can tell "you asked for a
    // transition the state machine does not allow" apart from a database
    // failure, and show the right message for each (CLAUDE.md, Errors).
    public static class InvalidTransitionException extends Exception {

        public InvalidTransitionException(String message) {
            super(message);
        }
    }

    // Two agents opening the same unassigned queue is ordinary, so losing the
    // race is a message to read, not a failure.
    public static class AlreadyClaimedException extends Exception {

        public AlreadyClaimedException(String message) {
            super(message);
        }
    }
}
