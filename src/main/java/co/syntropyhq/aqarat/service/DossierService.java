package co.syntropyhq.aqarat.service;

import co.syntropyhq.aqarat.dao.AuditDao;
import co.syntropyhq.aqarat.dao.ContractDao;
import co.syntropyhq.aqarat.dao.PropertyDao;
import co.syntropyhq.aqarat.dao.PropertyDocumentDao;
import co.syntropyhq.aqarat.dao.PropertyPhotoDao;
import co.syntropyhq.aqarat.dao.ReservationDao;
import co.syntropyhq.aqarat.dao.UserDao;
import co.syntropyhq.aqarat.dao.ValuationDao;
import co.syntropyhq.aqarat.dao.ViewingDao;
import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.AuditLog;
import co.syntropyhq.aqarat.model.Contract;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.PropertyDocument;
import co.syntropyhq.aqarat.model.PropertyDossier;
import co.syntropyhq.aqarat.model.Reservation;
import co.syntropyhq.aqarat.model.Role;
import co.syntropyhq.aqarat.model.Viewing;
import co.syntropyhq.aqarat.util.Db;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles the dossier: one property's whole file, in one place.
 *
 * <p>Read-only, and staff-only. It creates nothing and changes nothing, which
 * is why it is allowed to reach several DAOs directly rather than through the
 * service that owns each one - there is no rule to apply and no transaction
 * to protect, only rows to fetch.
 *
 * <p>Everything is loaded on one connection. The alternative, letting each
 * find method open its own, would take a dozen connections from the pool to
 * paint a single screen.
 */
public class DossierService {

    private final PropertyDao propertyDao;
    private final PropertyDocumentDao propertyDocumentDao;
    private final PropertyPhotoDao propertyPhotoDao;
    private final ValuationDao valuationDao;
    private final ViewingDao viewingDao;
    private final ReservationDao reservationDao;
    private final ContractDao contractDao;
    private final AuditDao auditDao;
    private final UserDao userDao;

    public DossierService(PropertyDao propertyDao, PropertyDocumentDao propertyDocumentDao,
            PropertyPhotoDao propertyPhotoDao, ValuationDao valuationDao, ViewingDao viewingDao,
            ReservationDao reservationDao, ContractDao contractDao, AuditDao auditDao,
            UserDao userDao) {
        this.propertyDao = propertyDao;
        this.propertyDocumentDao = propertyDocumentDao;
        this.propertyPhotoDao = propertyPhotoDao;
        this.valuationDao = valuationDao;
        this.viewingDao = viewingDao;
        this.reservationDao = reservationDao;
        this.contractDao = contractDao;
        this.auditDao = auditDao;
        this.userDao = userDao;
    }

    /**
     * The whole file on one property.
     *
     * <p>Staff only. The dossier names the people who asked to view a property
     * and who reserved it, so showing it to a customer - the owner included -
     * would hand one customer another customer's identity. The owner sees
     * their own submission, their own documents and their own review thread on
     * their own screens; they do not see who has been looking at their listing.
     */
    public PropertyDossier load(int propertyId, AppUser viewer)
            throws SQLException, NotPermittedException {
        requireStaff(viewer);
        try (Connection connection = Db.get()) {
            Property property = propertyDao.findById(connection, propertyId);
            if (property == null) {
                throw new IllegalArgumentException("No property with id " + propertyId + ".");
            }
            List<PropertyDocument> documents =
                propertyDocumentDao.findByProperty(connection, propertyId);
            int photoCount = propertyPhotoDao.findByProperty(connection, propertyId).size();
            List<Viewing> viewings = viewingDao.findByProperty(connection, propertyId);
            List<Reservation> reservations = reservationDao.findByProperty(connection, propertyId);
            List<Contract> contracts = contractDao.findByProperty(connection, propertyId);
            List<AuditLog> timeline =
                buildTimeline(connection, propertyId, documents, viewings, reservations, contracts);
            Map<Integer, String> names = resolveNames(
                connection, property, documents, viewings, reservations, contracts, timeline);
            return new PropertyDossier(property, documents, photoCount,
                valuationDao.findAllByProperty(connection, propertyId), viewings, reservations,
                contracts, timeline, names);
        }
    }

    /**
     * The audit trail for the property and for everything hanging off it.
     *
     * <p>audit_log keys on (entity_type, entity_id), so asking only for
     * "property" would show a third of the story - the contract activation,
     * the viewing outcomes and the document verifications all file themselves
     * under their own type. Newest first, which is how a timeline is read.
     */
    private List<AuditLog> buildTimeline(Connection connection, int propertyId,
            List<PropertyDocument> documents, List<Viewing> viewings,
            List<Reservation> reservations, List<Contract> contracts) throws SQLException {
        List<AuditLog> timeline =
            new ArrayList<>(auditDao.findByEntity(connection, "property", propertyId));
        timeline.addAll(auditDao.findByEntities(connection, "property_document",
            documents.stream().map(PropertyDocument::getId).toList()));
        timeline.addAll(auditDao.findByEntities(connection, "viewing",
            viewings.stream().map(Viewing::getId).toList()));
        timeline.addAll(auditDao.findByEntities(connection, "reservation",
            reservations.stream().map(Reservation::getId).toList()));
        timeline.addAll(auditDao.findByEntities(connection, "contract",
            contracts.stream().map(Contract::getId).toList()));
        timeline.sort(Comparator.comparing(AuditLog::getCreatedAt)
            .thenComparingLong(AuditLog::getId).reversed());
        return timeline;
    }

    // Every person id anywhere in the bundle, looked up once each. The rows
    // carry ids because that is what the other screens need; only this one has
    // to put a name beside every one of them.
    private Map<Integer, String> resolveNames(Connection connection, Property property,
            List<PropertyDocument> documents, List<Viewing> viewings,
            List<Reservation> reservations, List<Contract> contracts, List<AuditLog> timeline)
            throws SQLException {
        List<Integer> ids = new ArrayList<>();
        ids.add(property.getOwnerId());
        ids.add(property.getAgentId());
        for (PropertyDocument document : documents) {
            ids.add(document.getUploadedBy());
            ids.add(document.getVerifiedBy());
        }
        for (Viewing viewing : viewings) {
            ids.add(viewing.getClientId());
            ids.add(viewing.getAgentId());
        }
        for (Reservation reservation : reservations) {
            ids.add(reservation.getClientId());
            ids.add(reservation.getAgentId());
        }
        for (Contract contract : contracts) {
            ids.add(contract.getClientId());
            ids.add(contract.getAgentId());
        }
        for (AuditLog entry : timeline) {
            ids.add(entry.getUserId());
        }

        Map<Integer, String> names = new LinkedHashMap<>();
        for (Integer id : ids) {
            if (id == null || names.containsKey(id)) {
                continue;
            }
            AppUser user = userDao.findById(connection, id);
            names.put(id, user == null ? "Deleted user" : user.getFullName());
        }
        return names;
    }

    private void requireStaff(AppUser viewer) throws NotPermittedException {
        if (viewer == null || viewer.getRole() == Role.CUSTOMER) {
            throw new NotPermittedException("The property file is available to Aqarat staff.");
        }
    }

    // A refusal to show something, distinct from a database failure so a
    // controller can show the reason rather than a generic error
    // (CONVENTIONS.md, Errors).
    public static class NotPermittedException extends Exception {

        public NotPermittedException(String message) {
            super(message);
        }
    }
}
