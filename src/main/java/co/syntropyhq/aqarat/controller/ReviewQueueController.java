package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.dao.AuditDao;
import co.syntropyhq.aqarat.dao.DistrictDao;
import co.syntropyhq.aqarat.dao.PropertyDao;
import co.syntropyhq.aqarat.dao.PropertyMessageDao;
import co.syntropyhq.aqarat.dao.PropertyPhotoDao;
import co.syntropyhq.aqarat.dao.PropertySearch;
import co.syntropyhq.aqarat.dao.PropertyTypeDao;
import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.DealType;
import co.syntropyhq.aqarat.model.District;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.PropertyPhoto;
import co.syntropyhq.aqarat.model.PropertyStatus;
import co.syntropyhq.aqarat.model.PropertyType;
import co.syntropyhq.aqarat.model.Role;
import co.syntropyhq.aqarat.service.AuditService;
import co.syntropyhq.aqarat.service.PropertyService;
import co.syntropyhq.aqarat.service.ReferenceService;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.AnimationUtil;
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.Panel;
import co.syntropyhq.aqarat.util.Router;
import co.syntropyhq.aqarat.util.SessionManager;
import co.syntropyhq.aqarat.util.UIHelper;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class ReviewQueueController {

    private static final int PAGE_SIZE = 15;

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
        new PropertyService(new PropertyDao(), new PropertyPhotoDao(), new PropertyMessageDao(), new AuditService(new AuditDao()));
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
        unassignedTabButton.getStyleClass().setAll("tab-pill-button", "active");
        myQueueTabButton.getStyleClass().setAll("tab-pill-button");
        runSearch(0);
    }

    private void selectMyQueueTab() {
        showingUnassigned = false;
        unassignedTabButton.getStyleClass().setAll("tab-pill-button");
        myQueueTabButton.getStyleClass().setAll("tab-pill-button", "active");
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
        propertyList.setPlaceholder(UIHelper.createEmptyState(
            showingUnassigned ? "No Unassigned Submissions" : "Your Review Queue is Empty",
            showingUnassigned ? "All incoming properties have been claimed." : "Claim a property from the Unassigned tab to start review."
        ));

        List<Property> results;
        try {
            results = propertyService.searchForStaff(searchStatuses(), searchFilters(), offset, PAGE_SIZE);
        } catch (SQLException e) {
            AlertUtil.showError("Could not load review queue. Check that SQL Server is running.");
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

    private String waitingText(Property property) {
        return property.getStatus() == PropertyStatus.WITHDRAWAL_REQUESTED
            ? "Owner requested listing withdrawal"
            : "Submitted on " + Format.dateTime(property.getSubmittedAt());
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
        return districtName + " · " + typeName + " · " + Format.enumLabel(property.getDealType())
            + " · " + Format.area(property.getAreaSqm())
            + " · " + property.getBedrooms() + " beds";
    }

    private final class SubmissionCard extends ListCell<Property> {

        @Override
        protected void updateItem(Property property, boolean empty) {
            super.updateItem(property, empty);
            setText(null);
            setGraphic(empty || property == null ? null : buildCard(property));
        }

        private HBox buildCard(Property property) {
            HBox row = new HBox(16);
            row.getStyleClass().addAll("card", "card-hoverable");
            row.setPadding(new Insets(16));
            row.setAlignment(Pos.TOP_LEFT);
            row.getChildren().addAll(
                UIHelper.createRowThumbnail(firstPhotoPath(property.getId()), 132, 96),
                buildDetails(property));
            AnimationUtil.addHoverLift(row);
            return row;
        }

        private VBox buildDetails(Property property) {
            VBox card = new VBox(10);
            HBox.setHgrow(card, Priority.ALWAYS);

            HBox header = new HBox(12);
            header.setAlignment(Pos.CENTER_LEFT);

            Label title = new Label(property.getTitle());
            title.getStyleClass().add("body-medium");
            title.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(title, Priority.ALWAYS);

            Label pill = UIHelper.createStatusPill(property.getStatus());
            Label price = new Label(priceText(property));
            price.getStyleClass().add("price-display");

            header.getChildren().addAll(title, pill, price);

            Label meta = new Label(metaLine(property));
            meta.getStyleClass().add("hint");

            Label waiting = new Label(waitingText(property));
            waiting.getStyleClass().add("hint");

            HBox actions = buildActions(property);

            card.getChildren().addAll(header, meta, waiting, actions);
            return card;
        }
        // A row without its photograph is still usable, so a failed lookup is
        // not allowed to take the whole queue down with it.
        private String firstPhotoPath(int propertyId) {
            try {
                List<PropertyPhoto> photos = propertyService.findPhotos(propertyId);
                return photos.isEmpty() ? null : photos.get(0).getFilePath();
            } catch (SQLException e) {
                return null;
            }
        }

        private HBox buildActions(Property property) {
            HBox actions = new HBox(8);
            actions.setAlignment(Pos.CENTER_LEFT);

            Button open = new Button("Review submission →");
            open.getStyleClass().addAll("button", "button-primary");
            open.setOnAction(event -> handleOpen(property));
            actions.getChildren().add(open);

            if (showingUnassigned) {
                Button claim = new Button("Claim to my queue");
                claim.getStyleClass().addAll("button", "button-secondary");
                claim.setOnAction(event -> handleClaim(property));
                actions.getChildren().add(claim);
            }
            return actions;
        }
    }
}
