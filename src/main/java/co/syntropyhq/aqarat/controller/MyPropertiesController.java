package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.dao.AuditDao;
import co.syntropyhq.aqarat.dao.DistrictDao;
import co.syntropyhq.aqarat.dao.PropertyDao;
import co.syntropyhq.aqarat.dao.PropertyPhotoDao;
import co.syntropyhq.aqarat.dao.PropertyTypeDao;
import co.syntropyhq.aqarat.model.DealType;
import co.syntropyhq.aqarat.model.District;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.PropertyStatus;
import co.syntropyhq.aqarat.model.PropertyType;
import co.syntropyhq.aqarat.service.AuditService;
import co.syntropyhq.aqarat.service.PropertyService;
import co.syntropyhq.aqarat.service.ReferenceService;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.Format;
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

public class MyPropertiesController {

    @FXML
    private ListView<Property> propertyList;

    private final PropertyService propertyService =
        new PropertyService(new PropertyDao(), new PropertyPhotoDao(), new AuditService(new AuditDao()));
    private final ReferenceService referenceService =
        new ReferenceService(new DistrictDao(), new PropertyTypeDao());

    private Map<Integer, District> districtsById = new HashMap<>();
    private Map<Integer, PropertyType> typesById = new HashMap<>();

    @FXML
    private void initialize() {
        Label emptyState = new Label("You have not submitted any properties yet.");
        emptyState.getStyleClass().add("empty-state");
        propertyList.setPlaceholder(emptyState);
        propertyList.setCellFactory(list -> new PropertyCard());
        loadReferenceData();
        loadProperties();
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

    private void loadProperties() {
        try {
            int ownerId = SessionManager.getCurrentUser().getId();
            List<Property> properties = propertyService.findByOwner(ownerId);
            propertyList.setItems(FXCollections.observableArrayList(properties));
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
        }
    }

    private void handleRespond(Property property) {
        try {
            propertyService.changeStatus(property.getId(), PropertyStatus.PENDING_REVIEW);
        } catch (PropertyService.InvalidTransitionException e) {
            AlertUtil.showError("This submission can no longer be sent back for review.");
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("Your submission has been sent back for review.");
        loadProperties();
    }

    private void handleWithdraw(Property property) {
        if (!AlertUtil.confirm("Withdraw this submission? This cannot be undone.")) {
            return;
        }
        try {
            propertyService.withdraw(property.getId());
        } catch (PropertyService.InvalidTransitionException e) {
            AlertUtil.showError("This submission can no longer be withdrawn.");
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("The submission has been withdrawn.");
        loadProperties();
    }

    // Maps each status to the one pill style docs/UI-STYLE.md assigns it.
    // Nothing on this screen is allowed to choose a colour on its own.
    private String pillClass(PropertyStatus status) {
        switch (status) {
            case AVAILABLE:
                return "pill-good";
            case PENDING_REVIEW:
            case NEEDS_INFO:
                return "pill-warn";
            case REJECTED:
                return "pill-bad";
            case RESERVED:
            case UNDER_CONTRACT:
            case DRAFT:
                return "pill-info";
            default:
                return "pill-neutral";
        }
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

    // One card per submission. A ListView cell, not a table row, because a
    // card is the only shape that comfortably holds a title, a pill, a meta
    // line, an optional review note and per-row action buttons together.
    private final class PropertyCard extends ListCell<Property> {

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

            VBox card = new VBox(8, header, meta);
            card.getStyleClass().add("card");
            card.setPadding(new Insets(16));

            if (property.getStatus() == PropertyStatus.NEEDS_INFO && property.getReviewNote() != null) {
                Label reviewNote = new Label("Review note: " + property.getReviewNote());
                reviewNote.setWrapText(true);
                reviewNote.getStyleClass().add("hint");
                card.getChildren().add(reviewNote);
            }

            HBox actions = buildActions(property);
            if (!actions.getChildren().isEmpty()) {
                card.getChildren().add(actions);
            }
            return card;
        }

        private HBox buildActions(Property property) {
            HBox actions = new HBox(8);
            PropertyStatus status = property.getStatus();
            if (status == PropertyStatus.NEEDS_INFO) {
                Button respond = new Button("Respond");
                respond.getStyleClass().addAll("button", "button-secondary");
                respond.setOnAction(event -> handleRespond(property));
                actions.getChildren().add(respond);
            }
            if (status == PropertyStatus.PENDING_REVIEW || status == PropertyStatus.NEEDS_INFO) {
                Button withdraw = new Button("Withdraw");
                withdraw.getStyleClass().addAll("button", "button-danger");
                withdraw.setOnAction(event -> handleWithdraw(property));
                actions.getChildren().add(withdraw);
            }
            return actions;
        }
    }
}
