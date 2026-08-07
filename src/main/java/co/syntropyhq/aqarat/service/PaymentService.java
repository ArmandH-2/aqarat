package co.syntropyhq.aqarat.service;

import co.syntropyhq.aqarat.dao.ContractDao;
import co.syntropyhq.aqarat.dao.PaymentDao;
import co.syntropyhq.aqarat.dao.PaymentScheduleDao;
import co.syntropyhq.aqarat.dao.ReservationDao;
import co.syntropyhq.aqarat.dao.SystemSettingDao;
import co.syntropyhq.aqarat.model.Contract;
import co.syntropyhq.aqarat.model.Payment;
import co.syntropyhq.aqarat.model.PaymentFrequency;
import co.syntropyhq.aqarat.model.PaymentMethod;
import co.syntropyhq.aqarat.model.PaymentSchedule;
import co.syntropyhq.aqarat.model.PaymentStatus;
import co.syntropyhq.aqarat.model.Reservation;
import co.syntropyhq.aqarat.model.ScheduleStatus;
import co.syntropyhq.aqarat.model.SystemSetting;
import co.syntropyhq.aqarat.util.Db;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

// Implements ContractService's ScheduleGenerator seam directly, so
// ContractsController can wire "new PaymentService(...)" straight in as the
// last constructor argument without any adapter.
public class PaymentService implements ContractService.ScheduleGenerator {

    private final PaymentScheduleDao paymentScheduleDao;
    private final PaymentDao paymentDao;
    private final ContractDao contractDao;
    private final ReservationDao reservationDao;
    private final SystemSettingDao systemSettingDao;
    private final AuditService auditService;

    public PaymentService(PaymentScheduleDao paymentScheduleDao, PaymentDao paymentDao,
            ContractDao contractDao, ReservationDao reservationDao,
            SystemSettingDao systemSettingDao, AuditService auditService) {
        this.paymentScheduleDao = paymentScheduleDao;
        this.paymentDao = paymentDao;
        this.contractDao = contractDao;
        this.reservationDao = reservationDao;
        this.systemSettingDao = systemSettingDao;
        this.auditService = auditService;
    }

    @Override
    public void generate(Connection connection, Contract activatedContract) throws SQLException {
        List<PaymentSchedule> schedule = buildSchedule(activatedContract.getPaymentFrequency(),
            activatedContract.getInstallmentCount(), activatedContract.getTotalAmount(),
            activatedContract.getStartDate());
        for (PaymentSchedule row : schedule) {
            row.setContractId(activatedContract.getId());
        }
        paymentScheduleDao.insertAll(connection, schedule);
        auditService.record(connection, "contract", activatedContract.getId(), "SCHEDULE_GENERATED",
            null, schedule.size() + " installment(s)");
    }

