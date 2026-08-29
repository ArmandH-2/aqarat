package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.dao.AuditDao;
import co.syntropyhq.aqarat.dao.ContractDao;
import co.syntropyhq.aqarat.dao.PaymentDao;
import co.syntropyhq.aqarat.dao.PaymentScheduleDao;
import co.syntropyhq.aqarat.dao.PropertyDao;
import co.syntropyhq.aqarat.dao.PropertyMessageDao;
import co.syntropyhq.aqarat.dao.PropertyPhotoDao;
import co.syntropyhq.aqarat.dao.ReservationDao;
import co.syntropyhq.aqarat.dao.SystemSettingDao;
import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.Contract;
import co.syntropyhq.aqarat.model.PaymentSchedule;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.PropertyStatus;
import co.syntropyhq.aqarat.model.Reservation;
import co.syntropyhq.aqarat.model.ReservationStatus;
import co.syntropyhq.aqarat.model.ScheduleStatus;
import co.syntropyhq.aqarat.service.AuditService;
import co.syntropyhq.aqarat.service.ContractService;
import co.syntropyhq.aqarat.service.PaymentService;
import co.syntropyhq.aqarat.service.PropertyService;
import co.syntropyhq.aqarat.service.ReservationService;
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.Panel;
import co.syntropyhq.aqarat.util.Router;
import co.syntropyhq.aqarat.util.SessionManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Portfolio — everything one customer has going on.
 *
 * <p>This replaces My properties, My contracts and My activity as separate nav
 * destinations. Those three split a single person's own business along database
 * table boundaries: properties, contracts, reservations-and-viewings. Nobody
 * thinks "I will check the reservations table"; they think "what is happening
 * with my things".
 *
 * <p>The three panels still render their own content — they hold working
 * schedule and payment-declaration logic worth keeping — and are embedded here
 * as segments. What is new is the strip on top: the reservation about to lapse,
 * the instalment already overdue, the agent waiting on an answer. Each of those
 * used to be a word inside a row of a list.
 */
public class PortfolioController {

    /** A reservation inside this window is worth interrupting someone about. */
    private static final Duration EXPIRY_WARNING = Duration.ofHours(24);

    /* A strip that grows with the data stops being a summary. Four is what fits
       one row at the narrowest supported width; the rest are counted, and the
       segments below hold the full detail either way. */
    private static final int MAX_ATTENTION_CARDS = 4;

    @FXML
    private Label accessDeniedLabel;
    @FXML
    private VBox contentBox;
    @FXML
    private Label ownerLabel;
    @FXML
    private VBox attentionSection;
    @FXML
    private FlowPane attentionBar;
    @FXML
    private Button propertiesTab;
    @FXML
    private Button contractsTab;
    @FXML
    private Button activityTab;
    @FXML
    private StackPane segmentContent;

    private final AuditService auditService = new AuditService(new AuditDao());
    private final PropertyService propertyService = new PropertyService(
        new PropertyDao(), new PropertyPhotoDao(), new PropertyMessageDao(), auditService);
    private final ReservationService reservationService = new ReservationService(
        new ReservationDao(), new SystemSettingDao(), propertyService, auditService);
    private final PaymentService paymentService = new PaymentService(
        new PaymentScheduleDao(), new PaymentDao(), new ContractDao(), new ReservationDao(),
        new SystemSettingDao(), auditService);
    private final ContractService contractService = new ContractService(
        new ContractDao(), new ReservationDao(), propertyService, reservationService,
        new SystemSettingDao(), auditService, paymentService);

    @FXML
    private void initialize() {
        AppUser user = SessionManager.getCurrentUser();
        if (user == null || !SessionManager.isCustomer()) {
            accessDeniedLabel.setText("Sign in as a customer to see your portfolio.");
            accessDeniedLabel.setVisible(true);
            accessDeniedLabel.setManaged(true);
            contentBox.setVisible(false);
            contentBox.setManaged(false);
            return;
        }

        ownerLabel.setText(user.getFullName().toUpperCase());
        renderAttention(user.getId());
        showProperties();
    }

    // --------------------------------------------------------------- segments

    @FXML
    private void showProperties() {
        selectTab(propertiesTab);
        embed(Panel.MY_PROPERTIES);
    }

    @FXML
    private void showContracts() {
        selectTab(contractsTab);
        embed(Panel.MY_CONTRACTS);
    }

    @FXML
    private void showActivity() {
        selectTab(activityTab);
        embed(Panel.MY_ACTIVITY);
    }

    @FXML
    private void handleSubmitNew() {
        Router.show(Panel.SUBMIT_PROPERTY);
    }

    private void embed(Panel panel) {
        Parent view = Router.load(panel);
        segmentContent.getChildren().setAll(view);
    }

    private void selectTab(Button active) {
        for (Button tab : List.of(propertiesTab, contractsTab, activityTab)) {
            tab.getStyleClass().remove("active");
        }
        if (!active.getStyleClass().contains("active")) {
            active.getStyleClass().add("active");
        }
    }

    // -------------------------------------------------------------- attention

    /**
     * Collects the things that are actually waiting on this person.
     *
     * <p>A failure here hides the strip rather than blocking the panel: the
     * segments below still work, and an alert about a summary a visitor did not
     * ask for would be worse than its absence.
     */
    private void renderAttention(int userId) {
        List<Attention> items = new ArrayList<>();
        try {
            items.addAll(expiringReservations(userId));
            items.addAll(overdueInstalments(userId));
            items.addAll(propertiesNeedingInfo(userId));
        } catch (SQLException | PropertyService.InvalidTransitionException e) {
            // findByClient lapses reservations that have run out, so it can fail
            // on a status transition as well as on the database.
            items.clear();
        }

        // Sharpest first: a reservation lapsing tonight outranks an instalment
        // that has been late for a month, which outranks a message.
        items.sort(Comparator.comparingInt((Attention item) -> item.rank)
            .thenComparing(item -> -item.magnitude));

        List<Region> shown = new ArrayList<>();
        for (Attention item : items.subList(0, Math.min(MAX_ATTENTION_CARDS, items.size()))) {
            shown.add(item.card);
        }
        int hidden = items.size() - shown.size();
        if (hidden > 0) {
            shown.add(overflowCard(hidden));
        }

        attentionBar.getChildren().setAll(shown);
        boolean any = !shown.isEmpty();
        attentionSection.setVisible(any);
        attentionSection.setManaged(any);
    }

