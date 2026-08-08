package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.dao.AuditDao;
import co.syntropyhq.aqarat.dao.DistrictDao;
import co.syntropyhq.aqarat.dao.PropertyDao;
import co.syntropyhq.aqarat.dao.PropertyMessageDao;
import co.syntropyhq.aqarat.dao.PropertyPhotoDao;
import co.syntropyhq.aqarat.dao.PropertyTypeDao;
import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.DealType;
import co.syntropyhq.aqarat.model.District;
import co.syntropyhq.aqarat.model.NewPhoto;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.PropertyMessage;
import co.syntropyhq.aqarat.model.PropertyStatus;
import co.syntropyhq.aqarat.model.PropertyType;
import co.syntropyhq.aqarat.service.AuditService;
import co.syntropyhq.aqarat.service.PropertyService;
import co.syntropyhq.aqarat.service.ReferenceService;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.Panel;
import co.syntropyhq.aqarat.util.Router;
import co.syntropyhq.aqarat.util.SessionManager;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

public class MyPropertiesController {

    @FXML
    private ListView<Property> propertyList;

    private final PropertyService propertyService =
        new PropertyService(new PropertyDao(), new PropertyPhotoDao(), new PropertyMessageDao(), new AuditService(new AuditDao()));
    private final ReferenceService referenceService =
        new ReferenceService(new DistrictDao(), new PropertyTypeDao());

    private Map<Integer, District> districtsById = new HashMap<>();
    private Map<Integer, PropertyType> typesById = new HashMap<>();

    @FXML
    private void initialize() {
        // A customer-only panel: the sidebar hides it from everyone else, and
        // a guest would NPE on getCurrentUser().getId(). The guard stays after
        // navigation already hides it, not instead of.
        AppUser user = SessionManager.getCurrentUser();
        if (user == null || user.getRole() != co.syntropyhq.aqarat.model.Role.CUSTOMER) {
            denyAccess();
            return;
        }
        Label emptyState = new Label("You have not submitted any properties yet.");
        emptyState.getStyleClass().add("empty-state");
        propertyList.setPlaceholder(emptyState);
        propertyList.setCellFactory(list -> new PropertyCard());
        loadReferenceData();
        loadProperties();
    }

