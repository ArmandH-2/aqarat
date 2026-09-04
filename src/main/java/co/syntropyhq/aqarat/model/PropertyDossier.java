package co.syntropyhq.aqarat.model;

import java.util.List;
import java.util.Map;

/**
 * Everything the system knows about one property, gathered for the dossier
 * screen.
 *
 * <p>This is a read projection and nothing more. It is assembled per request
 * and thrown away; there is no dossier table, because every row in here
 * already keys on {@code property_id} somewhere else. Storing a copy would
 * mean maintaining a second version of facts that already have an owner, and
 * it would be wrong the moment anything changed.
 *
 * <p>{@code userNames} resolves every person id appearing anywhere in the
 * bundle, so the screen can name an owner, a client or an agent without going
 * back to the database for each row.
 */
public class PropertyDossier {

    private final Property property;
    private final List<PropertyDocument> documents;
    private final int photoCount;
    private final List<Valuation> valuations;
    private final List<Viewing> viewings;
    private final List<Reservation> reservations;
    private final List<Contract> contracts;
    private final List<AuditLog> timeline;
    private final Map<Integer, String> userNames;

    public PropertyDossier(Property property, List<PropertyDocument> documents, int photoCount,
            List<Valuation> valuations, List<Viewing> viewings, List<Reservation> reservations,
            List<Contract> contracts, List<AuditLog> timeline, Map<Integer, String> userNames) {
        this.property = property;
        this.documents = documents;
        this.photoCount = photoCount;
        this.valuations = valuations;
        this.viewings = viewings;
        this.reservations = reservations;
        this.contracts = contracts;
        this.timeline = timeline;
        this.userNames = userNames;
    }

    /** A person's name, or a readable placeholder rather than a bare id. */
    public String nameOf(Integer userId) {
        if (userId == null) {
            return "Unassigned";
        }
        return userNames.getOrDefault(userId, "User " + userId);
    }

    public Property getProperty() {
        return property;
    }

    public List<PropertyDocument> getDocuments() {
        return documents;
    }

    public int getPhotoCount() {
        return photoCount;
    }

    public List<Valuation> getValuations() {
        return valuations;
    }

    public List<Viewing> getViewings() {
        return viewings;
    }

    public List<Reservation> getReservations() {
        return reservations;
    }

    public List<Contract> getContracts() {
        return contracts;
    }

    public List<AuditLog> getTimeline() {
        return timeline;
    }

    public Map<Integer, String> getUserNames() {
        return userNames;
    }
}
