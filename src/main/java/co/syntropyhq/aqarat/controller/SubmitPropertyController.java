package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.util.DocumentStore;
import co.syntropyhq.aqarat.service.DocumentService;
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
import co.syntropyhq.aqarat.model.PropertyType;
import co.syntropyhq.aqarat.model.Role;
import co.syntropyhq.aqarat.service.AuditService;
import co.syntropyhq.aqarat.service.PropertyService;
import co.syntropyhq.aqarat.service.ReferenceService;
import co.syntropyhq.aqarat.service.ValuationService;
import co.syntropyhq.aqarat.dao.ValuationDao;
import co.syntropyhq.aqarat.dao.SystemSettingDao;
import co.syntropyhq.aqarat.valuation.ComparableProperty;
import co.syntropyhq.aqarat.valuation.ValuationResult;
import co.syntropyhq.aqarat.model.ValuationFlag;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.AnimationUtil;
import co.syntropyhq.aqarat.util.FieldError;
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.Panel;
import co.syntropyhq.aqarat.util.Router;
import co.syntropyhq.aqarat.util.SessionManager;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.util.Duration;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

public class SubmitPropertyController {

    @FXML
    private VBox contentBox;
    @FXML
    private Label accessDeniedLabel;
    @FXML
    private TextField titleField;
    @FXML
    private Label titleError;
    @FXML
    private TextArea descriptionArea;
    @FXML
    private TextField addressLineField;
    @FXML
    private ComboBox<District> districtCombo;
    @FXML
    private Label districtError;
    @FXML
    private ComboBox<PropertyType> propertyTypeCombo;
    @FXML
    private Label propertyTypeError;
    @FXML
    private TextField areaField;
    @FXML
    private Label areaError;
    @FXML
    private TextField bedroomsField;
    @FXML
    private Label bedroomsError;
    @FXML
    private TextField bathroomsField;
    @FXML
    private Label bathroomsError;
    @FXML
    private TextField floorNumberField;
    @FXML
    private Label floorNumberError;
    @FXML
    private TextField totalFloorsField;
    @FXML
    private Label totalFloorsError;
    @FXML
    private TextField yearBuiltField;
    @FXML
    private Label yearBuiltError;
    @FXML
    private CheckBox parkingCheck;
    @FXML
    private CheckBox elevatorCheck;
    @FXML
    private CheckBox balconyCheck;
    @FXML
    private CheckBox furnishedCheck;
    @FXML
    private ComboBox<DealType> dealTypeCombo;
    @FXML
    private Label askingPriceLabel;
    @FXML
    private TextField askingPriceField;
    @FXML
    private Label askingPriceError;
    @FXML
    private VBox termsBox;
    @FXML
    private TextField minTermField;
    @FXML
    private Label minTermError;
    @FXML
    private TextField maxTermField;
    @FXML
    private Label maxTermError;
    @FXML
    private FlowPane photosContainer;
    @FXML
    private FlowPane documentsContainer;
    @FXML
    private ComboBox<DocumentType> documentTypeCombo;
    @FXML
    private Label documentCountBadge;
    @FXML
    private Label documentListLabel;
    @FXML
    private Label documentError;
    @FXML
    private Label photoCountBadge;
    @FXML
    private Label photoListLabel;

    @FXML
    private VBox valuationPanel;
    @FXML
    private Label estimateValue;
    @FXML
    private Label estimateState;
    @FXML
    private VBox estimateDetail;
    @FXML
    private Label plausibilityPill;
    @FXML
    private HBox rangeTrack;
    @FXML
    private Label lowerBoundLabel;
    @FXML
    private Label upperBoundLabel;
    @FXML
    private VBox factorsBox;
    @FXML
    private Label comparablesHeading;
    @FXML
    private VBox comparablesBox;

    private final List<Path> selectedPhotos = new ArrayList<>();
    private final List<NewDocument> selectedDocuments = new ArrayList<>();
    private final DocumentService documentService = new DocumentService(
        new PropertyDocumentDao(), new PropertyDao(), new AuditService(new AuditDao()));

    private final ReferenceService referenceService =
        new ReferenceService(new DistrictDao(), new PropertyTypeDao());
    private final PropertyService propertyService =
        new PropertyService(new PropertyDao(), new PropertyPhotoDao(), new PropertyMessageDao(), new AuditService(new AuditDao()));
    private final ValuationService valuationService = new ValuationService(
        new PropertyDao(), new ValuationDao(), new SystemSettingDao(), new DistrictDao());

