package co.syntropyhq.aqarat.service;

import co.syntropyhq.aqarat.dao.ReservationDao;
import co.syntropyhq.aqarat.dao.SystemSettingDao;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.PropertyStatus;
import co.syntropyhq.aqarat.model.Reservation;
import co.syntropyhq.aqarat.model.ReservationStatus;
import co.syntropyhq.aqarat.model.SystemSetting;
import co.syntropyhq.aqarat.util.Db;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

public class ReservationService {

    // SQL Server error codes for a unique index or constraint violation. Two
    // clients racing to reserve the same property both pass the readable
    // check below before either has committed, so this is the backstop the
    // filtered unique index (db/schema.sql, ux_reservation_active) exists for.
    private static final int SQL_UNIQUE_INDEX_VIOLATION = 2601;
    private static final int SQL_UNIQUE_CONSTRAINT_VIOLATION = 2627;

    private final ReservationDao reservationDao;
    private final SystemSettingDao systemSettingDao;
    private final PropertyService propertyService;
    private final AuditService auditService;

    public ReservationService(ReservationDao reservationDao, SystemSettingDao systemSettingDao,
            PropertyService propertyService, AuditService auditService) {
        this.reservationDao = reservationDao;
        this.systemSettingDao = systemSettingDao;
        this.propertyService = propertyService;
        this.auditService = auditService;
    }

    /**
     * A client reserves an AVAILABLE property with a deposit. The reservation
     * row, the property's move to RESERVED and both audit entries are one
     * transaction, so the property can never be left on the market holding a
     * reservation nobody can see. The deposit must be positive and the
     * property must still be available when the row is written.
     */
    public int create(int propertyId, int clientId, BigDecimal depositAmount)
            throws SQLException, PropertyService.InvalidTransitionException,
            PropertyNotAvailableException, DuplicateReservationException,
            CannotReserveOwnPropertyException {
        if (depositAmount == null || depositAmount.signum() <= 0) {
            throw new IllegalArgumentException("The deposit must be a positive amount.");
        }
        Property property = propertyService.findById(propertyId);
        if (property == null) {
            throw new IllegalArgumentException("No property with id " + propertyId + ".");
        }
        // The owner's own listing is not on the market for them: reserving
        // it would take their own property off the market against themselves.
        // The details screen hides the actions for the owner; this is the
        // backstop that keeps a crafted request from getting through.
        if (property.getOwnerId() == clientId) {
            throw new CannotReserveOwnPropertyException(
                "You cannot reserve your own property.");
        }
        if (property.getStatus() != PropertyStatus.AVAILABLE) {
            throw new PropertyNotAvailableException(
                "This property is not available to reserve.");
        }

        Reservation reservation = new Reservation();
        reservation.setPropertyId(propertyId);
        reservation.setClientId(clientId);
        reservation.setDepositAmount(depositAmount);
        LocalDateTime reservedAt = LocalDateTime.now(ZoneOffset.UTC);
        reservation.setReservedAt(reservedAt);
        reservation.setExpiresAt(reservedAt.plusDays(reservationDays()));
        reservation.setStatus(ReservationStatus.ACTIVE);

        return insertReservation(reservation);
    }

