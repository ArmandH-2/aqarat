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
import co.syntropyhq.aqarat.util.AnimationUtil;
import co.syntropyhq.aqarat.util.Panel;
import co.syntropyhq.aqarat.util.Router;
import co.syntropyhq.aqarat.util.SessionManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
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

    private final PropertyService propertyService =
        new PropertyService(new PropertyDao(), new PropertyPhotoDao(), new PropertyMessageDao(), new AuditService(new AuditDao()));
    private final ViewingService viewingService =
        new ViewingService(new ViewingDao(), propertyService, new AuditService(new AuditDao()));
    private final ReportService reportService =
        new ReportService(new ReportDao(), new SystemSettingDao());

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
            int weekViewings = countViewingsThisWeek(agentId);

            AppUser user = SessionManager.getCurrentUser();
            Integer scope = user.getRole() == Role.ADMIN ? null : agentId;
            int overdueCount = reportService.overduePayments(scope).size();

            AnimationUtil.animateCount(queueCountLabel, queueCount, 350);
            AnimationUtil.animateCount(unassignedCountLabel, unassignedCount, 350);
            AnimationUtil.animateCount(activeListingsCountLabel, activeListings, 350);
            AnimationUtil.animateCount(weekViewingsCountLabel, weekViewings, 350);
            AnimationUtil.animateCount(overdueCountLabel, overdueCount, 350);

            overdueCaptionLabel.setText(user.getRole() == Role.ADMIN
                ? "Payments overdue across the agency" : "Payments overdue in your portfolio");
        } catch (SQLException e) {
            AlertUtil.showError("Could not load dashboard figures. Check that SQL Server is running.");
        }
    }

    private int countViewingsThisWeek(int agentId) throws SQLException {
        LocalDateTime from = LocalDateTime.now(ZoneOffset.UTC);
        return viewingService.findByAgentInRange(agentId, from, from.plusDays(7)).size();
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

    @FXML
    private void handleGoReviewQueue() {
        Router.show(Panel.REVIEW_QUEUE);
    }

    @FXML
    private void handleGoListings() {
        Router.show(Panel.LISTINGS);
    }

    @FXML
    private void handleGoViewings() {
        Router.show(Panel.VIEWINGS);
    }

    @FXML
    private void handleGoPayments() {
        Router.show(Panel.PAYMENTS);
    }

    @FXML
    private void handleGoContracts() {
        Router.show(Panel.CONTRACTS);
    }
}
