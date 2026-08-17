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
import co.syntropyhq.aqarat.util.AnimationUtil;
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.Panel;
import co.syntropyhq.aqarat.util.Router;
import co.syntropyhq.aqarat.util.SessionManager;
import co.syntropyhq.aqarat.util.UIHelper;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

public class MyPropertiesController {

    @FXML
    private ListView<Property> propertyList;
    @FXML
    private Label totalCountLabel;
    @FXML
    private Label activeCountLabel;
    @FXML
    private Label reviewCountLabel;
    @FXML
    private Label needsInfoCountLabel;

    private final PropertyService propertyService =
        new PropertyService(new PropertyDao(), new PropertyPhotoDao(), new PropertyMessageDao(), new AuditService(new AuditDao()));
    private final ReferenceService referenceService =
        new ReferenceService(new DistrictDao(), new PropertyTypeDao());

    private final Map<Integer, District> districtsById = new HashMap<>();
    private final Map<Integer, PropertyType> typesById = new HashMap<>();

    @FXML
    private void initialize() {
        AppUser user = SessionManager.getCurrentUser();
        if (user == null || user.getRole() != co.syntropyhq.aqarat.model.Role.CUSTOMER) {
            denyAccess();
            return;
        }
        propertyList.setPlaceholder(UIHelper.createEmptyState("No Properties Submitted Yet", "Click '+ Submit new property' above to list your apartment, villa, or land."));
        propertyList.setCellFactory(list -> new PropertyCard());
        loadReferenceData();
        loadProperties();
    }

    private void denyAccess() {
        propertyList.setPlaceholder(UIHelper.createEmptyState("Access Restricted", "Only registered customer accounts can manage owner properties."));
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
            updateKpis(properties);
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
        }
    }

    private void updateKpis(List<Property> properties) {
        int total = properties.size();
        int active = 0;
        int review = 0;
        int needsInfo = 0;

        for (Property p : properties) {
            if (p.getStatus() == PropertyStatus.AVAILABLE) active++;
            else if (p.getStatus() == PropertyStatus.PENDING_REVIEW) review++;
            else if (p.getStatus() == PropertyStatus.NEEDS_INFO) needsInfo++;
        }

        AnimationUtil.animateCount(totalCountLabel, total, 300);
        AnimationUtil.animateCount(activeCountLabel, active, 300);
        AnimationUtil.animateCount(reviewCountLabel, review, 300);
        AnimationUtil.animateCount(needsInfoCountLabel, needsInfo, 300);
    }

    @FXML
    private void handleSubmitNew() {
        Router.show(Panel.SUBMIT_PROPERTY);
    }

    private void handleRespond(Property property) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setHeaderText(null);
        dialog.setTitle("Respond to Review Query");
        dialog.setContentText("Your response to the reviewing agent:");
        Optional<String> input = dialog.showAndWait();
        if (input.isEmpty()) {
            return;
        }
        String answer = input.get().trim();
        if (answer.isEmpty()) {
            AlertUtil.showError("An answer is required to submit back to review.");
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
        AlertUtil.showInfo("Your response has been sent to the agent.");
        loadProperties();
    }

    private void handleWithdraw(Property property) {
        if (!AlertUtil.confirm("Withdraw this submission? This action cannot be undone.")) {
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

    private void handleAddPhotos(Property property) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose photos for " + property.getTitle());
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Photos (*.jpg, *.jpeg, *.png)", "*.jpg", "*.jpeg", "*.png"));
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
            AlertUtil.showError("One photo could not be read. Ensure files are valid images.");
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("Photos successfully attached to listing.");
    }

    private void handleRequestRemoval(Property property) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setHeaderText(null);
        dialog.setTitle("Request Listing Removal");
        dialog.setContentText("Reason for requesting listing removal:");
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
            AlertUtil.showError("This listing cannot currently have its removal requested.");
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("Removal request dispatched to reviewing agent.");
        loadProperties();
    }

    private String priceText(Property property) {
        return property.getDealType() == DealType.RENT
            ? Format.monthlyRent(property.getAskingPrice())
            : Format.salePrice(property.getAskingPrice());
    }

    private final class PropertyCard extends ListCell<Property> {

        @Override
        protected void updateItem(Property property, boolean empty) {
            super.updateItem(property, empty);
            setText(null);
            setGraphic(empty || property == null ? null : buildCard(property));
        }

        private VBox buildCard(Property property) {
            VBox card = new VBox(10);
            card.getStyleClass().addAll("card", "card-hoverable");
            card.setPadding(new Insets(16));

            // Header row
            HBox header = new HBox(12);
            header.setAlignment(Pos.CENTER_LEFT);

            Label title = new Label(property.getTitle());
            title.getStyleClass().add("section-title");
            HBox.setHgrow(title, Priority.ALWAYS);

            Label pill = UIHelper.createStatusPill(property.getStatus());
            Label price = new Label(priceText(property));
            price.getStyleClass().add("section-title");
            price.setStyle("-fx-text-fill: -c-primary; -fx-font-weight: 700;");

            header.getChildren().addAll(title, pill, price);

            // Meta specs line
            District district = districtsById.get(property.getDistrictId());
            PropertyType type = typesById.get(property.getPropertyTypeId());
            String locationStr = (district != null ? district.getName() : "—")
                + " • " + (type != null ? type.getName() : "—")
                + " • " + Format.enumLabel(property.getDealType())
                + " • " + Format.area(property.getAreaSqm());

            Label meta = new Label(locationStr);
            meta.getStyleClass().add("label-soft");

            card.getChildren().addAll(header, meta);

            // Discussion thread if any
            VBox thread = buildThread(property);
            if (thread != null) {
                card.getChildren().add(thread);
            }

            // Actions row
            HBox actions = buildActions(property);
            if (!actions.getChildren().isEmpty()) {
                card.getChildren().add(actions);
            }

            AnimationUtil.addHoverLift(card);
            return card;
        }

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
            VBox thread = new VBox(6);
            thread.getStyleClass().add("thread");

            Label threadHeader = new Label("Review & Discussion Thread");
            threadHeader.getStyleClass().add("hint");
            threadHeader.setStyle("-fx-font-weight: 600; -fx-text-fill: -c-text-secondary;");
            thread.getChildren().add(threadHeader);

            for (PropertyMessage message : messages) {
                String sender = message.getAuthorId() == currentUserId
                    ? "You" : message.getAuthorName();
                Label senderLine = new Label(sender + " • " + Format.dateTime(message.getCreatedAt()));
                senderLine.getStyleClass().add("hint");
                Label body = new Label(message.getMessage());
                body.setWrapText(true);
                body.getStyleClass().add("body");
                thread.getChildren().add(new VBox(2, senderLine, body));
            }
            return thread;
        }

        private HBox buildActions(Property property) {
            HBox actions = new HBox(8);
            actions.setAlignment(Pos.CENTER_LEFT);
            PropertyStatus status = property.getStatus();

            Button view = new Button("View listing");
            view.getStyleClass().addAll("button", "button-secondary");
            view.setOnAction(event -> Router.show(Panel.PROPERTY_DETAILS, property.getId()));
            actions.getChildren().add(view);

            if (status == PropertyStatus.NEEDS_INFO) {
                Button respond = new Button("Respond to query");
                respond.getStyleClass().addAll("button", "button-primary");
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