    private Region overflowCard(int hidden) {
        Label count = new Label(hidden + (hidden == 1 ? " more item" : " more items"));
        count.getStyleClass().add("body-medium");

        Label detail = new Label("Open a segment below to see everything.");
        detail.getStyleClass().add("hint");
        detail.setWrapText(true);

        VBox card = new VBox(6, count, detail);
        card.getStyleClass().addAll("attention-card", "informal");
        card.setMinWidth(200);
        card.setPrefWidth(220);
        card.setMaxWidth(240);
        return card;
    }

    /** One thing waiting on the customer, with what decides its place in the row. */
    private static final class Attention {
        private final int rank;
        private final long magnitude;
        private final Region card;

        private Attention(int rank, long magnitude, Region card) {
            this.rank = rank;
            this.magnitude = magnitude;
            this.card = card;
        }
    }

    private List<Attention> expiringReservations(int userId)
            throws SQLException, PropertyService.InvalidTransitionException {
        List<Attention> cards = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        for (Reservation reservation : reservationService.findByClient(userId)) {
            if (reservation.getStatus() != ReservationStatus.ACTIVE
                || reservation.getExpiresAt() == null) {
                continue;
            }
            Duration remaining = Duration.between(now, reservation.getExpiresAt());
            if (remaining.isNegative() || remaining.compareTo(EXPIRY_WARNING) > 0) {
                continue;
            }
            long hours = Math.max(1, remaining.toHours());
            // Fewer hours left is more urgent, so the key is inverted.
            cards.add(new Attention(0, EXPIRY_WARNING.toHours() - hours, attentionCard(
                "urgent",
                "EXPIRES IN " + hours + (hours == 1 ? " HOUR" : " HOURS"),
                "Reservation on " + propertyTitle(reservation.getPropertyId()),
                "Your " + Format.salePrice(reservation.getDepositAmount())
                    + " deposit holds it until " + Format.dateTime(reservation.getExpiresAt()) + ".",
                "Open contracts",
                this::showContracts)));
        }
        return cards;
    }

    private List<Attention> overdueInstalments(int userId) throws SQLException {
        List<Attention> cards = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (Contract contract : contractService.findByClient(userId)) {
            for (PaymentSchedule instalment : paymentService.findScheduleByContract(contract.getId())) {
                boolean overdue = instalment.getStatus() == ScheduleStatus.OVERDUE
                    || (instalment.getStatus() != ScheduleStatus.PAID
                        && instalment.getDueDate() != null
                        && instalment.getDueDate().isBefore(today));
                if (!overdue) {
                    continue;
                }
                long days = Math.max(1, today.toEpochDay() - instalment.getDueDate().toEpochDay());
                cards.add(new Attention(1, days, attentionCard(
                    "overdue",
                    "OVERDUE BY " + days + (days == 1 ? " DAY" : " DAYS"),
                    "Instalment " + instalment.getInstallmentNo(),
                    Format.paymentAmount(instalment.getAmountDue()) + " due on "
                        + propertyTitle(contract.getPropertyId()) + ".",
                    "Declare a payment",
                    this::showContracts)));
            }
        }
        return cards;
    }

    private List<Attention> propertiesNeedingInfo(int userId) throws SQLException {
        List<Attention> cards = new ArrayList<>();
        for (Property property : propertyService.findByOwner(userId)) {
            if (property.getStatus() != PropertyStatus.NEEDS_INFO) {
                continue;
            }
            String note = property.getReviewNote() == null || property.getReviewNote().isBlank()
                ? "An agent has asked for more information before publishing."
                : property.getReviewNote();
            cards.add(new Attention(2, 0, attentionCard(
                "informal",
                "AGENT REPLIED",
                property.getTitle(),
                note,
                "Read and respond",
                this::showProperties)));
        }
        return cards;
    }

    private Region attentionCard(String tone, String eyebrow, String title, String detail,
            String actionText, Runnable action) {
        Label eyebrowLabel = new Label(eyebrow);
        eyebrowLabel.getStyleClass().addAll("attention-eyebrow", "tone-" + toneColour(tone));

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("body-medium");
        titleLabel.setWrapText(true);

        Label detailLabel = new Label(detail);
        detailLabel.getStyleClass().add("hint");
        detailLabel.setWrapText(true);

        Button actionButton = new Button(actionText);
        actionButton.getStyleClass().addAll("button", "button-secondary");
        actionButton.setOnAction(event -> action.run());

        HBox actions = new HBox(actionButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(9, eyebrowLabel, titleLabel, detailLabel, actions);
        card.getStyleClass().addAll("attention-card", tone);
        card.setMinWidth(310);
        card.setPrefWidth(310);
        card.setMaxWidth(340);
        return card;
    }

    private String toneColour(String tone) {
        return switch (tone) {
            case "overdue" -> "bad";
            case "informal" -> "info";
            default -> "warn";
        };
    }

    private String propertyTitle(int propertyId) {
        try {
            Property property = propertyService.findById(propertyId);
            return property == null ? "a property" : property.getTitle();
        } catch (SQLException e) {
            return "a property";
        }
    }
}