    /* Typing an area should not fire a valuation per keystroke, so edits settle
       for a moment before the estimate is recomputed. */
    private final PauseTransition valuationDebounce = new PauseTransition(Duration.millis(450));

    @FXML
    private void initialize() {
        AppUser user = SessionManager.getCurrentUser();
        if (user == null || user.getRole() != Role.CUSTOMER) {
            denyAccess();
            return;
        }
        loadReferenceData();
        setLabelConverter(dealTypeCombo, Format::enumLabel);
        dealTypeCombo.setItems(FXCollections.observableArrayList(DealType.values()));
        dealTypeCombo.getSelectionModel().select(DealType.SALE);
        dealTypeCombo.valueProperty().addListener((obs, oldValue, newValue) -> updateDealTypeUi(newValue));
        updateDealTypeUi(DealType.SALE);
        setLabelConverter(documentTypeCombo, Format::enumLabel);
        documentTypeCombo.setItems(FXCollections.observableArrayList(DocumentType.values()));
        documentTypeCombo.getSelectionModel().select(DocumentType.TITLE_DEED);
        refreshDocumentChips();
        // Photographs needs the same first paint as documents. Without it the
        // count badge renders as an empty pill and the hint line is blank until
        // the owner happens to pick a file - an empty state that looks broken
        // rather than empty, on the first card of the first screen.
        refreshPhotoChips();
        wireLiveValuation();
    }

    // -------------------------------------------------------------- valuation

    private void wireLiveValuation() {
        valuationDebounce.setOnFinished(event -> refreshEstimate());
        Runnable schedule = valuationDebounce::playFromStart;

        districtCombo.valueProperty().addListener((obs, was, now) -> schedule.run());
        propertyTypeCombo.valueProperty().addListener((obs, was, now) -> schedule.run());
        dealTypeCombo.valueProperty().addListener((obs, was, now) -> schedule.run());
        areaField.textProperty().addListener((obs, was, now) -> schedule.run());
        bedroomsField.textProperty().addListener((obs, was, now) -> schedule.run());
        bathroomsField.textProperty().addListener((obs, was, now) -> schedule.run());
        yearBuiltField.textProperty().addListener((obs, was, now) -> schedule.run());
        floorNumberField.textProperty().addListener((obs, was, now) -> schedule.run());
        askingPriceField.textProperty().addListener((obs, was, now) -> schedule.run());
        parkingCheck.selectedProperty().addListener((obs, was, now) -> schedule.run());
        elevatorCheck.selectedProperty().addListener((obs, was, now) -> schedule.run());
        balconyCheck.selectedProperty().addListener((obs, was, now) -> schedule.run());
        furnishedCheck.selectedProperty().addListener((obs, was, now) -> schedule.run());
    }

    /**
     * Values whatever has been typed so far, without saving anything.
     *
     * <p>The estimate needs a district, a type and an area; until all three are
     * present the panel says so rather than showing a number it cannot justify.
     */
    private void refreshEstimate() {
        Property draft = draftForValuation();
        if (draft == null) {
            showEstimateMessage("Enter a district, property type and area, and an estimate appears here.");
            return;
        }

        Task<ValuationResult> task = new Task<>() {
            @Override
            protected ValuationResult call() throws Exception {
                return valuationService.previewValue(draft);
            }
        };
        task.setOnSucceeded(event -> renderEstimate(task.getValue(), draft));
        task.setOnFailed(event -> {
            Throwable cause = task.getException();
            System.err.println("Aqarat valuation preview failed: "
                + (cause == null ? "unknown" : cause.getClass().getSimpleName() + " - " + cause.getMessage()));
            showEstimateMessage(
                "Not enough comparable properties in this district yet to estimate a price.");
        });

        Thread worker = new Thread(task, "submit-valuation-preview");
        worker.setDaemon(true);
        worker.start();
    }

