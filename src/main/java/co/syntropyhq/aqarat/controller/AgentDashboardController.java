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

// The four figures DESIGN.md section 9 asks for, plus the unassigned queue an
// agent can claim from. Every one is a COUNT in SQL rather than a list read
// and measured, so opening the dashboard costs five small queries.
public class AgentDashboardController {

    @FXML
    private VBox contentBox;
    @FXML
    private Label accessDeniedLabel;
    @FXML
    private VBox queueCard;
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
        queueCard.setCursor(Cursor.HAND);
        queueCard.setOnMouseClicked(event -> Router.show(Panel.REVIEW_QUEUE));
        loadCounts(user.getId());
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
            queueCountLabel.setText(String.valueOf(countQueue(agentId)));
            unassignedCountLabel.setText(String.valueOf(countUnassigned()));
            activeListingsCountLabel.setText(String.valueOf(countActiveListings(agentId)));
            weekViewingsCountLabel.setText(String.valueOf(countViewingsThisWeek(agentId)));
            // The dashboard speaks about the agent's own portfolio, so the
            // overdue figure is their queue, not the agency's.
            AppUser user = SessionManager.getCurrentUser();
            Integer scope = user.getRole() == Role.ADMIN ? null : agentId;
            overdueCountLabel.setText(String.valueOf(reportService.overduePayments(scope).size()));
        } catch (SQLException e) {
            AlertUtil.showError("Could not load dashboard figures. Check that SQL Server is running.");
        }
    }

    // The database stores UTC, so the week is measured in UTC too rather than
    // against a local midnight the stored timestamps know nothing about.
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
}
