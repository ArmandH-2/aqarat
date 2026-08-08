package co.syntropyhq.aqarat;

import co.syntropyhq.aqarat.dao.AuditDao;
import co.syntropyhq.aqarat.dao.ContractDao;
import co.syntropyhq.aqarat.dao.DistrictDao;
import co.syntropyhq.aqarat.dao.PaymentDao;
import co.syntropyhq.aqarat.dao.PaymentScheduleDao;
import co.syntropyhq.aqarat.dao.PropertyDao;
import co.syntropyhq.aqarat.dao.PropertyMessageDao;
import co.syntropyhq.aqarat.dao.PropertyPhotoDao;
import co.syntropyhq.aqarat.dao.PropertySearch;
import co.syntropyhq.aqarat.dao.PropertyTypeDao;
import co.syntropyhq.aqarat.dao.ReportDao;
import co.syntropyhq.aqarat.dao.ReservationDao;
import co.syntropyhq.aqarat.dao.SystemSettingDao;
import co.syntropyhq.aqarat.dao.UserDao;
import co.syntropyhq.aqarat.dao.ValuationDao;
import co.syntropyhq.aqarat.dao.ViewingDao;
import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.Contract;
import co.syntropyhq.aqarat.model.ContractType;
import co.syntropyhq.aqarat.model.DealType;
import co.syntropyhq.aqarat.model.District;
import co.syntropyhq.aqarat.model.NewPhoto;
import co.syntropyhq.aqarat.model.PaymentFrequency;
import co.syntropyhq.aqarat.model.PaymentMethod;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.PropertyStatus;
import co.syntropyhq.aqarat.model.PropertyType;
import co.syntropyhq.aqarat.model.Role;
import co.syntropyhq.aqarat.service.AuditService;
import co.syntropyhq.aqarat.service.AuthService;
import co.syntropyhq.aqarat.service.ContractService;
import co.syntropyhq.aqarat.service.PaymentService;
import co.syntropyhq.aqarat.service.PropertyService;
import co.syntropyhq.aqarat.service.ReportService;
import co.syntropyhq.aqarat.service.ReservationService;
import co.syntropyhq.aqarat.service.ValuationService;
import co.syntropyhq.aqarat.service.ViewingService;
import co.syntropyhq.aqarat.util.Db;
import co.syntropyhq.aqarat.util.SessionManager;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RoleWorkflowTest {

    private static final String OWNER_EMAIL = "verify-owner@test.local";
    private static final String CLIENT_EMAIL = "verify-client@test.local";

    private AuthService auth;
    private PropertyService propertyService;
    private PropertyDao propertyDao;
    private ReservationService reservationService;
    private ViewingService viewingService;
    private ContractService contractService;
    private PaymentService paymentService;
    private ReportService reportService;

    @BeforeEach
    void before() throws Exception {
        UserDao userDao = new UserDao();
        auth = new AuthService(userDao);
        propertyDao = new PropertyDao();
        PropertyPhotoDao propertyPhotoDao = new PropertyPhotoDao();
        propertyService = new PropertyService(propertyDao, propertyPhotoDao,
            new PropertyMessageDao(), new AuditService(new AuditDao()));
        reservationService = new ReservationService(new ReservationDao(), new SystemSettingDao(),
            propertyService, new AuditService(new AuditDao()));
        viewingService = new ViewingService(new ViewingDao(), propertyService,
            new AuditService(new AuditDao()));
        paymentService = new PaymentService(new PaymentScheduleDao(), new PaymentDao(),
            new ContractDao(), new ReservationDao(), new SystemSettingDao(),
            new AuditService(new AuditDao()));
        contractService = new ContractService(new ContractDao(), new ReservationDao(),
            propertyService, reservationService, new SystemSettingDao(), new AuditService(new AuditDao()),
            paymentService);
        reportService = new ReportService(new ReportDao(), new SystemSettingDao());
    }

    @AfterEach
    void after() throws Exception {
        SessionManager.logout();
        try (Connection conn = Db.get()) {
            // FK-safe order for the smoke users: children first, app_user
            // last. The generated SQL strings below are kept short because
            // the table names here are long enough that inline SQL strings
            // would hurt to read - they're still the app_user / verify- queries.
            String u = "SELECT id FROM app_user WHERE email LIKE 'verify-%'";
            String myProperty = "SELECT id FROM property WHERE owner_id IN (" + u + ")";
            String myContract = "SELECT id FROM contract WHERE property_id IN (" + myProperty
                + ") OR client_id IN (" + u + ") OR agent_id IN (" + u + ")";
            String[][] deletes = {
                {"audit_log", "user_id IN (" + u + ")"},
                {"payment", "declared_by IN (" + u + ") OR confirmed_by IN (" + u + ")"},
                {"payment_schedule", "contract_id IN (" + myContract + ")"},
                {"contract", "property_id IN (" + myProperty + ") OR client_id IN (" + u
                    + ") OR agent_id IN (" + u + ")"},
                {"reservation", "property_id IN (" + myProperty + ") OR client_id IN (" + u + ")"},
                {"viewing", "property_id IN (" + myProperty + ") OR client_id IN (" + u + ")"},
                {"property_message", "property_id IN (" + myProperty + ") OR author_id IN (" + u + ")"},
                {"property_photo", "property_id IN (" + myProperty + ")"},
                {"property", "id IN (" + myProperty + ")"},
                {"app_user", "email LIKE 'verify-%'"},
            };
            for (String[] delete : deletes) {
                conn.prepareStatement("DELETE FROM " + delete[0] + " WHERE " + delete[1])
                    .executeUpdate();
            }
        }
    }

    private AppUser registerOrFind(String email, String password, String fullName, String phone)
            throws Exception {
        AppUser user = auth.register(email, password, fullName, phone);
        return user == null ? auth.login(email, password) : user;
    }

    private Property referenceProperty() throws Exception {
        List<Property> any = propertyService.searchPublished(new PropertySearch(), 0, 1);
        return any.get(0);
    }

    // The whole pipeline from the owner submitting to the agent closing,
    // with each role's action checked at the service boundary. The seed
    // database must exist - this is not a unit test.
    @Test
    void ownerToCloseHappyPath() throws Exception {
        AppUser owner = registerOrFind(OWNER_EMAIL, "Passw0rd!", "Smoke Owner", "+9617000001");
        AppUser client = registerOrFind(CLIENT_EMAIL, "Passw0rd!", "Smoke Client", "+9617000002");
        AppUser agent = auth.login("rami@aqarat.local", "Password123!");

        Property base = referenceProperty();
        SessionManager.login(owner);
        Property p = new Property();
        p.setOwnerId(owner.getId());
        p.setTitle("Verify smoke " + System.currentTimeMillis());
        p.setDistrictId(base.getDistrictId());
        p.setPropertyTypeId(base.getPropertyTypeId());
        p.setAreaSqm(new BigDecimal("120"));
        p.setBedrooms(2);
        p.setBathrooms(1);
        p.setDealType(DealType.SALE);
        p.setAskingPrice(new BigDecimal("250000"));
        int propertyId = propertyService.submit(p);
        assertEquals(PropertyStatus.PENDING_REVIEW, propertyDao.findById(propertyId).getStatus());

        SessionManager.login(agent);
        propertyService.claim(propertyId, agent.getId());
        propertyService.review(propertyId, PropertyStatus.NEEDS_INFO, "Upload the title deed.");
        assertEquals(1, propertyService.findMessages(propertyId).size());

        SessionManager.login(owner);
        propertyService.respondToReview(propertyId, "Deed uploaded.", owner.getId());
        assertEquals(PropertyStatus.PENDING_REVIEW, propertyDao.findById(propertyId).getStatus());

        SessionManager.login(agent);
        propertyService.review(propertyId, PropertyStatus.AVAILABLE, null);
        assertEquals(PropertyStatus.AVAILABLE, propertyDao.findById(propertyId).getStatus());

        SessionManager.login(client);
        int viewingId = viewingService.request(propertyId, client.getId(),
            LocalDateTime.now().plusDays(1).withNano(0));
        assertTrue(viewingId > 0);
        int reservationId = reservationService.create(propertyId, client.getId(),
            new BigDecimal("5000"));
        assertEquals(PropertyStatus.RESERVED, propertyDao.findById(propertyId).getStatus());

        Contract contract = new Contract();
        contract.setPropertyId(propertyId);
        contract.setClientId(client.getId());
        contract.setAgentId(agent.getId());
        contract.setContractType(ContractType.SALE);
        contract.setTotalAmount(new BigDecimal("250000"));
        contract.setPaymentFrequency(PaymentFrequency.MONTHLY);
        contract.setInstallmentCount(4);
        contract.setStartDate(LocalDate.now());
        int contractId = contractService.draft(contract);
        assertTrue(contractId > 0);

        contractService.activate(contractId);
        assertEquals(PropertyStatus.UNDER_CONTRACT, propertyDao.findById(propertyId).getStatus());

        var schedule = paymentService.findScheduleByContract(contractId);
        assertFalse(schedule.isEmpty());
        int paymentId = paymentService.declare(schedule.get(0).getId(), null,
            new BigDecimal("1000"), PaymentMethod.BANK_TRANSFER, "ref-1", null, client.getId());
        assertTrue(paymentId > 0);

        SessionManager.login(agent);
        paymentService.confirm(paymentId, agent.getId());
        contractService.close(contractId);
        assertEquals(PropertyStatus.CLOSED, propertyDao.findById(propertyId).getStatus());
    }

    @Test
    void privilegesAreEnforced() throws Exception {
        AppUser owner = registerOrFind(OWNER_EMAIL, "Passw0rd!", "Smoke Owner", "+9617000001");
        AppUser other = registerOrFind(CLIENT_EMAIL, "Passw0rd!", "Smoke Other", "+9617000002");
        AppUser agent = auth.login("rami@aqarat.local", "Password123!");

        Property base = referenceProperty();
        SessionManager.login(owner);
        Property p = new Property();
        p.setOwnerId(owner.getId());
        p.setTitle("Priv check " + System.currentTimeMillis());
        p.setDistrictId(base.getDistrictId());
        p.setPropertyTypeId(base.getPropertyTypeId());
        p.setAreaSqm(new BigDecimal("100"));
        p.setBedrooms(1);
        p.setDealType(DealType.SALE);
        p.setAskingPrice(new BigDecimal("150000"));
        int propertyId = propertyService.submit(p);

        SessionManager.login(other);
        assertThrows(IllegalArgumentException.class,
            () -> propertyService.respondToReview(propertyId, "nope", other.getId()));

        SessionManager.login(owner);
        assertThrows(ReservationService.CannotReserveOwnPropertyException.class,
            () -> reservationService.create(propertyId, owner.getId(), BigDecimal.TEN));
        assertThrows(ViewingService.CannotRequestOwnPropertyException.class,
            () -> viewingService.request(propertyId, owner.getId(), LocalDateTime.now().plusDays(1)));

        Contract selfContract = new Contract();
        selfContract.setPropertyId(propertyId);
        selfContract.setClientId(owner.getId());
        selfContract.setAgentId(agent.getId());
        selfContract.setContractType(ContractType.SALE);
        selfContract.setStartDate(LocalDate.now());
        assertThrows(ContractService.DraftRefusedException.class,
            () -> contractService.draft(selfContract));

        List<?> adminView = reportService.overduePayments(null);
        List<?> agentView = reportService.overduePayments(2);
        assertTrue(agentView.size() <= adminView.size());
    }
}