    private Property draftForValuation() {
        District district = districtCombo.getValue();
        PropertyType type = propertyTypeCombo.getValue();
        BigDecimal area = parseDecimal(areaField.getText());
        if (district == null || type == null || area == null || area.signum() <= 0) {
            return null;
        }

        Property draft = new Property();
        draft.setDistrictId(district.getId());
        draft.setPropertyTypeId(type.getId());
        draft.setAreaSqm(area);
        draft.setDealType(dealTypeCombo.getValue() == null ? DealType.SALE : dealTypeCombo.getValue());
        draft.setBedrooms(parseInt(bedroomsField.getText(), 0));
        draft.setBathrooms(parseInt(bathroomsField.getText(), 0));
        draft.setFloorNumber(parseNullableInt(floorNumberField.getText()));
        draft.setYearBuilt(parseNullableInt(yearBuiltField.getText()));
        draft.setHasParking(parkingCheck.isSelected());
        draft.setHasElevator(elevatorCheck.isSelected());
        draft.setHasBalcony(balconyCheck.isSelected());
        draft.setFurnished(furnishedCheck.isSelected());
        draft.setAskingPrice(parseDecimal(askingPriceField.getText()));
        return draft;
    }

    private void renderEstimate(ValuationResult result, Property draft) {
        estimateValue.setText(Format.salePrice(result.getEstimatedValue()));
        estimateState.setText(Format.pricePerSqm(result.getPricePerSqm()) + " estimated");
        estimateDetail.setVisible(true);
        estimateDetail.setManaged(true);

        renderPlausibility(result, draft.getAskingPrice());
        renderRange(result, draft.getAskingPrice());
        renderFactors(result);
        renderComparables(result);
        AnimationUtil.fadeIn(estimateDetail, 200);
    }

    /* The flag is only meaningful once an asking price has been entered - before
       that there is nothing to judge the estimate against. */
    private void renderPlausibility(ValuationResult result, BigDecimal askingPrice) {
        boolean hasAsk = askingPrice != null && askingPrice.signum() > 0;
        plausibilityPill.setVisible(hasAsk);
        plausibilityPill.setManaged(hasAsk);
        if (!hasAsk) {
            return;
        }

        plausibilityPill.getStyleClass().removeAll(
            "pill-good", "pill-warn", "pill-bad", "pill-info", "pill-neutral");
        switch (result.getFlag()) {
            case ABOVE_MARKET -> {
                plausibilityPill.setText("Above the market rate");
                plausibilityPill.getStyleClass().add("pill-warn");
            }
            case IMPLAUSIBLE -> {
                plausibilityPill.setText("Implausible for this property");
                plausibilityPill.getStyleClass().add("pill-bad");
            }
            default -> {
                plausibilityPill.setText("Your price is plausible");
                plausibilityPill.getStyleClass().add("pill-good");
            }
        }
    }

    /**
     * Draws the confidence range with the asking price marked on it.
     *
     * <p>Two filler regions either side of the marker rather than absolute
     * positioning, so the bar stretches with the panel instead of drifting
     * away from the labels beneath it.
     */
    /**
     * Draws the confidence range with both marks on it.
     *
     * <p>Showing only one of them answers half the question. The point of the
     * range is the distance between what the system thinks and what the owner
     * is asking, so the estimate is a line and the asking price a filled dot,
     * and where they sit relative to each other is the whole message.
     *
     * <p>Built from proportional spacers rather than absolute positions so the
     * bar stretches with the panel instead of drifting from its labels.
     */
    private void renderRange(ValuationResult result, BigDecimal askingPrice) {
        lowerBoundLabel.setText(Format.salePrice(result.getLowerBound()));
        upperBoundLabel.setText(Format.salePrice(result.getUpperBound()));
        rangeTrack.getChildren().clear();

        BigDecimal low = result.getLowerBound();
        BigDecimal high = result.getUpperBound();
        if (low == null || high == null || high.compareTo(low) <= 0) {
            return;
        }

        double estimateAt = positionOf(result.getEstimatedValue(), low, high);
        Node estimateMark = new Region();
        estimateMark.getStyleClass().add("range-estimate-mark");

        boolean hasAsk = askingPrice != null && askingPrice.signum() > 0;
        if (!hasAsk) {
            layOut(new double[] {estimateAt}, new Node[] {estimateMark});
            return;
        }

        double askAt = positionOf(askingPrice, low, high);
        Node askMark = new Region();
        askMark.getStyleClass().add("range-asking-mark");

        // Whichever sits further left is placed first, or the spacers between
        // them come out negative.
        if (askAt < estimateAt) {
            layOut(new double[] {askAt, estimateAt}, new Node[] {askMark, estimateMark});
        } else {
            layOut(new double[] {estimateAt, askAt}, new Node[] {estimateMark, askMark});
        }
    }

