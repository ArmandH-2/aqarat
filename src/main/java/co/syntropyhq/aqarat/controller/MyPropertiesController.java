package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.util.DocumentStore;
import co.syntropyhq.aqarat.service.DocumentService;
import co.syntropyhq.aqarat.model.PropertyDocument;
import co.syntropyhq.aqarat.model.NewDocument;
import co.syntropyhq.aqarat.model.DocumentType;
import co.syntropyhq.aqarat.dao.PropertyDocumentDao;
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
import co.syntropyhq.aqarat.model.PropertyPhoto;
import co.syntropyhq.aqarat.model.PropertyMessage;
import co.syntropyhq.aqarat.model.PropertyStatus;
import co.syntropyhq.aqarat.model.PropertyType;
import co.syntropyhq.aqarat.service.AuditService;
import co.syntropyhq.aqarat.service.PropertyService;
import co.syntropyhq.aqarat.service.ReferenceService;
import co.syntropyhq.aqarat.util.Uploads;
import co.syntropyhq.aqarat.util.Banner;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.AnimationUtil;
import co.syntropyhq.aqarat.util.Dialogs;
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
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
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

    private final DocumentService documentService = new DocumentService(
        new PropertyDocumentDao(), new PropertyDao(), new AuditService(new AuditDao()));

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
            propertyList.getItems().clear();
            propertyList.setPlaceholder(Banner.failure("Your properties could not be loaded",
                "The database did not answer. Every submission and listing is untouched.",
                this::loadProperties));
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

        totalCountLabel.setText(Format.count(total));
        activeCountLabel.setText(Format.count(active));
        reviewCountLabel.setText(Format.count(review));
        needsInfoCountLabel.setText(Format.count(needsInfo));
    }

    @FXML
    private void handleSubmitNew() {
        Router.show(Panel.SUBMIT_PROPERTY);
    }

    private void handleRespond(Property property) {
        if (ReviewReply.ask(propertyService, property)) {
            loadProperties();
        }
    }

    private void handleWithdraw(Property property) {
        boolean go = Dialogs.ask("Withdraw this submission?")
            .about(property.getTitle())
            .because("It leaves the review queue and the agent stops working on it. To list the "
                + "property later you would submit it again from the beginning.")
            .confirm("Withdraw it")
            .cancel("Leave it in review")
            .destructive()
            .show();
        if (!go) {
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
        AlertUtil.showUndone("Submission withdrawn",
            "It has left the review queue. Listing the property later means submitting it again.");
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
        AlertUtil.showInfo("Photos added",
            "They appear on the listing straight away, in the order they were chosen.");
    }

    /**
     * Attaching proof of ownership to a property that already exists.
     *
     * <p>The file is picked first and its kind asked afterwards, because the
     * owner knows which file they mean before they know what Aqarat calls it.
     * This is the road out of NEEDS_INFO when the agent's question was "we
     * need to see the deed".
     */
    private void handleAddDocument(Property property) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a document for " + property.getTitle());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
            "Documents (*.pdf, *.jpg, *.jpeg, *.png)", "*.pdf", "*.jpg", "*.jpeg", "*.png"));
        File chosen = chooser.showOpenDialog(null);
        if (chosen == null) {
            return;
        }
        try {
            DocumentStore.validate(chosen.toPath());
        } catch (IOException e) {
            AlertUtil.showUndone("That file cannot be attached.", e.getMessage());
            return;
        }
        ComboBox<DocumentType> typeCombo = new ComboBox<>();
        typeCombo.setItems(FXCollections.observableArrayList(DocumentType.values()));
        typeCombo.getSelectionModel().select(DocumentType.TITLE_DEED);
        boolean go = Dialogs.form("Attach " + chosen.getName())
            .about(property.getTitle())
            .note("Only you and Aqarat staff can open it.")
            .required("What is this document?", typeCombo)
            .confirm("Attach it")
            .show();
        if (!go) {
            return;
        }
        try {
            documentService.upload(property.getId(),
                List.of(new NewDocument(chosen.toPath(), typeCombo.getValue())),
                SessionManager.getCurrentUser());
        } catch (IOException | DocumentService.NotPermittedException e) {
            AlertUtil.showUndone("That document was not attached.", e.getMessage());
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("Document attached",
            "An agent reviews it. Your listing goes live once one is verified.");
        loadProperties();
    }

    private void handleEditCopy(Property property) {
        TextField titleField = new TextField(property.getTitle());
        TextArea descriptionArea = new TextArea(
            property.getDescription() == null ? "" : property.getDescription());
        descriptionArea.setWrapText(true);
        descriptionArea.setPrefRowCount(4);
        boolean go = Dialogs.form("Edit the listing text")
            .about(property.getTitle())
            .note("Only the title and description change. Price, size and address stay as submitted.")
            .required("Title", titleField)
            .optional("Description", descriptionArea)
            .confirm("Save it")
            .show();
        if (!go) {
            return;
        }
        try {
            propertyService.editCopy(property.getId(), titleField.getText(),
                descriptionArea.getText(), SessionManager.getCurrentUser());
        } catch (PropertyService.NotPermittedException | IllegalArgumentException e) {
            AlertUtil.showUndone("That listing text was not saved.", e.getMessage());
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("Listing text updated",
            "\"" + titleField.getText() + "\" now shows the new title and description.");
        loadProperties();
    }

    private void handleRequestRemoval(Property property) {
        Optional<String> input = Dialogs.note("Ask for this listing to be removed")
            .about(property.getTitle())
            .explaining("An agent decides on the request. The listing stays on the market and "
                + "keeps taking enquiries until they do.")
            .field("Why it should come off the market")
            .placeholder("Sold privately, taken off the market, price under review")
            .confirm("Send the request")
            .cancel("Keep it listed")
            .show();
        if (input.isEmpty()) {
            return;
        }
        String reason = input.get();
        try {
            propertyService.requestWithdrawal(property.getId(), reason);
        } catch (PropertyService.InvalidTransitionException e) {
            AlertUtil.showError("This listing cannot currently have its removal requested.");
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("Removal requested",
            "An agent decides on it. The listing stays on the market and keeps taking enquiries until they do.");
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

        /*
         * An owner's row leads with the photograph and the lifecycle bar. The
         * old card opened with a status word, which says what a property is but
         * not how far along it is or whether anything is waiting on them — the
         * two things somebody opens this screen to find out.
         */
        private HBox buildCard(Property property) {
            HBox card = new HBox(16);
            card.getStyleClass().addAll("card", "card-hoverable");
            card.setPadding(new Insets(16));
            card.setAlignment(Pos.TOP_LEFT);

            card.getChildren().addAll(buildThumbnail(property), buildBody(property));
            AnimationUtil.addHoverLift(card);
            return card;
        }

        private StackPane buildThumbnail(Property property) {
            StackPane frame = new StackPane();
            frame.getStyleClass().add("owned-thumb");
            frame.setMinSize(132, 96);
            frame.setPrefSize(132, 96);
            frame.setMaxSize(132, 96);

            String path = firstPhotoPath(property.getId());
            File file = path == null ? null : Uploads.resolve(path).toFile();
            if (file != null && file.exists()) {
                ImageView photo = new ImageView(
                    new Image(file.toURI().toString(), 264, 192, false, true, true));
                photo.setFitWidth(132);
                photo.setFitHeight(96);
                photo.setPreserveRatio(false);
                Rectangle clip = new Rectangle(132, 96);
                clip.setArcWidth(14);
                clip.setArcHeight(14);
                photo.setClip(clip);
                frame.getChildren().add(photo);
            }
            return frame;
        }

        private VBox buildBody(Property property) {
            VBox body = new VBox(11);
            HBox.setHgrow(body, Priority.ALWAYS);

            Label title = new Label(property.getTitle());
            title.getStyleClass().add("body-medium");
            title.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(title, Priority.ALWAYS);

            Label price = new Label(priceText(property));
            price.getStyleClass().add("price-display");

            HBox header = new HBox(12, title,
                UIHelper.createStatusPill(property.getStatus()), price);
            header.setAlignment(Pos.CENTER_LEFT);

            District district = districtsById.get(property.getDistrictId());
            PropertyType type = typesById.get(property.getPropertyTypeId());
            Label meta = new Label((district != null ? district.getName() : "\u2014")
                + " \u00b7 " + (type != null ? type.getName() : "\u2014")
                + " \u00b7 " + Format.enumLabel(property.getDealType())
                + " \u00b7 " + Format.area(property.getAreaSqm()));
            meta.getStyleClass().add("hint");

            body.getChildren().addAll(header, meta,
                UIHelper.createLifecycleBar(property.getStatus()));

            Node evidence = buildEvidenceLine(property);
            if (evidence != null) {
                body.getChildren().add(evidence);
            }

            VBox thread = buildThread(property);
            if (thread != null) {
                body.getChildren().add(thread);
            }

            HBox actions = buildActions(property);
            if (!actions.getChildren().isEmpty()) {
                body.getChildren().add(actions);
            }
            return body;
        }

        // A row without its photograph is still a usable row, so a lookup that
        // fails is not allowed to take the list down with it.
        private String firstPhotoPath(int propertyId) {
            try {
                List<PropertyPhoto> photos = propertyService.findPhotos(propertyId);
                return photos.isEmpty() ? null : photos.get(0).getFilePath();
            } catch (SQLException e) {
                return null;
            }
        }

        /*
         * One line telling the owner where their proof of ownership stands,
         * because until something here is verified their listing cannot be
         * published and nothing else on this screen would say so.
         */
        private Node buildEvidenceLine(Property property) {
            List<PropertyDocument> documents;
            try {
                documents = documentService.findForProperty(
                    property.getId(), SessionManager.getCurrentUser());
            } catch (SQLException | DocumentService.NotPermittedException e) {
                return null;
            }
            long verified = documents.stream().filter(PropertyDocument::isVerified).count();

            // A pill rather than a third line of grey text: the lifecycle
            // caption sits directly above this, and two hint lines in a row
            // read as one paragraph with no way to tell which is the status.
            String label;
            String tone;
            if (documents.isEmpty()) {
                label = "No proof of ownership";
                tone = "pill-warn";
            } else if (verified > 0) {
                label = "Ownership verified";
                tone = "pill-good";
            } else {
                label = "Awaiting verification";
                tone = "pill-neutral";
            }

            HBox row = new HBox(8, UIHelper.createPill(label, tone));
            row.setAlignment(Pos.CENTER_LEFT);
            if (!documents.isEmpty()) {
                StringBuilder names = new StringBuilder();
                for (PropertyDocument document : documents) {
                    if (names.length() > 0) {
                        names.append(" · ");
                    }
                    names.append(Format.enumLabel(document.getDocType()));
                }
                Label detail = new Label(names.toString());
                detail.getStyleClass().add("hint");
                detail.setWrapText(true);
                row.getChildren().add(detail);
            }
            return row;
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
                Label senderLine = new Label(sender + " · " + Format.dateTime(message.getCreatedAt()));
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
            // Offered wherever the property is still live business, not only
            // while it is in review: the card tells an owner their listing has
            // no proof of ownership, and a warning with no way to act on it is
            // worse than no warning at all.
            if (status != PropertyStatus.REJECTED && status != PropertyStatus.WITHDRAWN
                    && status != PropertyStatus.CLOSED) {
                Button addDocument = new Button("Add document");
                addDocument.getStyleClass().addAll("button", "button-secondary");
                addDocument.setOnAction(event -> handleAddDocument(property));
                actions.getChildren().add(addDocument);
            }
            if (status != PropertyStatus.REJECTED && status != PropertyStatus.WITHDRAWN
                    && status != PropertyStatus.CLOSED) {
                Button editCopy = new Button("Edit text");
                editCopy.getStyleClass().addAll("button", "button-secondary");
                editCopy.setOnAction(event -> handleEditCopy(property));
                actions.getChildren().add(editCopy);
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