    /**
     * Pure arithmetic, no database - the part CLAUDE.md requires tested.
     * ONE_OFF is always a single row for the full amount. Every other
     * frequency produces installmentCount rows, spaced by 1, 3 or 12 months.
     * The rounding remainder lands on the last row so the schedule always
     * sums exactly to totalAmount (CLAUDE.md, Tests).
     */
    public List<PaymentSchedule> buildSchedule(PaymentFrequency frequency, int installmentCount,
            BigDecimal totalAmount, LocalDate startDate) {
        int count = frequency == PaymentFrequency.ONE_OFF ? 1 : installmentCount;
        int stepMonths = stepMonths(frequency);
        BigDecimal[] amounts = splitEvenly(totalAmount, count);
        List<PaymentSchedule> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(scheduleRow(i + 1, startDate.plusMonths((long) stepMonths * i), amounts[i]));
        }
        return rows;
    }

    private PaymentSchedule scheduleRow(int installmentNo, LocalDate dueDate, BigDecimal amountDue) {
        PaymentSchedule row = new PaymentSchedule();
        row.setInstallmentNo(installmentNo);
        row.setDueDate(dueDate);
        row.setAmountDue(amountDue);
        row.setAmountPaid(BigDecimal.ZERO.setScale(2));
        row.setStatus(ScheduleStatus.PENDING);
        return row;
    }

    private int stepMonths(PaymentFrequency frequency) {
        if (frequency == PaymentFrequency.QUARTERLY) {
            return 3;
        }
        if (frequency == PaymentFrequency.ANNUAL) {
            return 12;
        }
        return 1;
    }

    // CLAUDE.md: "1000 over 3 is 333.33 + 333.33 + 333.34 - never three
    // 333.33s losing a penny." Every row but the last takes the floor of the
    // even share; the last takes whatever is left, so the sum is exact.
    private BigDecimal[] splitEvenly(BigDecimal totalAmount, int count) {
        BigDecimal share = totalAmount.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        BigDecimal[] amounts = new BigDecimal[count];
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < count - 1; i++) {
            amounts[i] = share;
            allocated = allocated.add(share);
        }
        amounts[count - 1] = totalAmount.subtract(allocated);
        return amounts;
    }

    /**
     * DESIGN.md section 6: overdue is evaluated on read, not by a background
     * job - there is no scheduler in this system. This read is the "next
     * time the row is touched" that persists an overdue row's status.
     */
    public List<PaymentSchedule> findScheduleByContract(int contractId) throws SQLException {
        List<PaymentSchedule> schedule;
        try (Connection connection = Db.get()) {
            schedule = paymentScheduleDao.findByContract(connection, contractId);
        }
        int graceDays = paymentGraceDays();
        for (PaymentSchedule row : schedule) {
            markOverdueIfNeeded(row, graceDays);
        }
        return schedule;
    }

    private void markOverdueIfNeeded(PaymentSchedule row, int graceDays) throws SQLException {
        if (row.getStatus() == ScheduleStatus.PAID || row.getStatus() == ScheduleStatus.OVERDUE) {
            return;
        }
        LocalDate overdueSince = row.getDueDate().plusDays(graceDays);
        boolean pastGrace = !LocalDate.now().isBefore(overdueSince);
        boolean stillOwed = row.getAmountPaid().compareTo(row.getAmountDue()) < 0;
        if (pastGrace && stillOwed) {
            writeScheduleStatus(row, ScheduleStatus.OVERDUE);
        }
    }

    // A status-only write, but it still touches two tables with the audit
    // entry, so it gets the canonical transaction shape (CLAUDE.md).
    private void writeScheduleStatus(PaymentSchedule row, ScheduleStatus newStatus) throws SQLException {
        ScheduleStatus oldStatus = row.getStatus();
        try (Connection connection = Db.get()) {
            connection.setAutoCommit(false);
            try {
                paymentScheduleDao.updateStatus(connection, row.getId(), newStatus);
                auditService.record(connection, "payment_schedule", row.getId(), "STATUS_CHANGE",
                    oldStatus.name(), newStatus.name());
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
        row.setStatus(newStatus);
    }

    // payment_grace_days lives in system_setting, never hardcoded. A missing
    // key fails loudly and names the key (matches ReservationService).
    private int paymentGraceDays() throws SQLException {
        SystemSetting setting = systemSettingDao.findByKey("payment_grace_days");
        if (setting == null) {
            throw new IllegalStateException("Missing system setting: payment_grace_days");
        }
        return Integer.parseInt(setting.getValue());
    }

    public PaymentSchedule findScheduleById(int scheduleId) throws SQLException {
        return paymentScheduleDao.findById(scheduleId);
    }

    public List<Payment> findPaymentsByContract(int contractId) throws SQLException {
        try (Connection connection = Db.get()) {
            return paymentDao.findByContract(connection, contractId);
        }
    }

    // agentId scopes the queue to one agent's own contracts and reservations;
    // null (admin) shows the whole agency's. Bound into the query, not
    // filtered afterwards (DESIGN.md section 8).
    public List<Payment> findDeclaredAwaitingConfirmation(Integer agentId) throws SQLException {
        try (Connection connection = Db.get()) {
            return paymentDao.findDeclared(connection, agentId);
        }
    }

    /**
     * A client declares a payment with proof, against an installment or a
     * reservation deposit - never both, never neither (ck_payment_target).
     * The target must be the client's own: the installment's contract or the
     * reservation has to carry this user's id, so a client can only ever
     * declare against what they owe. The amount is capped at what is left
     * to pay on that target. All of this is checked here so the database is
     * never asked to accept it.
     */
    public int declare(Integer scheduleId, Integer reservationId, BigDecimal amount,
            PaymentMethod method, String reference, String proofPath, int declaredByUserId)
            throws SQLException, InvalidPaymentTargetException, InvalidPaymentAmountException {
        requireExactlyOneTarget(scheduleId, reservationId);
        requirePositiveAmount(amount);
        try (Connection connection = Db.get()) {
            connection.setAutoCommit(false);
            try {
                requireTargetBelongsTo(connection, scheduleId, reservationId, declaredByUserId);
                requireAmountWithinOutstanding(connection, scheduleId, reservationId, amount);
                Payment payment = newPayment(scheduleId, reservationId, amount, method, reference,
                    proofPath, declaredByUserId, PaymentStatus.DECLARED);
                int id = paymentDao.insert(connection, payment);
                auditService.record(connection, "payment", id, "DECLARE", null,
                    PaymentStatus.DECLARED.name());
                connection.commit();
                return id;
            } catch (SQLException | InvalidPaymentTargetException | InvalidPaymentAmountException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /**
     * An agent recording a payment directly - as opposed to confirming one a
     * client declared - writes it already confirmed, in one transaction:
     * there is no point persisting DECLARED only to flip it a moment later.
     * The amount is still capped at what is left on the target, because
     * recording more than is owed is a data-entry error in either direction.
     */
    public int recordConfirmedPayment(Integer scheduleId, Integer reservationId, BigDecimal amount,
            PaymentMethod method, String reference, String proofPath, int agentUserId)
            throws SQLException, InvalidPaymentTargetException, InvalidPaymentAmountException {
        requireExactlyOneTarget(scheduleId, reservationId);
        requirePositiveAmount(amount);
        try (Connection connection = Db.get()) {
            connection.setAutoCommit(false);
            try {
                requireAmountWithinOutstanding(connection, scheduleId, reservationId, amount);
                Payment payment = newPayment(scheduleId, reservationId, amount, method, reference,
                    proofPath, agentUserId, PaymentStatus.CONFIRMED);
                payment.setConfirmedBy(agentUserId);
                int id = paymentDao.insert(connection, payment);
                auditService.record(connection, "payment", id, "RECORD_CONFIRMED", null,
                    PaymentStatus.CONFIRMED.name());
                if (scheduleId != null) {
                    applyToSchedule(connection, scheduleId, amount);
                }
                connection.commit();
                return id;
            } catch (SQLException | InvalidPaymentAmountException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private Payment newPayment(Integer scheduleId, Integer reservationId, BigDecimal amount,
            PaymentMethod method, String reference, String proofPath, int declaredBy,
            PaymentStatus status) {
        Payment payment = new Payment();
        payment.setScheduleId(scheduleId);
        payment.setReservationId(reservationId);
        payment.setAmount(amount);
        payment.setPaidAt(LocalDateTime.now(ZoneOffset.UTC));
        payment.setMethod(method);
        payment.setReference(reference);
        payment.setProofPath(proofPath);
        payment.setDeclaredBy(declaredBy);
        payment.setStatus(status);
        return payment;
    }

    private void requireExactlyOneTarget(Integer scheduleId, Integer reservationId)
            throws InvalidPaymentTargetException {
        boolean hasSchedule = scheduleId != null;
        boolean hasReservation = reservationId != null;
        if (hasSchedule == hasReservation) {
            throw new InvalidPaymentTargetException(
                "A payment must settle either an installment or a reservation deposit.");
        }
    }

    private void requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("The amount must be greater than zero.");
        }
    }

    // The target's owner is resolved from the database, never trusted from
    // the caller - the declared-by id is checked against the row it names.
    private void requireTargetBelongsTo(Connection connection, Integer scheduleId,
            Integer reservationId, int userId) throws SQLException, InvalidPaymentTargetException {
        if (scheduleId != null) {
            PaymentSchedule schedule = paymentScheduleDao.findById(connection, scheduleId);
            if (schedule == null) {
                throw new InvalidPaymentTargetException("No installment with that id.");
            }
            Contract contract = contractDao.findById(connection, schedule.getContractId());
            if (contract == null || contract.getClientId() != userId) {
                throw new InvalidPaymentTargetException(
                    "You can only declare a payment against your own contract.");
            }
            return;
        }
        Reservation reservation = reservationDao.findById(connection, reservationId);
        if (reservation == null || reservation.getClientId() != userId) {
            throw new InvalidPaymentTargetException(
                "You can only declare a payment against your own reservation.");
        }
    }

    // What is left on an installment is amount_due minus amount_paid; what is
    // left on a reservation is the whole deposit, which is only ever settled
    // in full (no partial-tracking column exists for it).
    private void requireAmountWithinOutstanding(Connection connection, Integer scheduleId,
            Integer reservationId, BigDecimal amount)
            throws SQLException, InvalidPaymentAmountException {
        BigDecimal outstanding;
        if (scheduleId != null) {
            PaymentSchedule schedule = paymentScheduleDao.findById(connection, scheduleId);
            if (schedule == null) {
                throw new InvalidPaymentAmountException("No installment with that id.");
            }
            outstanding = schedule.getAmountDue().subtract(schedule.getAmountPaid());
        } else {
            Reservation reservation = reservationDao.findById(connection, reservationId);
            if (reservation == null) {
                throw new InvalidPaymentAmountException("No reservation with that id.");
            }
            outstanding = reservation.getDepositAmount();
        }
        if (amount.compareTo(outstanding) > 0) {
            throw new InvalidPaymentAmountException(
                "This amount exceeds what is left to pay on the target.");
        }
    }

    /**
     * An agent confirms a payment a client declared. When it settles an
     * installment, the schedule row's amount_paid and status move together
     * with the payment and the audit entry - one transaction (CLAUDE.md,
     * canonical shape), so a confirmed payment can never be left off the
     * schedule it was meant to pay down.
     */
    public void confirm(int paymentId, int confirmedByUserId)
            throws SQLException, InvalidPaymentStateException {
        Payment payment = requireDeclaredPayment(paymentId);
        try (Connection connection = Db.get()) {
            connection.setAutoCommit(false);
            try {
                paymentDao.updateStatus(connection, paymentId, PaymentStatus.CONFIRMED, confirmedByUserId);
                auditService.record(connection, "payment", paymentId, "CONFIRM",
                    PaymentStatus.DECLARED.name(), PaymentStatus.CONFIRMED.name());
                if (payment.getScheduleId() != null) {
                    applyToSchedule(connection, payment.getScheduleId(), payment.getAmount());
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    public void reject(int paymentId, int confirmedByUserId)
            throws SQLException, InvalidPaymentStateException {
        requireDeclaredPayment(paymentId);
        try (Connection connection = Db.get()) {
            connection.setAutoCommit(false);
            try {
                paymentDao.updateStatus(connection, paymentId, PaymentStatus.REJECTED, confirmedByUserId);
                auditService.record(connection, "payment", paymentId, "REJECT",
                    PaymentStatus.DECLARED.name(), PaymentStatus.REJECTED.name());
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private void applyToSchedule(Connection connection, int scheduleId, BigDecimal amount)
            throws SQLException {
        PaymentSchedule schedule = paymentScheduleDao.findById(connection, scheduleId);
        BigDecimal newAmountPaid = schedule.getAmountPaid().add(amount);
        ScheduleStatus newStatus = scheduleStatusFor(newAmountPaid, schedule.getAmountDue());
        paymentScheduleDao.updateAmountPaid(connection, scheduleId, newAmountPaid);
        paymentScheduleDao.updateStatus(connection, scheduleId, newStatus);
        auditService.record(connection, "payment_schedule", scheduleId, "PAYMENT_APPLIED",
            schedule.getStatus().name(), newStatus.name());
    }

    private ScheduleStatus scheduleStatusFor(BigDecimal amountPaid, BigDecimal amountDue) {
        if (amountPaid.compareTo(amountDue) >= 0) {
            return ScheduleStatus.PAID;
        }
        if (amountPaid.signum() > 0) {
            return ScheduleStatus.PARTIALLY_PAID;
        }
        return ScheduleStatus.PENDING;
    }

    private Payment requireDeclaredPayment(int paymentId)
            throws SQLException, InvalidPaymentStateException {
        Payment payment = paymentDao.findById(paymentId);
        if (payment == null) {
            throw new IllegalArgumentException("No payment with id " + paymentId + ".");
        }
        if (payment.getStatus() != PaymentStatus.DECLARED) {
            throw new InvalidPaymentStateException(
                "This payment has already been " + payment.getStatus() + ".");
        }
        return payment;
    }

    // Same shape as ContractService.DraftRefusedException: a business-rule
    // refusal, not a state-machine violation, with a message written to be
    // shown directly.
    public static class InvalidPaymentTargetException extends Exception {

        public InvalidPaymentTargetException(String message) {
            super(message);
        }
    }

    public static class InvalidPaymentAmountException extends Exception {

        public InvalidPaymentAmountException(String message) {
            super(message);
        }
    }

    public static class InvalidPaymentStateException extends Exception {

        public InvalidPaymentStateException(String message) {
            super(message);
        }
    }
}