    // The reservation, the property moving to RESERVED and both audit rows are
    // one transaction: a reservation that exists while its property still
    // reads AVAILABLE would let the next client reserve it too.
    private int insertReservation(Reservation reservation)
            throws SQLException, DuplicateReservationException,
            PropertyService.InvalidTransitionException {
        try (Connection connection = Db.get()) {
            connection.setAutoCommit(false);
            try {
                if (reservationDao.findActiveForProperty(connection, reservation.getPropertyId())
                        != null) {
                    throw new DuplicateReservationException(
                        "This property already has an active reservation.");
                }
                int id = reservationDao.insert(connection, reservation);
                auditService.record(connection, "reservation", id, "CREATE", null,
                    ReservationStatus.ACTIVE.name());
                propertyService.changeStatus(
                    connection, reservation.getPropertyId(), PropertyStatus.RESERVED);
                connection.commit();
                return id;
            } catch (SQLException e) {
                connection.rollback();
                if (isDuplicateActiveReservation(e)) {
                    throw new DuplicateReservationException(
                        "This property already has an active reservation.");
                }
                throw e;
            } catch (DuplicateReservationException | PropertyService.InvalidTransitionException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private boolean isDuplicateActiveReservation(SQLException e) {
        return e.getErrorCode() == SQL_UNIQUE_INDEX_VIOLATION
            || e.getErrorCode() == SQL_UNIQUE_CONSTRAINT_VIOLATION;
    }

    // reservation_days lives in system_setting, never hardcoded. A missing
    // key fails loudly and names the key, rather than silently falling back
    // to a made-up default.
    private int reservationDays() throws SQLException {
        SystemSetting setting = systemSettingDao.findByKey("reservation_days");
        if (setting == null) {
            throw new IllegalStateException("Missing system setting: reservation_days");
        }
        return Integer.parseInt(setting.getValue());
    }

    /**
     * A client's own reservations, an expired ACTIVE one reported as LAPSED.
     * DESIGN.md section 6: lapse is evaluated on read, not by a timer, and
     * the status change is written the next time the row is touched - this
     * read is that touch, so an expired row is persisted as LAPSED here
     * rather than merely displayed that way.
     */
    public List<Reservation> findByClient(int clientId)
            throws SQLException, PropertyService.InvalidTransitionException {
        List<Reservation> reservations;
        try (Connection connection = Db.get()) {
            reservations = reservationDao.findByClient(connection, clientId);
        }
        for (Reservation reservation : reservations) {
            lapseIfExpired(reservation);
        }
        return reservations;
    }

    // Lapsing the reservation and returning its property to the market are one
    // transaction: a LAPSED reservation on a property still reading RESERVED
    // would take that property off the market permanently.
    private void lapseIfExpired(Reservation reservation)
            throws SQLException, PropertyService.InvalidTransitionException {
        if (reservation.getStatus() != ReservationStatus.ACTIVE
                || reservation.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            return;
        }
        writeStatusAndFreeProperty(reservation, ReservationStatus.LAPSED, "LAPSE");
    }

    private void writeStatusAndFreeProperty(Reservation reservation, ReservationStatus newStatus,
            String action) throws SQLException, PropertyService.InvalidTransitionException {
        ReservationStatus oldStatus = reservation.getStatus();
        try (Connection connection = Db.get()) {
            connection.setAutoCommit(false);
            try {
                reservationDao.updateStatus(connection, reservation.getId(), newStatus);
                auditService.record(connection, "reservation", reservation.getId(), action,
                    oldStatus.name(), newStatus.name());
                propertyService.changeStatus(
                    connection, reservation.getPropertyId(), PropertyStatus.AVAILABLE);
                connection.commit();
            } catch (SQLException | PropertyService.InvalidTransitionException e) {
                connection.rollback();
                throw e;
            }
        }
        reservation.setStatus(newStatus);
    }

    /**
     * Cancel by the client or the agent, before it converts. An already
     * expired reservation is lapsed rather than cancelled - that is what it
     * actually is by the time anyone looks at it.
     */
    public void cancel(int reservationId)
            throws SQLException, PropertyService.InvalidTransitionException,
            InvalidTransitionException {
        Reservation reservation = requireReservation(reservationId);
        lapseIfExpired(reservation);
        requireLegalTransition(reservation.getStatus(), ReservationStatus.CANCELLED);
        writeStatusAndFreeProperty(reservation, ReservationStatus.CANCELLED, "CANCEL");
    }

    /**
     * The reservation becomes a contract, on its own connection. Contract
     * activation uses the overload below instead, so the conversion joins
     * that transaction rather than committing on its own.
     */
    public void convert(int reservationId)
            throws SQLException, PropertyService.InvalidTransitionException,
            InvalidTransitionException {
        Reservation reservation = requireReservation(reservationId);
        lapseIfExpired(reservation);
        requireLegalTransition(reservation.getStatus(), ReservationStatus.CONVERTED);
        writeStatus(reservation, ReservationStatus.CONVERTED, "CONVERT");
    }

    /**
     * Converting as part of activating a contract, on the caller's connection
     * so the whole activation is one unit. Still refuses a reservation that
     * has expired or already been used - going straight to the DAO would skip
     * both of those checks, which is the reason this method exists.
     */
    public void convert(Connection connection, int reservationId)
            throws SQLException, InvalidTransitionException {
        Reservation reservation = reservationDao.findById(connection, reservationId);
        if (reservation == null) {
            throw new IllegalArgumentException("No reservation with id " + reservationId + ".");
        }
        if (reservation.getStatus() == ReservationStatus.ACTIVE
                && !reservation.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new InvalidTransitionException(
                "This reservation expired on " + reservation.getExpiresAt() + " and cannot be converted.");
        }
        requireLegalTransition(reservation.getStatus(), ReservationStatus.CONVERTED);
        reservationDao.updateStatus(connection, reservationId, ReservationStatus.CONVERTED);
        auditService.record(connection, "reservation", reservationId, "CONVERT",
            reservation.getStatus().name(), ReservationStatus.CONVERTED.name());
    }

    private void writeStatus(Reservation reservation, ReservationStatus newStatus, String action)
            throws SQLException {
        ReservationStatus oldStatus = reservation.getStatus();
        try (Connection connection = Db.get()) {
            connection.setAutoCommit(false);
            try {
                reservationDao.updateStatus(connection, reservation.getId(), newStatus);
                auditService.record(connection, "reservation", reservation.getId(), action,
                    oldStatus.name(), newStatus.name());
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
        reservation.setStatus(newStatus);
    }

    private Reservation requireReservation(int reservationId) throws SQLException {
        Reservation reservation = reservationDao.findById(reservationId);
        if (reservation == null) {
            throw new IllegalArgumentException("No reservation with id " + reservationId + ".");
        }
        return reservation;
    }

    private void requireLegalTransition(ReservationStatus from, ReservationStatus to)
            throws InvalidTransitionException {
        if (!isLegalTransition(from, to)) {
            throw new InvalidTransitionException(
                "Cannot move a reservation from " + from + " to " + to + ".");
        }
    }

    // The only state ever left is ACTIVE: it converts, is cancelled, or
    // lapses. CONVERTED, LAPSED and CANCELLED are terminal.
    private boolean isLegalTransition(ReservationStatus from, ReservationStatus to) {
        if (from != ReservationStatus.ACTIVE) {
            return false;
        }
        return to == ReservationStatus.CONVERTED || to == ReservationStatus.CANCELLED
            || to == ReservationStatus.LAPSED;
    }

    // A distinct, checked type so a controller can tell "you asked for a
    // transition the state machine does not allow" apart from a database
    // failure, and show the right message for each (CONVENTIONS.md, Errors).
    public static class InvalidTransitionException extends Exception {

        public InvalidTransitionException(String message) {
            super(message);
        }
    }

    public static class PropertyNotAvailableException extends Exception {

        public PropertyNotAvailableException(String message) {
            super(message);
        }
    }

    // Two clients reserving at once will beat a check-then-write. This is
    // thrown both for the readable pre-check and for the unique index
    // backstop (SQL Server error 2601/2627) that catches the race.
    public static class DuplicateReservationException extends Exception {

        public DuplicateReservationException(String message) {
            super(message);
        }
    }

    public static class CannotReserveOwnPropertyException extends Exception {

        public CannotReserveOwnPropertyException(String message) {
            super(message);
        }
    }
}