    /** Where a value falls across the range, clamped to its ends. */
    private double positionOf(BigDecimal value, BigDecimal low, BigDecimal high) {
        if (value == null) {
            return 0.5;
        }
        double span = high.subtract(low).doubleValue();
        double at = value.subtract(low).doubleValue() / span;
        return Math.max(0, Math.min(1, at));
    }

    private void layOut(double[] positions, Node[] marks) {
        double previous = 0;
        for (int i = 0; i < marks.length; i++) {
            rangeTrack.getChildren().add(spacer(positions[i] - previous));
            rangeTrack.getChildren().add(marks[i]);
            previous = positions[i];
        }
        rangeTrack.getChildren().add(spacer(1 - previous));
    }

    private Region spacer(double share) {
        Region spacer = new Region();
        spacer.setMinWidth(0);
        spacer.setPrefWidth(Math.max(0, share) * 100);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private void renderFactors(ValuationResult result) {
        factorsBox.getChildren().clear();
        Map<String, BigDecimal> factors = result.getFactorContributions();
        if (factors == null || factors.isEmpty()) {
            factorsBox.getChildren().add(mutedLine("Only one method produced a usable number."));
            return;
        }
        factors.entrySet().stream()
            .filter(entry -> entry.getValue() != null && entry.getValue().signum() != 0)
            .forEach(entry -> factorsBox.getChildren().add(factorRow(entry.getKey(), entry.getValue())));
    }

    private HBox factorRow(String name, BigDecimal estimate) {
        Label label = new Label(methodName(name));
        label.getStyleClass().add("hint");
        label.setMinWidth(150);
        label.setPrefWidth(150);
        label.setWrapText(true);

        Label amount = new Label(Format.salePrice(estimate));
        amount.getStyleClass().add("body-medium");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(8, label, spacer, amount);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /* The engine reports each method it tried, not a signed adjustment, so these
       are named as the routes to a number rather than as things that moved it. */
    private String methodName(String key) {
        return switch (key) {
            case "comparablesEstimate" -> "From comparable sales";
            case "regressionEstimate" -> "From the price model";
            case "districtAverageFallback" -> "From the district average";
            default -> humanise(key);
        };
    }

    private void renderComparables(ValuationResult result) {
        comparablesBox.getChildren().clear();
        List<ComparableProperty> comparables = result.getComparables();
        if (comparables == null || comparables.isEmpty()) {
            comparablesHeading.setText("Reasoned from the district average");
            comparablesBox.getChildren().add(mutedLine("No close comparable sales were available."));
            return;
        }

        comparablesHeading.setText("Reasoned from " + comparables.size()
            + (comparables.size() == 1 ? " comparable" : " comparables"));

        int shown = Math.min(3, comparables.size());
        for (int i = 0; i < shown; i++) {
            Property comparable = comparables.get(i).getProperty();
            Label what = new Label(Format.area(comparable.getAreaSqm())
                + " · " + comparable.getBedrooms() + " bed");
            what.getStyleClass().add("hint");

            Label price = new Label(Format.salePrice(comparable.getAskingPrice()));
            price.getStyleClass().add("body-medium");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox row = new HBox(8, what, spacer, price);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("comparable-row");
            comparablesBox.getChildren().add(row);
        }
    }

    private Label mutedLine(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("hint");
        label.setWrapText(true);
        return label;
    }

    private String humanise(String key) {
        String spaced = key.replaceAll("([a-z])([A-Z])", "$1 $2")
            .replace('_', ' ').replace('.', ' ').trim().toLowerCase();
        return spaced.isEmpty() ? key : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private void showEstimateMessage(String message) {
        estimateValue.setText("—");
        estimateState.setText(message);
        estimateDetail.setVisible(false);
        estimateDetail.setManaged(false);
    }

    private BigDecimal parseDecimal(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int parseInt(String text, int fallback) {
        Integer value = parseNullableInt(text);
        return value == null ? fallback : value;
    }

    private Integer parseNullableInt(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }


    private void denyAccess() {
        contentBox.setVisible(false);
        contentBox.setManaged(false);
        accessDeniedLabel.setText("Only customers can submit a property.");
        accessDeniedLabel.setVisible(true);
        accessDeniedLabel.setManaged(true);
    }

    private void loadReferenceData() {
        try {
            districtCombo.setItems(FXCollections.observableArrayList(referenceService.findAllDistricts()));
            propertyTypeCombo.setItems(
                FXCollections.observableArrayList(referenceService.findAllPropertyTypes()));
        } catch (SQLException e) {
            AlertUtil.showError("Could not load reference data. Check that SQL Server is running.");
            return;
        }
        setLabelConverter(districtCombo, District::getName);
        setLabelConverter(propertyTypeCombo, PropertyType::getName);
    }

    private void updateDealTypeUi(DealType dealType) {
        boolean isRent = dealType == DealType.RENT;
        askingPriceLabel.setText(isRent ? "Monthly Rent ($ USD / month)" : "Asking Price ($ USD)");
        termsBox.setVisible(isRent);
        termsBox.setManaged(isRent);
        if (!isRent) {
            minTermField.clear();
            maxTermField.clear();
            FieldError.clear(minTermField, minTermError);
            FieldError.clear(maxTermField, maxTermError);
        }
    }

    @FXML
    private void handleSubmit() {
        clearErrors();
        Property property = new Property();
        boolean basicOk = collectBasicFields(property);
        boolean specOk = collectSpecFields(property);
        boolean dealOk = collectDealFields(property);
        if (!basicOk || !specOk || !dealOk) {
            return;
        }
        property.setOwnerId(SessionManager.getCurrentUser().getId());
        List<NewPhoto> photos = new ArrayList<>();
        for (Path path : selectedPhotos) {
            photos.add(new NewPhoto(path));
        }
        int propertyId;
        try {
            propertyId = propertyService.submit(property, photos);
        } catch (IOException e) {
            AlertUtil.showError("One of the photos could not be read. Check the file exists and is a JPG or PNG, then try again.");
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        if (!storeDocuments(propertyId)) {
            return;
        }
        AlertUtil.showInfo("Submitted for review",
            "An agent checks the details and the valuation before it is listed. You will find it under Portfolio.");
        Router.show(Panel.MY_PROPERTIES);
    }

    /**
     * Picks one ownership document and checks it immediately.
     *
     * <p>Checked here rather than at submit: an owner who has chosen the wrong
     * file should be told while they are still looking at the chooser, not
     * after filling in the rest of the form.
     */
    /*
     * The documents are written after the property, because they need its id.
     * A failure here leaves a submitted property with no evidence attached,
     * which the owner can put right from their portfolio - so it says exactly
     * that rather than pretending the whole submission failed.
     */
    private boolean storeDocuments(int propertyId) {
        if (selectedDocuments.isEmpty()) {
            return true;
        }
        try {
            documentService.upload(propertyId, selectedDocuments, SessionManager.getCurrentUser());
            return true;
        } catch (IOException | SQLException | DocumentService.NotPermittedException e) {
            AlertUtil.showUndone("The property was submitted, but its documents were not attached.",
                "Open it under Portfolio and attach them there.");
            Router.show(Panel.MY_PROPERTIES);
            return false;
        }
    }

    @FXML
    private void handleAddDocument() {
        FieldError.clear(documentTypeCombo, documentError);
        DocumentType type = documentTypeCombo.getValue();
        if (type == null) {
            FieldError.show(documentTypeCombo, documentError, "Say what the document is first.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a document");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
            "Documents (*.pdf, *.jpg, *.jpeg, *.png)", "*.pdf", "*.jpg", "*.jpeg", "*.png"));
        File chosen = chooser.showOpenDialog(null);
        if (chosen == null) {
            return;
        }
        try {
            DocumentStore.validate(chosen.toPath());
        } catch (IOException e) {
            FieldError.show(documentTypeCombo, documentError, e.getMessage());
            return;
        }
        selectedDocuments.add(new NewDocument(chosen.toPath(), type));
        refreshDocumentChips();
    }

    private void refreshDocumentChips() {
        if (documentsContainer == null) {
            return;
        }
        documentsContainer.getChildren().clear();
        documentCountBadge.setText(selectedDocuments.size()
            + (selectedDocuments.size() == 1 ? " document" : " documents"));
        documentListLabel.setText(selectedDocuments.isEmpty()
            ? "Nothing attached yet. A listing cannot go live until staff verify one."
            : "Private to you and Aqarat staff.");

        for (int i = 0; i < selectedDocuments.size(); i++) {
            NewDocument document = selectedDocuments.get(i);
            final int index = i;

            Label name = new Label(Format.enumLabel(document.getDocType()) + " · "
                + document.getPath().getFileName());
            name.getStyleClass().add("hint");

            Button remove = new Button("×");
            remove.getStyleClass().addAll("button", "button-ghost", "button-compact");
            remove.setOnAction(event -> {
                selectedDocuments.remove(index);
                refreshDocumentChips();
            });

            HBox chip = new HBox(6, name, remove);
            chip.setAlignment(Pos.CENTER_LEFT);
            chip.getStyleClass().add("spec-chip");
            documentsContainer.getChildren().add(chip);
        }
    }

    @FXML
    private void handleAddPhotos() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose property photos");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Images (*.jpg, *.jpeg, *.png)", "*.jpg", "*.jpeg", "*.png"));
        List<File> chosen = chooser.showOpenMultipleDialog(null);
        if (chosen == null || chosen.isEmpty()) {
            return;
        }

        for (File file : chosen) {
            if (selectedPhotos.size() < 8) {
                selectedPhotos.add(file.toPath());
            }
        }
        refreshPhotoChips();
    }

    private void refreshPhotoChips() {
        if (photosContainer == null) return;
        photosContainer.getChildren().clear();
        photoCountBadge.setText(selectedPhotos.size() + " / 8 photos");

        if (selectedPhotos.isEmpty()) {
            photoListLabel.setText("No photos chosen yet. High-quality photos increase buyer inquiries by 3x.");
            return;
        }
        photoListLabel.setText(selectedPhotos.size() + " photo(s) selected ready to upload.");

        for (int i = 0; i < selectedPhotos.size(); i++) {
            Path path = selectedPhotos.get(i);
            final int index = i;

            HBox chip = new HBox(6);
            chip.setAlignment(Pos.CENTER_LEFT);
            chip.setStyle("-fx-background-color: -c-surface-subtle; -fx-padding: 4 8 4 8; -fx-background-radius: 8px; -fx-border-color: -c-border-subtle; -fx-border-radius: 8px;");

            try {
                ImageView thumb = new ImageView(new Image(path.toUri().toString(), 36, 28, false, true));
                Rectangle clip = new Rectangle(36, 28);
                clip.setArcWidth(6);
                clip.setArcHeight(6);
                thumb.setClip(clip);
                chip.getChildren().add(thumb);
            } catch (Exception ignored) {
            }

            Label nameLabel = new Label(path.getFileName().toString());
            nameLabel.getStyleClass().add("hint");
            nameLabel.setStyle("-fx-font-weight: 500; -fx-max-width: 140px;");

            Button removeBtn = new Button("×");
            removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: -c-bad; -fx-font-weight: bold; -fx-padding: 0 4 0 4; -fx-cursor: hand;");
            removeBtn.setOnAction(e -> {
                selectedPhotos.remove(index);
                refreshPhotoChips();
            });

            chip.getChildren().addAll(nameLabel, removeBtn);
            AnimationUtil.addHoverLift(chip);
            photosContainer.getChildren().add(chip);
        }
    }

    private boolean collectBasicFields(Property property) {
        String title = requireText(titleField, titleError, "Enter a title.");
        District district = requireSelection(districtCombo, districtError, "Choose a district.");
        PropertyType propertyType =
            requireSelection(propertyTypeCombo, propertyTypeError, "Choose a property type.");
        property.setTitle(title);
        String description = descriptionArea.getText().trim();
        property.setDescription(description.isEmpty() ? null : description);
        String addressLine = addressLineField.getText().trim();
        property.setAddressLine(addressLine.isEmpty() ? null : addressLine);
        if (district != null) {
            property.setDistrictId(district.getId());
        }
        if (propertyType != null) {
            property.setPropertyTypeId(propertyType.getId());
        }
        return isValid(titleError) && isValid(districtError) && isValid(propertyTypeError);
    }

    private boolean collectSpecFields(Property property) {
        BigDecimal area = requirePositiveDecimal(areaField, areaError, "Enter the area in square metres.");
        property.setAreaSqm(area);
        property.setBedrooms(parseCount(bedroomsField, bedroomsError));
        property.setBathrooms(parseCount(bathroomsField, bathroomsError));
        property.setFloorNumber(parseOptionalInt(floorNumberField, floorNumberError));
        property.setTotalFloors(parseOptionalInt(totalFloorsField, totalFloorsError));
        property.setYearBuilt(parseOptionalInt(yearBuiltField, yearBuiltError));
        property.setHasParking(parkingCheck.isSelected());
        property.setHasElevator(elevatorCheck.isSelected());
        property.setHasBalcony(balconyCheck.isSelected());
        property.setFurnished(furnishedCheck.isSelected());
        return isValid(areaError) && isValid(bedroomsError) && isValid(bathroomsError)
            && isValid(floorNumberError) && isValid(totalFloorsError) && isValid(yearBuiltError);
    }

    private boolean collectDealFields(Property property) {
        DealType dealType = dealTypeCombo.getValue();
        property.setDealType(dealType);
        String priceMessage = dealType == DealType.RENT
            ? "Enter the monthly rent." : "Enter the asking price.";
        property.setAskingPrice(requirePositiveDecimal(askingPriceField, askingPriceError, priceMessage));

        Integer minTerm = null;
        Integer maxTerm = null;
        if (dealType == DealType.RENT) {
            minTerm = parseOptionalInt(minTermField, minTermError);
            maxTerm = parseOptionalInt(maxTermField, maxTermError);
            if (minTerm != null && maxTerm != null && maxTerm < minTerm) {
                FieldError.show(maxTermField, maxTermError, "Maximum term cannot be less than the minimum term.");
            }
        }
        property.setMinTermMonths(minTerm);
        property.setMaxTermMonths(maxTerm);
        return isValid(askingPriceError) && isValid(minTermError) && isValid(maxTermError);
    }

    private String requireText(TextField field, Label errorLabel, String message) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            FieldError.show(field, errorLabel, message);
            return null;
        }
        return text;
    }

    private <T> T requireSelection(ComboBox<T> combo, Label errorLabel, String message) {
        T value = combo.getValue();
        if (value == null) {
            FieldError.show(combo, errorLabel, message);
        }
        return value;
    }

    private BigDecimal requirePositiveDecimal(TextField field, Label errorLabel, String emptyMessage) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            FieldError.show(field, errorLabel, emptyMessage);
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(text);
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                FieldError.show(field, errorLabel, "Enter a number greater than zero.");
                return null;
            }
            return value.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            FieldError.show(field, errorLabel, "Enter a valid number.");
            return null;
        }
    }

    private int parseCount(TextField field, Label errorLabel) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            return 0;
        }
        try {
            int value = Integer.parseInt(text);
            if (value < 0) {
                FieldError.show(field, errorLabel, "Enter a positive whole number.");
                return 0;
            }
            return value;
        } catch (NumberFormatException e) {
            FieldError.show(field, errorLabel, "Enter a whole number.");
            return 0;
        }
    }

    private Integer parseOptionalInt(TextField field, Label errorLabel) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            FieldError.show(field, errorLabel, "Enter a whole number.");
            return null;
        }
    }

    private <T> void setLabelConverter(ComboBox<T> combo, Function<T, String> labelFunction) {
        combo.setConverter(new StringConverter<T>() {
            @Override
            public String toString(T item) {
                return item == null ? "" : labelFunction.apply(item);
            }

            @Override
            public T fromString(String text) {
                return null;
            }
        });
    }

    private boolean isValid(Label errorLabel) {
        return !FieldError.isShown(errorLabel);
    }

    private void clearErrors() {
        FieldError.clear(titleField, titleError);
        FieldError.clear(districtCombo, districtError);
        FieldError.clear(propertyTypeCombo, propertyTypeError);
        FieldError.clear(areaField, areaError);
        FieldError.clear(bedroomsField, bedroomsError);
        FieldError.clear(bathroomsField, bathroomsError);
        FieldError.clear(floorNumberField, floorNumberError);
        FieldError.clear(totalFloorsField, totalFloorsError);
        FieldError.clear(yearBuiltField, yearBuiltError);
        FieldError.clear(askingPriceField, askingPriceError);
        FieldError.clear(minTermField, minTermError);
        FieldError.clear(maxTermField, maxTermError);
    }
}
