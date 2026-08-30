package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.dao.AuditDao;
import co.syntropyhq.aqarat.dao.PropertyDao;
import co.syntropyhq.aqarat.dao.PropertyMessageDao;
import co.syntropyhq.aqarat.dao.PropertyPhotoDao;
import co.syntropyhq.aqarat.dao.ReportDao;
import co.syntropyhq.aqarat.dao.SystemSettingDao;
import co.syntropyhq.aqarat.dao.ViewingDao;
import co.syntropyhq.aqarat.dao.PropertySearch;
import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.PropertyStatus;
import co.syntropyhq.aqarat.model.Role;
import co.syntropyhq.aqarat.service.AuditService;
import co.syntropyhq.aqarat.service.PropertyService;
import co.syntropyhq.aqarat.service.ReportService;
import co.syntropyhq.aqarat.service.ViewingService;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.AnimationUtil;
import co.syntropyhq.aqarat.util.Panel;
import co.syntropyhq.aqarat.util.Router;
import co.syntropyhq.aqarat.util.SessionManager;
import co.syntropyhq.aqarat.dao.OverduePayment;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.Viewing;
import co.syntropyhq.aqarat.model.ViewingStatus;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class AgentDashboardController {

    @FXML
    private VBox contentBox;
    @FXML
    private Label accessDeniedLabel;
    @FXML
    private VBox queueCard;
    @FXML
    private VBox unassignedCard;
    @FXML
    private VBox activeListingsCard;
    @FXML
    private VBox weekViewingsCard;
    @FXML
    private VBox overdueCard;
    @FXML
    private Label queueCountLabel;
    @FXML
    private Label unassignedCountLabel;
    @FXML
    private Label activeListingsCountLabel;
    @FXML
    private Label weekViewingsCountLabel;
    @FXML
    private Label overdueCountLabel;
    @FXML
    private Label overdueCaptionLabel;
    @FXML
    private VBox arrearsSection;
    @FXML
    private VBox arrearsList;
    @FXML
    private Label arrearsTotalLabel;
    @FXML
    private VBox weekSection;
    @FXML
    private VBox weekList;

    /** Enough to see the shape of the problem without becoming the Payments panel. */
    private static final int ROWS = 5;

    private final PropertyService propertyService =
        new PropertyService(new PropertyDao(), new PropertyPhotoDao(), new PropertyMessageDao(), new AuditService(new AuditDao()));
    private final ViewingService viewingService =
        new ViewingService(new ViewingDao(), propertyService, new AuditService(new AuditDao()));
    private final ReportService reportService =
        new ReportService(new ReportDao(), new SystemSettingDao());

    private final Map<Integer, String> propertyTitles = new HashMap<>();

    @FXML
    private void initialize() {
        AppUser user = SessionManager.getCurrentUser();
        if (user == null || (user.getRole() != Role.AGENT && user.getRole() != Role.ADMIN)) {
            denyAccess();
            return;
        }

        setupClickableCard(queueCard, Panel.REVIEW_QUEUE);
        setupClickableCard(unassignedCard, Panel.REVIEW_QUEUE);
        setupClickableCard(activeListingsCard, Panel.LISTINGS);
        setupClickableCard(weekViewingsCard, Panel.VIEWINGS);
        setupClickableCard(overdueCard, Panel.PAYMENTS);

        loadCounts(user.getId());
    }

    private void setupClickableCard(VBox card, Panel panel) {
        if (card == null) return;
        card.setCursor(Cursor.HAND);
        card.setOnMouseClicked(event -> Router.show(panel));
        AnimationUtil.addHoverLift(card);
    }

    private void denyAccess() {
        contentBox.setVisible(false);
        contentBox.setManaged(false);
        accessDeniedLabel.setText("You do not have access to this dashboard.");
        accessDeniedLabel.setVisible(true);
        accessDeniedLabel.setManaged(true);
    }

    private void loadCounts(int agentId) {
        try {
            int queueCount = countQueue(agentId);
            int unassignedCount = countUnassigned();
            int activeListings = countActiveListings(agentId);
            List<Viewing> week = viewingsThisWeek(agentId);
            int weekViewings = week.size();

            AppUser user = SessionManager.getCurrentUser();
            Integer scope = user.getRole() == Role.ADMIN ? null : agentId;
            List<OverduePayment> arrears = reportService.overduePayments(scope);
            int overdueCount = arrears.size();

            queueCountLabel.setText(Format.count(queueCount));
            unassignedCountLabel.setText(Format.count(unassignedCount));
            activeListingsCountLabel.setText(Format.count(activeListings));
            weekViewingsCountLabel.setText(Format.count(weekViewings));
            overdueCountLabel.setText(Format.count(overdueCount));

            overdueCaptionLabel.setText(user.getRole() == Role.ADMIN
                ? "Payments overdue across the agency" : "Payments overdue in your portfolio");

            renderArrears(arrears);
            renderWeek(week);
        } catch (SQLException e) {
            AlertUtil.showError("Could not load dashboard figures. Check that SQL Server is running.");
        }
    }

    private List<Viewing> viewingsThisWeek(int agentId) throws SQLException {
        LocalDateTime from = LocalDateTime.now(ZoneOffset.UTC);
        return viewingService.findByAgentInRange(agentId, from, from.plusDays(7));
    }

    /*
     * The five worst, oldest first, with what the whole backlog comes to beside
     * the heading. A count on its own tells an agent there is a problem; this
     * tells them which client to ring.
     */
    private void renderArrears(List<OverduePayment> arrears) {
        arrearsList.getChildren().clear();
        if (arrears.isEmpty()) {
            return;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (OverduePayment payment : arrears) {
            total = total.add(payment.getAmountDue().subtract(payment.getAmountPaid()));
        }
        arrearsTotalLabel.setText(Format.paymentAmount(total) + " outstanding in total");

        List<OverduePayment> worst = arrears.stream()
            .sorted(Comparator.comparingInt(OverduePayment::getDaysOverdue).reversed())
            .limit(ROWS)
            .toList();
        for (int i = 0; i < worst.size(); i++) {
            OverduePayment payment = worst.get(i);
            String owed = Format.paymentAmount(
                payment.getAmountDue().subtract(payment.getAmountPaid()));
            arrearsList.getChildren().add(row(
                payment.getPropertyTitle(),
                payment.getClientName() + " · instalment " + payment.getInstallmentNo()
                    + " · due " + Format.date(payment.getDueDate()),
                payment.getDaysOverdue() + (payment.getDaysOverdue() == 1 ? " day" : " days"),
                owed,
                "pill-bad",
                i == worst.size() - 1,
                Panel.PAYMENTS));
        }
        show(arrearsSection);
    }

    private void renderWeek(List<Viewing> week) {
        weekList.getChildren().clear();
        if (week.isEmpty()) {
            return;
        }
        List<Viewing> soonest = week.stream()
            .sorted(Comparator.comparing(Viewing::getScheduledAt))
            .limit(ROWS)
            .toList();
        for (int i = 0; i < soonest.size(); i++) {
            Viewing viewing = soonest.get(i);
            weekList.getChildren().add(row(
                propertyTitle(viewing.getPropertyId()),
                Format.dateTime(viewing.getScheduledAt()),
                Format.enumLabel(viewing.getStatus()),
                "",
                viewing.getStatus() == ViewingStatus.CONFIRMED ? "pill-good" : "pill-warn",
                i == soonest.size() - 1,
                Panel.VIEWINGS));
        }
        show(weekSection);
    }

    /* One row of either list: what it is, the detail under it, a pill, and the
       figure on the right. Clicking opens the panel that can act on it, because
       a dashboard that cannot be followed is a poster. */
    private HBox row(String title, String detail, String pillText, String figure,
            String pillClass, boolean last, Panel destination) {
        Label name = new Label(title);
        name.getStyleClass().add("body-medium");
        Label under = new Label(detail);
        under.getStyleClass().add("hint");

        VBox text = new VBox(2, name, under);
        HBox.setHgrow(text, Priority.ALWAYS);

        Label pill = new Label(pillText);
        pill.getStyleClass().addAll("pill", pillClass);

        HBox line = new HBox(12, text, pill);
        line.setAlignment(Pos.CENTER_LEFT);
        if (!figure.isBlank()) {
            Label amount = new Label(figure);
            amount.getStyleClass().add("numeric");
            line.getChildren().add(amount);
        }
        line.getStyleClass().add(last ? "dash-row-last" : "dash-row");
        line.setCursor(Cursor.HAND);
        line.setOnMouseClicked(event -> Router.show(destination));
        return line;
    }

    private String propertyTitle(int propertyId) {
        return propertyTitles.computeIfAbsent(propertyId, id -> {
            try {
                Property property = propertyService.findById(id);
                return property == null ? "Unknown property" : property.getTitle();
            } catch (SQLException e) {
                return "Unknown property";
            }
        });
    }

    private static void show(VBox section) {
        section.setVisible(true);
        section.setManaged(true);
    }

    private int countQueue(int agentId) throws SQLException {
        PropertySearch filters = new PropertySearch();
        filters.setAgentId(agentId);
        List<PropertyStatus> statuses =
            List.of(PropertyStatus.PENDING_REVIEW, PropertyStatus.NEEDS_INFO);
        return propertyService.count(statuses, filters);
    }

    private int countUnassigned() throws SQLException {
        PropertySearch filters = new PropertySearch();
        filters.setUnassignedOnly(Boolean.TRUE);
        return propertyService.count(List.of(PropertyStatus.PENDING_REVIEW), filters);
    }

    private int countActiveListings(int agentId) throws SQLException {
        PropertySearch filters = new PropertySearch();
        filters.setAgentId(agentId);
        return propertyService.count(List.of(PropertyStatus.AVAILABLE), filters);
    }





}
