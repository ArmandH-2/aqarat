package co.syntropyhq.aqarat.service;

import co.syntropyhq.aqarat.dao.ViewingDao;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.Viewing;
import co.syntropyhq.aqarat.model.ViewingStatus;
import co.syntropyhq.aqarat.util.Db;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public class ViewingService {

    // SQL Server's numbers for a unique-index and a unique-constraint
    // violation. ux_viewing_agent_slot is the only unique index this
    // transaction can hit, so either code means the same thing here.
    private static final int SQL_ERROR_UNIQUE_INDEX = 2601;
    private static final int SQL_ERROR_UNIQUE_CONSTRAINT = 2627;

    private final ViewingDao viewingDao;
    private final PropertyService propertyService;
    private final AuditService auditService;

    public ViewingService(ViewingDao viewingDao, PropertyService propertyService,
            AuditService auditService) {
        this.viewingDao = viewingDao;
        this.propertyService = propertyService;
        this.auditService = auditService;
    }

    public Viewing findById(int viewingId) throws SQLException {
        return viewingDao.findById(viewingId);
    }

    public List<Viewing> findByAgent(int agentId) throws SQLException {
        return viewingDao.findByAgent(agentId);
    }

    // The confirm-or-decline queue: any agent may pick up a REQUESTED
    // viewing, the same way an unclaimed property waits in the shared queue
    // rather than one agent's own (DESIGN.md section 6).
    public List<Viewing> findRequested() throws SQLException {
        return viewingDao.findByStatus(ViewingStatus.REQUESTED);
    }

    // client_id is bound into the WHERE clause by ViewingDao, not applied by
    // filtering a full result set afterwards - a client can only ever see
    // rows the query itself was restricted to (DESIGN.md section 8).
    public List<Viewing> findByClient(int clientId) throws SQLException {
        return viewingDao.findByClient(clientId);
    }

    public List<Viewing> findByProperty(int propertyId) throws SQLException {
        return viewingDao.findByProperty(propertyId);
    }

    public List<Viewing> findByAgentInRange(int agentId, LocalDateTime from, LocalDateTime to)
            throws SQLException {
        return viewingDao.findByAgentInRange(agentId, from, to);
    }

    /**
     * A client asking to see a property. Starts REQUESTED with no agent -
     * any agent may confirm it, the same way an unclaimed property submission
     * waits for whoever picks it up first (DESIGN.md section 6). The owner
     * cannot ask to view their own listing; the details screen hides the
     * request for them, and this is the backstop that refuses it anyway.
     */
    public int request(int propertyId, int clientId, LocalDateTime scheduledAt)
            throws SQLException, CannotRequestOwnPropertyException {
        Property property = propertyService.findById(propertyId);
        if (property == null) {
            throw new IllegalArgumentException("No property with id " + propertyId + ".");
        }
        if (property.getOwnerId() == clientId) {
            throw new CannotRequestOwnPropertyException(
                "You cannot request a viewing of your own property.");
        }
        Viewing viewing = new Viewing();
        viewing.setPropertyId(propertyId);
        viewing.setClientId(clientId);
        viewing.setAgentId(null);
        viewing.setScheduledAt(scheduledAt);
        viewing.setStatus(ViewingStatus.REQUESTED);
        try (Connection connection = Db.get()) {
            connection.setAutoCommit(false);
            try {
                int id = viewingDao.insert(connection, viewing);
                auditService.record(connection, "viewing", id, "REQUEST", null,
                    ViewingStatus.REQUESTED.name());
                connection.commit();
                return id;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /**
     * An agent takes a requested slot. This is the one transition that also
     * assigns agent_id, and the one place ux_viewing_agent_slot can fire: two
     * agents cannot hold a CONFIRMED viewing at the same instant. The slot is
     * checked before the write for a clean message, and the write is still
     * caught in case another confirmation landed in between (CLAUDE.md, the
     * canonical transaction shape).
     */
    public void confirm(int viewingId, int agentId)
            throws SQLException, InvalidTransitionException, SlotTakenException {
        try (Connection connection = Db.get()) {
            connection.setAutoCommit(false);
            try {
                Viewing viewing = requireViewing(connection, viewingId);
                requireLegalTransition(viewing.getStatus(), ViewingStatus.CONFIRMED);
                if (viewingDao.hasConfirmedSlot(connection, agentId, viewing.getScheduledAt())) {
                    throw new SlotTakenException(
                        "You already have a confirmed viewing at this time.");
                }
                writeConfirmed(connection, viewingId, agentId);
                auditService.record(connection, "viewing", viewingId, "CONFIRM",
                    viewing.getStatus().name(), ViewingStatus.CONFIRMED.name());
                connection.commit();
            } catch (SQLException | InvalidTransitionException | SlotTakenException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private void writeConfirmed(Connection connection, int viewingId, int agentId)
            throws SQLException, SlotTakenException {
        try {
            viewingDao.updateStatus(connection, viewingId, ViewingStatus.CONFIRMED, agentId);
        } catch (SQLException e) {
            if (isDoubleBookingViolation(e)) {
                throw new SlotTakenException(
                    "Another confirmation for this slot landed first. Choose a different time.");
            }
            throw e;
        }
    }

    private boolean isDoubleBookingViolation(SQLException e) {
        int code = e.getErrorCode();
        boolean isUniqueViolation =
            code == SQL_ERROR_UNIQUE_INDEX || code == SQL_ERROR_UNIQUE_CONSTRAINT;
        String message = e.getMessage();
        return isUniqueViolation && message != null && message.contains("ux_viewing_agent_slot");
    }

    /**
     * Either side backing out of a viewing that has not happened yet.
     * CANCELLED is terminal, matching how CLAUDE.md's forbidden patterns rule
     * out inventing a DECLINED status for what the enum already covers.
     */
    public void cancel(int viewingId) throws SQLException, InvalidTransitionException {
        try (Connection connection = Db.get()) {
            connection.setAutoCommit(false);
            try {
                Viewing viewing = requireViewing(connection, viewingId);
                requireLegalTransition(viewing.getStatus(), ViewingStatus.CANCELLED);
                viewingDao.updateStatus(connection, viewingId, ViewingStatus.CANCELLED,
                    viewing.getAgentId());
                auditService.record(connection, "viewing", viewingId, "CANCEL",
                    viewing.getStatus().name(), ViewingStatus.CANCELLED.name());
                connection.commit();
            } catch (SQLException | InvalidTransitionException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /**
     * What actually happened at a confirmed viewing: the client showed up
     * (COMPLETED) or did not (NO_SHOW). Only a confirmed slot can have an
     * outcome - nobody attends a viewing that was never confirmed.
     */
    public void recordOutcome(int viewingId, ViewingStatus outcome, String outcomeNote)
            throws SQLException, InvalidTransitionException {
        try (Connection connection = Db.get()) {
            connection.setAutoCommit(false);
            try {
                Viewing viewing = requireViewing(connection, viewingId);
                requireLegalTransition(viewing.getStatus(), outcome);
                viewingDao.updateStatus(connection, viewingId, outcome, viewing.getAgentId());
                viewingDao.updateOutcomeNote(connection, viewingId, outcomeNote);
                auditService.record(connection, "viewing", viewingId, "OUTCOME",
                    viewing.getStatus().name(), outcome.name());
                connection.commit();
            } catch (SQLException | InvalidTransitionException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private Viewing requireViewing(Connection connection, int viewingId) throws SQLException {
        Viewing viewing = viewingDao.findById(connection, viewingId);
        if (viewing == null) {
            throw new IllegalArgumentException("No viewing with id " + viewingId + ".");
        }
        return viewing;
    }

    private void requireLegalTransition(ViewingStatus from, ViewingStatus to)
            throws InvalidTransitionException {
        if (!isLegalTransition(from, to)) {
            throw new InvalidTransitionException(
                "Cannot move a viewing from " + from + " to " + to + ".");
        }
    }

    // The viewing lifecycle from DESIGN.md section 5 and 8: a request is
    // confirmed or cancelled outright, and only a confirmed viewing can be
    // cancelled after the fact or given an outcome - COMPLETED and NO_SHOW
    // both describe something that happened at an appointment that was
    // actually on the books. COMPLETED, CANCELLED and NO_SHOW are terminal.
    private boolean isLegalTransition(ViewingStatus from, ViewingStatus to) {
        switch (from) {
            case REQUESTED:
                return to == ViewingStatus.CONFIRMED || to == ViewingStatus.CANCELLED;
            case CONFIRMED:
                return to == ViewingStatus.COMPLETED || to == ViewingStatus.NO_SHOW
                    || to == ViewingStatus.CANCELLED;
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

    // What the user sees for a double-booked slot - never a stack trace, and
    // never the raw SQL error code (BUILD-ORDER.md, phase 4 track A).
    public static class SlotTakenException extends Exception {

        public SlotTakenException(String message) {
            super(message);
        }
    }

    public static class CannotRequestOwnPropertyException extends Exception {

        public CannotRequestOwnPropertyException(String message) {
            super(message);
        }
    }
}
