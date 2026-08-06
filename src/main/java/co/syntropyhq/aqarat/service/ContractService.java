package co.syntropyhq.aqarat.service;

import co.syntropyhq.aqarat.dao.ContractDao;
import co.syntropyhq.aqarat.dao.ReservationDao;
import co.syntropyhq.aqarat.dao.SystemSettingDao;
import co.syntropyhq.aqarat.model.Contract;
import co.syntropyhq.aqarat.model.ContractStatus;
import co.syntropyhq.aqarat.model.ContractType;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.PropertyStatus;
import co.syntropyhq.aqarat.model.Reservation;
import co.syntropyhq.aqarat.model.SystemSetting;
import co.syntropyhq.aqarat.util.Db;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

public class ContractService {

    private static final String COMMISSION_RATE_KEY = "commission_rate_percent";

    private final ContractDao contractDao;
    private final ReservationDao reservationDao;
    private final PropertyService propertyService;
    private final ReservationService reservationService;
    private final SystemSettingDao systemSettingDao;
    private final AuditService auditService;
    private final ScheduleGenerator scheduleGenerator;

    public ContractService(ContractDao contractDao, ReservationDao reservationDao,
            PropertyService propertyService, ReservationService reservationService,
            SystemSettingDao systemSettingDao, AuditService auditService,
            ScheduleGenerator scheduleGenerator) {
        this.contractDao = contractDao;
        this.reservationDao = reservationDao;
        this.propertyService = propertyService;
        this.reservationService = reservationService;
        this.systemSettingDao = systemSettingDao;
        this.auditService = auditService;
        this.scheduleGenerator = scheduleGenerator;
    }

    public Contract findById(int id) throws SQLException {
        return contractDao.findById(id);
    }

    // ContractDao also has findByProperty, which no panel needs yet - nothing
    // here filters a property's contracts on their own.
    public List<Contract> findByAgent(int agentId) throws SQLException {
        try (Connection connection = Db.get()) {
            return contractDao.findByAgent(connection, agentId);
        }
    }

    // clientId is bound into the query rather than filtered afterwards, so a
    // client can only ever be handed their own contracts (DESIGN.md section 8).
    public List<Contract> findByClient(int clientId) throws SQLException {
        try (Connection connection = Db.get()) {
            return contractDao.findByClient(connection, clientId);
        }
    }

