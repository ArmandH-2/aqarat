package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.dao.AuditDao;
import co.syntropyhq.aqarat.dao.PropertyDao;
import co.syntropyhq.aqarat.dao.PropertyPhotoDao;
import co.syntropyhq.aqarat.dao.PropertySearch;
import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.PropertyStatus;
import co.syntropyhq.aqarat.model.Role;
import co.syntropyhq.aqarat.service.AuditService;
import co.syntropyhq.aqarat.service.PropertyService;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.Panel;
import co.syntropyhq.aqarat.util.Router;
import co.syntropyhq.aqarat.util.SessionManager;
import java.sql.SQLException;
import java.util.List;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

// DESIGN.md section 9 lists four dashboard figures. Viewings and overdue
// payments have no service yet - they belong to phases 4 and 5 - so only the
// two figures that can be answered today are shown here. No placeholder
// tiles for the other two (CLAUDE.md: work that arrives before it was
// requested is work nobody has reviewed).
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

    private final PropertyService propertyService =
        new PropertyService(new PropertyDao(), new PropertyPhotoDao(), new AuditService(new AuditDao()));

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
        } catch (SQLException e) {
            AlertUtil.showError("Could not load dashboard figures. Check that SQL Server is running.");
        }
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
