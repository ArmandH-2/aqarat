package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.dao.AuditDao;
import co.syntropyhq.aqarat.dao.DistrictDao;
import co.syntropyhq.aqarat.dao.PropertyDao;
import co.syntropyhq.aqarat.dao.PropertyPhotoDao;
import co.syntropyhq.aqarat.dao.PropertySearch;
import co.syntropyhq.aqarat.dao.PropertyTypeDao;
import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.DealType;
import co.syntropyhq.aqarat.model.District;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.PropertyStatus;
import co.syntropyhq.aqarat.model.PropertyType;
import co.syntropyhq.aqarat.model.Role;
import co.syntropyhq.aqarat.service.AuditService;
import co.syntropyhq.aqarat.service.PropertyService;
import co.syntropyhq.aqarat.service.ReferenceService;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.Panel;
import co.syntropyhq.aqarat.util.Router;
import co.syntropyhq.aqarat.util.SessionManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

// One list with a toggle between "Unassigned" and "My queue" rather than two
// ListViews side by side - both queries return the same row shape and the
// agent only ever wants to look at one of them at a time, so a toggle keeps
// the panel to one card list instead of duplicating it (CLAUDE.md, one
// vertical slice, boring version).
public class ReviewQueueController {

    private static final int PAGE_SIZE = 20;

    @FXML
    private VBox contentBox;
    @FXML
    private Label accessDeniedLabel;
    @FXML
    private Button unassignedTabButton;
    @FXML
    private Button myQueueTabButton;
    @FXML
    private ListView<Property> propertyList;
    @FXML
    private Button previousButton;
    @FXML
    private Button nextButton;

    private final PropertyService propertyService =
        new PropertyService(new PropertyDao(), new PropertyPhotoDao(), new AuditService(new AuditDao()));
    private final ReferenceService referenceService =
        new ReferenceService(new DistrictDao(), new PropertyTypeDao());

    private final Map<Integer, District> districtsById = new HashMap<>();
    private final Map<Integer, PropertyType> typesById = new HashMap<>();

    private boolean showingUnassigned = true;
    private int currentOffset = 0;

    @FXML
    private void initialize() {
        AppUser user = SessionManager.getCurrentUser();
        if (user == null || (user.getRole() != Role.AGENT && user.getRole() != Role.ADMIN)) {
            denyAccess();
            return;
        }
        propertyList.setCellFactory(list -> new SubmissionCard());
        loadReferenceData();
        selectUnassignedTab();
    }

    private void denyAccess() {
        contentBox.setVisible(false);
        contentBox.setManaged(false);
        accessDeniedLabel.setText("You do not have access to the review queue.");
        accessDeniedLabel.setVisible(true);
        accessDeniedLabel.setManaged(true);
    }

    private void loadReferenceData() {
        try {
            for (District district : referenceService.findAllDistricts()) {
                districtsById.put(district.getId(), district);
            }
            for (PropertyType type : referenceService.findAllPropertyTypes()) {
                typesById.put(type.getId(), type);
            }
        } catch (SQLException e) {
            AlertUtil.showError("Could not load reference data. Check that SQL Server is running.");
        }
    }

    @FXML
    private void handleUnassignedTab() {
        selectUnassignedTab();
    }

    @FXML
    private void handleMyQueueTab() {
        selectMyQueueTab();
    }

    private void selectUnassignedTab() {
        showingUnassigned = true;
        unassignedTabButton.getStyleClass().setAll("button", "button-primary");
        myQueueTabButton.getStyleClass().setAll("button", "button-secondary");
        runSearch(0);
    }

    private void selectMyQueueTab() {
        showingUnassigned = false;
        unassignedTabButton.getStyleClass().setAll("button", "button-secondary");
        myQueueTabButton.getStyleClass().setAll("button", "button-primary");
        runSearch(0);
    }

    @FXML
    private void handlePrevious() {
        runSearch(Math.max(0, currentOffset - PAGE_SIZE));
    }

    @FXML
    private void handleNext() {
        runSearch(currentOffset + PAGE_SIZE);
    }