    // Draft validation: the property must be AVAILABLE, or RESERVED by this
    // same client. end_date and total_amount are derived below, never
    // trusted from the caller, so ck_contract_dates and ck_contract_lease
    // can never be violated. commission_rate is copied from system_setting
    // here, at draft time, so a later rate change never rewrites history
    // (DESIGN.md section 6).
    public int draft(Contract contract) throws SQLException, DraftRefusedException {
        Property property = requireProperty(contract.getPropertyId());
        requireClientCanDraft(property, contract.getClientId());
        if (contract.getContractType() == ContractType.LEASE) {
            applyLeaseAmounts(contract, property);
        } else {
            applySaleAmounts(contract);
        }
        contract.setOwnerId(property.getOwnerId());
        contract.setCommissionRate(commissionRatePercent());
        contract.setStatus(ContractStatus.DRAFT);

        try (Connection connection = Db.get()) {
            connection.setAutoCommit(false);
            try {
                int id = contractDao.insert(connection, contract);
                auditService.record(connection, "contract", id, "CREATE", null,
                    ContractStatus.DRAFT.name());
                connection.commit();
                return id;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private Property requireProperty(int propertyId) throws SQLException {
        Property property = propertyService.findById(propertyId);
        if (property == null) {
            throw new IllegalArgumentException("No property with id " + propertyId + ".");
        }
        return property;
    }

    private void requireClientCanDraft(Property property, int clientId)
            throws SQLException, DraftRefusedException {
        if (property.getStatus() == PropertyStatus.AVAILABLE) {
            return;
        }
        if (property.getStatus() == PropertyStatus.RESERVED
                && activeReservationBelongsTo(property.getId(), clientId)) {
            return;
        }
        throw new DraftRefusedException("This property is not available for a new contract.");
    }

    private boolean activeReservationBelongsTo(int propertyId, int clientId) throws SQLException {
        try (Connection connection = Db.get()) {
            Reservation reservation = reservationDao.findActiveForProperty(connection, propertyId);
            return reservation != null && reservation.getClientId() == clientId;
        }
    }

    // The owner's min/max term only applies once, here, at draft time - the
    // end_date and total_amount are computed from it rather than typed
    // separately, so the two can never disagree with term_months.
    private void applyLeaseAmounts(Contract contract, Property property)
            throws DraftRefusedException {
        BigDecimal monthlyRent = contract.getMonthlyRent();
        Integer termMonths = contract.getTermMonths();
        if (monthlyRent == null || monthlyRent.signum() <= 0) {
            throw new DraftRefusedException("Enter a monthly rent greater than zero.");
        }
        if (termMonths == null || termMonths <= 0) {
            throw new DraftRefusedException("Enter a lease term in months.");
        }
        if (property.getMinTermMonths() != null && termMonths < property.getMinTermMonths()) {
            throw new DraftRefusedException(
                "The owner requires a lease of at least " + property.getMinTermMonths() + " months.");
        }
        if (property.getMaxTermMonths() != null && termMonths > property.getMaxTermMonths()) {
            throw new DraftRefusedException(
                "The owner allows a lease of at most " + property.getMaxTermMonths() + " months.");
        }
        if (contract.getStartDate() == null) {
            throw new DraftRefusedException("Choose a start date.");
        }
        contract.setEndDate(contract.getStartDate().plusMonths(termMonths));
        contract.setTotalAmount(
            monthlyRent.multiply(BigDecimal.valueOf(termMonths)).setScale(2, RoundingMode.HALF_UP));
    }

    // ck_contract_lease requires a SALE to carry none of the lease-only
    // columns, so they are cleared here unconditionally rather than trusting
    // an empty form to have left them blank.
    private void applySaleAmounts(Contract contract) throws DraftRefusedException {
        if (contract.getTotalAmount() == null || contract.getTotalAmount().signum() <= 0) {
            throw new DraftRefusedException("Enter a sale price greater than zero.");
        }
        if (contract.getStartDate() == null) {
            throw new DraftRefusedException("Choose a start date.");
        }
        contract.setMonthlyRent(null);
        contract.setEndDate(null);
        contract.setTermMonths(null);
    }

    private BigDecimal commissionRatePercent() throws SQLException {
        SystemSetting setting = systemSettingDao.findByKey(COMMISSION_RATE_KEY);
        if (setting == null) {
            throw new IllegalStateException("Missing system setting: " + COMMISSION_RATE_KEY);
        }
        return new BigDecimal(setting.getValue()).setScale(2, RoundingMode.HALF_UP);
    }

    // The one transaction from DESIGN.md section 6: the property status
    // flips, its reservation (if any) converts, commission_amount is
    // computed and stored, the schedule generator plugged in at
    // construction runs, and the audit entry is written. All commit
    // together, or none of them do.
    public void activate(int contractId) throws SQLException, InvalidTransitionException,
            PropertyService.InvalidTransitionException, ReservationService.InvalidTransitionException,
            DraftRefusedException {
        try (Connection connection = Db.get()) {
            connection.setAutoCommit(false);
            try {
                Contract contract = requireContract(connection, contractId);
                requireLegalTransition(contract.getStatus(), ContractStatus.ACTIVE);
                Reservation reservation =
                    reservationDao.findActiveForProperty(connection, contract.getPropertyId());
                movePropertyUnderContract(connection, contract, reservation);
                activateAndScheduleContract(connection, contract);
                connection.commit();
            } catch (SQLException | InvalidTransitionException
                    | PropertyService.InvalidTransitionException
                    | ReservationService.InvalidTransitionException | DraftRefusedException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    // Both paths reach UNDER_CONTRACT in one move: a property drafted against
    // directly, and one already reserved by this same client. The reservation
    // is converted through ReservationService, on this connection, so its own
    // rules about expiry still apply.
    private void movePropertyUnderContract(Connection connection, Contract contract,
            Reservation reservation) throws SQLException,
            PropertyService.InvalidTransitionException, DraftRefusedException,
            ReservationService.InvalidTransitionException {
        if (reservation != null && reservation.getClientId() != contract.getClientId()) {
            throw new DraftRefusedException("This property is reserved by a different client.");
        }
        propertyService.changeStatus(
            connection, contract.getPropertyId(), PropertyStatus.UNDER_CONTRACT);
        if (reservation != null) {
            reservationService.convert(connection, reservation.getId());
        }
    }

    // commission_amount applies commission_rate (copied at draft time) to
    // total_amount, which for a lease already holds monthly_rent * term_months
    // (DESIGN.md section 6) - one formula covers both contract types.
    private void activateAndScheduleContract(Connection connection, Contract contract)
            throws SQLException {
        BigDecimal commissionAmount = contract.getTotalAmount()
            .multiply(contract.getCommissionRate())
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        LocalDateTime activatedAt = LocalDateTime.now(ZoneOffset.UTC);
        contractDao.activate(connection, contract.getId(), commissionAmount, activatedAt);
        auditService.record(connection, "contract", contract.getId(), "ACTIVATE",
            ContractStatus.DRAFT.name(), ContractStatus.ACTIVE.name());
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setCommissionAmount(commissionAmount);
        contract.setActivatedAt(activatedAt);
        if (scheduleGenerator != null) {
            scheduleGenerator.generate(connection, contract);
        }
    }

    private Contract requireContract(Connection connection, int contractId) throws SQLException {
        Contract contract = contractDao.findById(connection, contractId);
        if (contract == null) {
            throw new IllegalArgumentException("No contract with id " + contractId + ".");
        }
        return contract;
    }

    // A sale closing takes its property off the market for good; a lease
    // closing, and an early termination of either type, return the property
    // to AVAILABLE for the next tenant or buyer (DESIGN.md section 6).
    public void close(int contractId) throws SQLException, InvalidTransitionException,
            PropertyService.InvalidTransitionException {
        changeStatus(contractId, ContractStatus.COMPLETED);
    }

    public void terminate(int contractId) throws SQLException, InvalidTransitionException,
            PropertyService.InvalidTransitionException {
        changeStatus(contractId, ContractStatus.TERMINATED);
    }

    private void changeStatus(int contractId, ContractStatus newStatus) throws SQLException,
            InvalidTransitionException, PropertyService.InvalidTransitionException {
        try (Connection connection = Db.get()) {
            connection.setAutoCommit(false);
            try {
                Contract contract = requireContract(connection, contractId);
                requireLegalTransition(contract.getStatus(), newStatus);
                PropertyStatus propertyTarget = newStatus == ContractStatus.COMPLETED
                    && contract.getContractType() == ContractType.SALE
                        ? PropertyStatus.CLOSED : PropertyStatus.AVAILABLE;
                contractDao.close(connection, contractId, newStatus, LocalDateTime.now(ZoneOffset.UTC));
                auditService.record(connection, "contract", contractId, "STATUS_CHANGE",
                    contract.getStatus().name(), newStatus.name());
                propertyService.changeStatus(connection, contract.getPropertyId(), propertyTarget);
                connection.commit();
            } catch (SQLException | InvalidTransitionException
                    | PropertyService.InvalidTransitionException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private void requireLegalTransition(ContractStatus from, ContractStatus to)
            throws InvalidTransitionException {
        if (!isLegalTransition(from, to)) {
            throw new InvalidTransitionException(
                "Cannot move a contract from " + from + " to " + to + ".");
        }
    }

    // DRAFT activates or stays a draft; ACTIVE is completed naturally or
    // terminated early. COMPLETED, TERMINATED and EXPIRED are terminal -
    // nothing in this track moves a contract out of them.
    private boolean isLegalTransition(ContractStatus from, ContractStatus to) {
        if (from == ContractStatus.DRAFT) {
            return to == ContractStatus.ACTIVE;
        }
        if (from == ContractStatus.ACTIVE) {
            return to == ContractStatus.COMPLETED || to == ContractStatus.TERMINATED;
        }
        return false;
    }

    // The seam for payment schedule generation, implemented by PaymentService
    // and wired in through the constructor. Runs inside activate()'s own
    // transaction: an implementation must use the connection passed in, and
    // never call commit() or rollback() itself.
    public interface ScheduleGenerator {
        void generate(Connection connection, Contract activatedContract) throws SQLException;
    }

    public static class InvalidTransitionException extends Exception {

        public InvalidTransitionException(String message) {
            super(message);
        }
    }

    // Everything draft() or activate() refuses on a business rule, rather
    // than a state-machine violation, comes through as this one type - the
    // message is what differs, and it is written to be shown directly.
    public static class DraftRefusedException extends Exception {

        public DraftRefusedException(String message) {
            super(message);
        }
    }
}