    private void denyAccess() {
        Label denied = new Label("Only customers can see their own properties.");
        denied.getStyleClass().add("empty-state");
        propertyList.setPlaceholder(denied);
        propertyList.setItems(FXCollections.observableArrayList());
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

    // Same dialog pattern as handleRequestRemoval: a card has nowhere to
    // hold an inline form, and the answer belongs to an action, not to
    // field validation. The answer is written to the discussion thread and
    // the submission moves back to PENDING_REVIEW as one unit.
    private void handleRespond(Property property) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setHeaderText(null);
        dialog.setTitle("Respond to review");
        dialog.setContentText("Your answer, which the reviewing agent will see:");
        Optional<String> input = dialog.showAndWait();
        if (input.isEmpty()) {
            return;
        }
        String answer = input.get().trim();
        if (answer.isEmpty()) {
            AlertUtil.showError("An answer is required to go back to review.");
            return;
        }
        try {
            propertyService.respondToReview(property.getId(), answer,
                SessionManager.getCurrentUser().getId());
        } catch (PropertyService.InvalidTransitionException e) {
            AlertUtil.showError("This submission can no longer be sent back for review.");
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("Your answer has been sent back for review.");
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

    // An agent can only ask for photos (NEEDS_INFO) if the owner actually has
    // somewhere to add them - that is what this button is for. All the file
    // work is in PropertyService, same rule as submit().
    private void handleAddPhotos(Property property) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose photos for " + property.getTitle());
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Photos", "*.jpg", "*.jpeg", "*.png"));
        List<File> chosen = chooser.showOpenMultipleDialog(null);
        if (chosen == null || chosen.isEmpty()) {
            return;
        }
        List<NewPhoto> photos = new ArrayList<>();
        for (File file : chosen) {
            photos.add(new NewPhoto(file.toPath()));
        }
        try {
            propertyService.addPhotos(property.getId(), photos);
        } catch (IOException e) {
            AlertUtil.showError("One photo could not be read. Check the file is a JPG or PNG.");
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("The photos are attached to the listing.");
    }

    // The dialog is the one place DESIGN.md's owner-withdrawal note allows
    // one (CLAUDE.md, comments) - a list row has nowhere to hold an inline
    // form, and this is collecting input for an action, not reporting a
    // validation failure.
    private void handleRequestRemoval(Property property) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setHeaderText(null);
        dialog.setTitle("Request removal");
        dialog.setContentText("Why are you asking for this listing to be taken down?");
        Optional<String> input = dialog.showAndWait();
        if (input.isEmpty()) {
            return;
        }
        String reason = input.get().trim();
        if (reason.isEmpty()) {
            AlertUtil.showError("A reason is required to request removal.");
            return;
        }
        try {
            propertyService.requestWithdrawal(property.getId(), reason);
        } catch (PropertyService.InvalidTransitionException e) {
            AlertUtil.showError("This listing can no longer have its removal requested.");
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("Your removal request has been sent to an agent.");
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
            case WITHDRAWAL_REQUESTED:
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

            VBox thread = buildThread(property);
            if (thread != null) {
                card.getChildren().add(thread);
            }

            HBox actions = buildActions(property);
            if (!actions.getChildren().isEmpty()) {
                card.getChildren().add(actions);
            }
            return card;
        }

        // The discussion - the agent's question, the owner's answers, the
        // removal request - reads as quiet lines under the meta line. Loaded
        // on demand per card rather than for the whole list at once.
        private VBox buildThread(Property property) {
            List<PropertyMessage> messages;
            try {
                messages = propertyService.findMessages(property.getId());
            } catch (SQLException e) {
                return null;
            }
            if (messages.isEmpty()) {
                return null;
            }
            int currentUserId = SessionManager.getCurrentUser().getId();
            VBox thread = new VBox(8);
            thread.getStyleClass().add("thread");
            for (PropertyMessage message : messages) {
                String sender = message.getAuthorId() == currentUserId
                    ? "You" : message.getAuthorName();
                Label senderLine = new Label(sender + " • " + Format.dateTime(message.getCreatedAt()));
                senderLine.getStyleClass().add("hint");
                Label body = new Label(message.getMessage());
                body.setWrapText(true);
                thread.getChildren().add(new VBox(2, senderLine, body));
            }
            return thread;
        }

        private HBox buildActions(Property property) {
            HBox actions = new HBox(8);
            PropertyStatus status = property.getStatus();
            // The owner's only way into the listing page: a submission that is
            // still under review never appears in Browse listings, so without
            // this the photos they just uploaded are invisible to them.
            Button view = new Button("View listing");
            view.getStyleClass().addAll("button", "button-secondary");
            view.setOnAction(event -> Router.show(Panel.PROPERTY_DETAILS, property.getId()));
            actions.getChildren().add(view);
            if (status == PropertyStatus.NEEDS_INFO) {
                Button respond = new Button("Respond");
                respond.getStyleClass().addAll("button", "button-secondary");
                respond.setOnAction(event -> handleRespond(property));
                actions.getChildren().add(respond);
            }
            if (status == PropertyStatus.NEEDS_INFO || status == PropertyStatus.PENDING_REVIEW) {
                Button addPhotos = new Button("Add photos");
                addPhotos.getStyleClass().addAll("button", "button-secondary");
                addPhotos.setOnAction(event -> handleAddPhotos(property));
                actions.getChildren().add(addPhotos);
            }
            if (status == PropertyStatus.PENDING_REVIEW || status == PropertyStatus.NEEDS_INFO) {
                Button withdraw = new Button("Withdraw");
                withdraw.getStyleClass().addAll("button", "button-danger");
                withdraw.setOnAction(event -> handleWithdraw(property));
                actions.getChildren().add(withdraw);
            }
            if (status == PropertyStatus.AVAILABLE) {
                Button requestRemoval = new Button("Request removal");
                requestRemoval.getStyleClass().addAll("button", "button-secondary");
                requestRemoval.setOnAction(event -> handleRequestRemoval(property));
                actions.getChildren().add(requestRemoval);
            }
            return actions;
        }
    }
}