    private void runSearch(int offset) {
        Label empty = new Label(showingUnassigned
            ? "No submissions are waiting to be claimed."
            : "You have not claimed any submissions.");
        empty.getStyleClass().add("empty-state");
        propertyList.setPlaceholder(empty);

        List<Property> results;
        try {
            results = propertyService.searchForStaff(searchStatuses(), searchFilters(), offset, PAGE_SIZE);
        } catch (SQLException e) {
            AlertUtil.showError("Could not load the review queue. Check that SQL Server is running.");
            return;
        }
        currentOffset = offset;
        propertyList.setItems(FXCollections.observableArrayList(results));
        previousButton.setDisable(currentOffset == 0);
        nextButton.setDisable(results.size() < PAGE_SIZE);
    }

    private List<PropertyStatus> searchStatuses() {
        return showingUnassigned
            ? List.of(PropertyStatus.PENDING_REVIEW, PropertyStatus.WITHDRAWAL_REQUESTED)
            : List.of(PropertyStatus.PENDING_REVIEW, PropertyStatus.NEEDS_INFO,
                PropertyStatus.WITHDRAWAL_REQUESTED);
    }

    private PropertySearch searchFilters() {
        PropertySearch filters = new PropertySearch();
        if (showingUnassigned) {
            filters.setUnassignedOnly(Boolean.TRUE);
        } else {
            filters.setAgentId(SessionManager.getCurrentUser().getId());
        }
        return filters;
    }

    private void handleClaim(Property property) {
        int agentId = SessionManager.getCurrentUser().getId();
        try {
            propertyService.claim(property.getId(), agentId);
        } catch (PropertyService.AlreadyClaimedException e) {
            AlertUtil.showError(e.getMessage());
            runSearch(currentOffset);
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("You have claimed this submission.");
        runSearch(currentOffset);
    }

    private void handleOpen(Property property) {
        Router.show(Panel.REVIEW_SUBMISSION, property.getId());
    }

    private String pillClass(PropertyStatus status) {
        switch (status) {
            case PENDING_REVIEW:
            case NEEDS_INFO:
            case WITHDRAWAL_REQUESTED:
                return "pill-warn";
            default:
                return "pill-neutral";
        }
    }

    // A withdrawal request is not a new submission, so the row says so
    // instead of reusing submittedAt - that timestamp is stale once the
    // owner has asked for the listing to come down.
    private String waitingText(Property property) {
        return property.getStatus() == PropertyStatus.WITHDRAWAL_REQUESTED
            ? "Owner has asked to remove this listing"
            : "Submitted " + Format.dateTime(property.getSubmittedAt());
    }

    private String priceText(Property property) {
        return property.getDealType() == DealType.RENT
            ? Format.monthlyRent(property.getAskingPrice())
            : Format.salePrice(property.getAskingPrice());
    }

    private String metaLine(Property property) {
        District district = districtsById.get(property.getDistrictId());
        PropertyType type = typesById.get(property.getPropertyTypeId());
        String districtName = district == null ? "-" : district.getName();
        String typeName = type == null ? "-" : type.getName();
        return districtName + " • " + typeName + " • " + Format.enumLabel(property.getDealType())
            + " • " + priceText(property) + " • " + Format.area(property.getAreaSqm());
    }

    // One card per submission, matching the shape MyPropertiesController
    // already established for a list of properties with per-row actions.
    private final class SubmissionCard extends ListCell<Property> {

        @Override
        protected void updateItem(Property property, boolean empty) {
            super.updateItem(property, empty);
            setText(null);
            setGraphic(empty || property == null ? null : buildCard(property));
        }

        private VBox buildCard(Property property) {
            Label title = new Label(property.getTitle());
            Label pill = new Label(Format.enumLabel(property.getStatus()));
            pill.getStyleClass().addAll("pill", pillClass(property.getStatus()));
            HBox header = new HBox(8, title, pill);

            Label meta = new Label(metaLine(property));
            meta.getStyleClass().add("label-soft");

            Label waiting = new Label(waitingText(property));
            waiting.getStyleClass().add("hint");

            HBox actions = buildActions(property);

            VBox card = new VBox(8, header, meta, waiting, actions);
            card.getStyleClass().add("card");
            card.setPadding(new Insets(16));
            return card;
        }

        private HBox buildActions(Property property) {
            HBox actions = new HBox(8);
            Button open = new Button("Open");
            open.getStyleClass().addAll("button", "button-secondary");
            open.setOnAction(event -> handleOpen(property));
            actions.getChildren().add(open);

            if (showingUnassigned) {
                Button claim = new Button("Claim");
                claim.getStyleClass().addAll("button", "button-primary");
                claim.setOnAction(event -> handleClaim(property));
                actions.getChildren().add(claim);
            }
            return actions;
        }
    }
}
