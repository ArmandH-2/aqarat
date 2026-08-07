package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.dao.AuditDao;
import co.syntropyhq.aqarat.dao.DistrictDao;
import co.syntropyhq.aqarat.dao.PropertyDao;
import co.syntropyhq.aqarat.dao.PropertyMessageDao;
import co.syntropyhq.aqarat.dao.PropertyPhotoDao;
import co.syntropyhq.aqarat.dao.PropertyTypeDao;
import co.syntropyhq.aqarat.dao.SystemSettingDao;
import co.syntropyhq.aqarat.dao.ValuationDao;
import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.DealType;
import co.syntropyhq.aqarat.model.District;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.PropertyMessage;
import co.syntropyhq.aqarat.model.PropertyPhoto;
import co.syntropyhq.aqarat.model.PropertyStatus;
import co.syntropyhq.aqarat.model.PropertyType;
import co.syntropyhq.aqarat.model.Role;
import co.syntropyhq.aqarat.model.Valuation;
import co.syntropyhq.aqarat.model.ValuationFlag;
import co.syntropyhq.aqarat.service.AuditService;
import co.syntropyhq.aqarat.service.PropertyService;
import co.syntropyhq.aqarat.service.ReferenceService;
import co.syntropyhq.aqarat.service.ValuationService;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.FieldError;
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.NeedsId;
import co.syntropyhq.aqarat.util.Panel;
import co.syntropyhq.aqarat.util.Router;
import co.syntropyhq.aqarat.util.SessionManager;
import co.syntropyhq.aqarat.valuation.ComparableProperty;
import co.syntropyhq.aqarat.valuation.ValuationResult;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

// The centrepiece screen (DESIGN.md section 9): the submission beside its
// valuation, and the decision that follows from looking at both together.
// ValuationService returns the same ValuationResult whether an estimate was
// just computed or read back from an earlier run, so there is one rendering
// path rather than two.
public class ReviewSubmissionController implements NeedsId {

    private static final String IMAGE_ROOT = "uploads";

    @FXML
    private Label accessDeniedLabel;
    @FXML
    private VBox contentBox;
    @FXML
    private Label titleLabel;
    @FXML
    private Label statusPill;
    @FXML
    private HBox galleryBox;
    @FXML
    private Label districtValue;
    @FXML
    private Label typeValue;
    @FXML
    private Label dealTypeValue;
    @FXML
    private Label areaValue;
    @FXML
    private Label bedroomsValue;
    @FXML
    private Label bathroomsValue;
    @FXML
    private Label floorValue;
    @FXML
    private Label yearBuiltValue;
    @FXML
    private Label parkingValue;
    @FXML
    private Label elevatorValue;
    @FXML
    private Label balconyValue;
    @FXML
    private Label furnishedValue;
    @FXML
    private Label addressValue;
    @FXML
    private Label descriptionValue;

    @FXML
    private Label askingPriceCaption;
    @FXML
    private Label askingPriceValue;
    @FXML
    private Label noValuationLabel;
    @FXML
    private VBox valuationContent;
    @FXML
    private Label estimateValue;
    @FXML
    private Label flagPill;
    @FXML
    private Label flagCaption;
    @FXML
    private Label rangeValue;
    @FXML
    private Label pricePerSqmValue;
    @FXML
    private Label valuationMetaLabel;
    @FXML
    private Label evidenceLabel;
    @FXML
    private VBox factorsBox;
    @FXML
    private VBox comparablesBox;

    @FXML
    private VBox decisionBox;
    @FXML
    private VBox discussionBox;
    @FXML
    private Label decisionHintLabel;
    @FXML
    private VBox withdrawalReasonBox;
    @FXML
    private Label withdrawalReasonValue;
    @FXML
    private VBox noteBox;
    @FXML
    private TextArea noteArea;
    @FXML
    private Label noteError;
    @FXML
    private HBox reviewActionsBox;
    @FXML
    private HBox withdrawalActionsBox;

    private final PropertyService propertyService =
        new PropertyService(new PropertyDao(), new PropertyPhotoDao(), new PropertyMessageDao(), new AuditService(new AuditDao()));
    private final ReferenceService referenceService =
        new ReferenceService(new DistrictDao(), new PropertyTypeDao());
    private final ValuationService valuationService = new ValuationService(
        new PropertyDao(), new ValuationDao(), new SystemSettingDao(), new DistrictDao());

    private int propertyId;
    private Property property;
    private boolean accessGranted;

    @FXML
    private void initialize() {
        AppUser user = SessionManager.getCurrentUser();
        accessGranted = user != null && (user.getRole() == Role.AGENT || user.getRole() == Role.ADMIN);
        if (!accessGranted) {
            denyAccess();
        }
    }

    private void denyAccess() {
        contentBox.setVisible(false);
        contentBox.setManaged(false);
        accessDeniedLabel.setText("You do not have access to review submissions.");
        accessDeniedLabel.setVisible(true);
        accessDeniedLabel.setManaged(true);
    }

    @Override
    public void receiveId(int id) {
        if (!accessGranted) {
            return;
        }
        propertyId = id;
        loadProperty();
    }

    private void loadProperty() {
        try {
            property = propertyService.findById(propertyId);
        } catch (SQLException e) {
            AlertUtil.showError("Could not load this submission. Check that SQL Server is running.");
            return;
        }
        if (property == null) {
            AlertUtil.showError("This submission no longer exists.");
            return;
        }
        renderProperty();
        renderGallery();
        renderDecisionSection();
        renderDiscussion();
        loadValuation();
    }

    private void renderProperty() {
        titleLabel.setText(property.getTitle());
        applyStatusPill(property.getStatus());
        descriptionValue.setText(property.getDescription() == null ? "" : property.getDescription());
        renderSpecs();
        renderFeatures();
        renderAskingPrice();
    }

    private void renderSpecs() {
        districtValue.setText(lookupDistrictName(property.getDistrictId()));
        typeValue.setText(lookupTypeName(property.getPropertyTypeId()));
        dealTypeValue.setText(Format.enumLabel(property.getDealType()));
        areaValue.setText(Format.area(property.getAreaSqm()));
        bedroomsValue.setText(String.valueOf(property.getBedrooms()));
        bathroomsValue.setText(String.valueOf(property.getBathrooms()));
        floorValue.setText(formatNullableInt(property.getFloorNumber()));
        yearBuiltValue.setText(formatNullableInt(property.getYearBuilt()));
        addressValue.setText(property.getAddressLine() == null ? "" : property.getAddressLine());
    }

    private void renderFeatures() {
        parkingValue.setText(property.isHasParking() ? "Yes" : "No");
        elevatorValue.setText(property.isHasElevator() ? "Yes" : "No");
        balconyValue.setText(property.isHasBalcony() ? "Yes" : "No");
        furnishedValue.setText(property.isFurnished() ? "Yes" : "No");
    }

    private void renderAskingPrice() {
        boolean isRent = property.getDealType() == DealType.RENT;
        askingPriceCaption.setText(isRent ? "Monthly rent" : "Asking price");
        askingPriceValue.setText(formatMoney(property.getAskingPrice()));
    }

    private String formatNullableInt(Integer value) {
        return value == null ? "—" : String.valueOf(value);
    }

    private String lookupDistrictName(int districtId) {
        try {
            District district = referenceService.findDistrict(districtId);
            return district == null ? "—" : district.getName();
        } catch (SQLException e) {
            return "—";
        }
    }

    private String lookupTypeName(int propertyTypeId) {
        try {
            PropertyType type = referenceService.findPropertyType(propertyTypeId);
            return type == null ? "—" : type.getName();
        } catch (SQLException e) {
            return "—";
        }
    }

    private void applyStatusPill(PropertyStatus status) {
        statusPill.setText(Format.enumLabel(status));
        statusPill.getStyleClass().removeAll(
            "pill-good", "pill-warn", "pill-bad", "pill-info", "pill-neutral");
        statusPill.getStyleClass().add(statusPillClass(status));
    }

    private String statusPillClass(PropertyStatus status) {
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

    // Matches PropertyDetailsController's approach: the seeded photo rows
    // point at a file that was never generated, so a tile falls back to a
    // caption instead of showing a broken image.
    private void renderGallery() {
        List<PropertyPhoto> photos;
        try {
            photos = propertyService.findPhotos(propertyId);
        } catch (SQLException e) {
            AlertUtil.showError("Could not load the photos for this submission.");
            return;
        }
        galleryBox.getChildren().clear();
        if (photos.isEmpty()) {
            Label empty = new Label("No photos have been added to this submission.");
            empty.getStyleClass().add("empty-state");
            galleryBox.getChildren().add(empty);
            return;
        }
        for (PropertyPhoto photo : photos) {
            galleryBox.getChildren().add(buildPhotoTile(photo));
        }
    }

    private Node buildPhotoTile(PropertyPhoto photo) {
        Path path = Path.of(IMAGE_ROOT, photo.getFilePath());
        if (Files.exists(path)) {
            ImageView view = new ImageView(new Image(path.toUri().toString()));
            view.setFitWidth(220);
            view.setPreserveRatio(true);
            return view;
        }
        Label missing = new Label("Photo not available");
        missing.getStyleClass().add("empty-state");
        StackPane tile = new StackPane(missing);
        tile.getStyleClass().add("card");
        tile.setPrefSize(220, 150);
        return tile;
    }

    // Decisions apply to a submission waiting on one (PENDING_REVIEW) and to
    // an owner's request to take a live listing down (WITHDRAWAL_REQUESTED,
    // DESIGN.md section 6). A NEEDS_INFO submission is back with the owner
    // until they resend it, so it gets neither button row.
    private void renderDecisionSection() {
        PropertyStatus status = property.getStatus();
        boolean isPendingReview = status == PropertyStatus.PENDING_REVIEW;
        boolean isWithdrawalRequest = status == PropertyStatus.WITHDRAWAL_REQUESTED;
        boolean canDecide = isPendingReview || isWithdrawalRequest;

        decisionBox.setVisible(canDecide);
        decisionBox.setManaged(canDecide);
        decisionHintLabel.setVisible(!canDecide);
        decisionHintLabel.setManaged(!canDecide);
        if (!canDecide) {
            decisionHintLabel.setText(
                "Current status: " + Format.enumLabel(status) + ". No decision is needed right now.");
            return;
        }
        showDecisionActions(isPendingReview, isWithdrawalRequest);
    }

    private void showDecisionActions(boolean isPendingReview, boolean isWithdrawalRequest) {
        reviewActionsBox.setVisible(isPendingReview);
        reviewActionsBox.setManaged(isPendingReview);
        noteBox.setVisible(isPendingReview);
        noteBox.setManaged(isPendingReview);

        withdrawalActionsBox.setVisible(isWithdrawalRequest);
        withdrawalActionsBox.setManaged(isWithdrawalRequest);
        withdrawalReasonBox.setVisible(isWithdrawalRequest);
        withdrawalReasonBox.setManaged(isWithdrawalRequest);
        if (isWithdrawalRequest) {
            withdrawalReasonValue.setText(
                property.getReviewNote() == null ? "No reason was given." : property.getReviewNote());
        }
    }

    // The owner's answers to previous decisions, oldest first. The agent's
    // own notes are in here too (PropertyService.review writes them), so the
    // agent reads the whole conversation rather than only the newest reply.
    private void renderDiscussion() {
        List<PropertyMessage> messages;
        try {
            messages = propertyService.findMessages(propertyId);
        } catch (SQLException e) {
            AlertUtil.showError("Could not load the discussion for this submission.");
            return;
        }
        discussionBox.getChildren().clear();
        if (messages.isEmpty()) {
            Label empty = new Label("No messages in this discussion yet.");
            empty.getStyleClass().add("empty-state");
            discussionBox.getChildren().add(empty);
            return;
        }
        for (PropertyMessage message : messages) {
            Label senderLine = new Label(
                message.getAuthorName() + " • " + Format.dateTime(message.getCreatedAt()));
            senderLine.getStyleClass().add("hint");
            Label body = new Label(message.getMessage());
            body.setWrapText(true);
            discussionBox.getChildren().add(new VBox(2, senderLine, body));
        }
    }

    private void loadValuation() {
        try {
            Valuation saved = valuationService.findLatest(propertyId);
            if (saved == null) {
                showNoValuation();
                return;
            }
            renderValuation(saved, valuationService.findLatestResult(propertyId));
        } catch (SQLException e) {
            AlertUtil.showError("Could not load the valuation. Check that SQL Server is running.");
        }
    }

    private void showNoValuation() {
        noValuationLabel.setText("This property has not been valued yet. Run a valuation to see an estimate.");
        noValuationLabel.setVisible(true);
        noValuationLabel.setManaged(true);
        valuationContent.setVisible(false);
        valuationContent.setManaged(false);
    }

    // The saved row carries when the estimate was made and by which model
    // version; the result carries the numbers and the evidence behind them.
    private void renderValuation(Valuation saved, ValuationResult result) {
        noValuationLabel.setVisible(false);
        noValuationLabel.setManaged(false);
        valuationContent.setVisible(true);
        valuationContent.setManaged(true);

        estimateValue.setText(formatMoney(result.getEstimatedValue()));
        rangeValue.setText(formatMoney(result.getLowerBound()) + " to " + formatMoney(result.getUpperBound()));
        pricePerSqmValue.setText(Format.pricePerSqm(result.getPricePerSqm()));
        valuationMetaLabel.setText(
            "Model " + saved.getModelVersion() + " • " + Format.dateTime(saved.getCreatedAt()));
        applyFlagPill(result.getFlag());
        applyRangeCaption(result.getLowerBound(), result.getUpperBound());
        applyEvidenceLine(result.getComparables().size());
        renderFactors(result.getFactorContributions());
        renderComparables(result.getComparables());
    }

    // Every one of the twenty worst estimates measured against the closed
    // properties had fewer than five comparables, and most had none. The
    // number is the single best clue to how much the estimate is worth, so it
    // is said out loud next to the price rather than left to be counted.
    private void applyEvidenceLine(int comparableCount) {
        if (comparableCount == 0) {
            evidenceLabel.setText(
                "No comparable sales were found, so this rests on the model alone. Treat it as a rough guide.");
            return;
        }
        evidenceLabel.setText(comparableCount == 1
            ? "Based on 1 comparable sale."
            : "Based on " + comparableCount + " comparable sales.");
    }

    // The flag is advisory only (DESIGN.md section 7) - it gets a loud pill
    // and nothing else. Nothing here disables or gates the Approve action.
    private void applyFlagPill(ValuationFlag flag) {
        flagPill.setText(Format.enumLabel(flag));
        flagPill.getStyleClass().removeAll("pill-good", "pill-warn", "pill-bad", "pill-info", "pill-neutral");
        flagPill.getStyleClass().add(flagPillClass(flag));
    }

    // ValuationFlag has no separate value for "below the range" (DESIGN.md
    // section 7 uses ABOVE_MARKET for both directions), so this reads the
    // same bounds the range line already shows rather than adding one. Text
    // only - it feeds no decision and the flag itself is unchanged.
    private void applyRangeCaption(BigDecimal lowerBound, BigDecimal upperBound) {
        BigDecimal askingPrice = property.getAskingPrice();
        if (askingPrice.compareTo(upperBound) > 0) {
            flagCaption.setText("Above the estimated range");
        } else if (askingPrice.compareTo(lowerBound) < 0) {
            flagCaption.setText("Below the estimated range");
        } else {
            flagCaption.setText("Within the estimated range");
        }
    }

    private String flagPillClass(ValuationFlag flag) {
        switch (flag) {
            case OK:
                return "pill-good";
            case ABOVE_MARKET:
                return "pill-warn";
            default:
                return "pill-bad";
        }
    }

    private void renderFactors(Map<String, BigDecimal> factors) {
        factorsBox.getChildren().clear();
        if (factors.isEmpty()) {
            Label empty = new Label("No factor breakdown was recorded for this estimate.");
            empty.getStyleClass().add("empty-state");
            factorsBox.getChildren().add(empty);
            return;
        }
        for (Map.Entry<String, BigDecimal> factor : factors.entrySet()) {
            factorsBox.getChildren().add(buildFactorRow(factor.getKey(), factor.getValue()));
        }
    }

    private HBox buildFactorRow(String key, BigDecimal value) {
        Label label = new Label(sentenceCase(key));
        Label amount = new Label(formatMoney(value));
        amount.getStyleClass().add("numeric");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return new HBox(8, label, spacer, amount);
    }

    // "comparablesEstimate" -> "Comparables estimate". The factor names come
    // from PriceEstimator and are camelCase for the code that reads them, not
    // for a person, so this is the one place they get turned into words.
    private String sentenceCase(String camelCase) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (i == 0) {
                text.append(Character.toUpperCase(c));
            } else if (Character.isUpperCase(c)) {
                text.append(' ').append(Character.toLowerCase(c));
            } else {
                text.append(c);
            }
        }
        return text.toString();
    }

    private void renderComparables(List<ComparableProperty> comparables) {
        comparablesBox.getChildren().clear();
        if (comparables.isEmpty()) {
            Label empty = new Label("No comparable properties were used for this estimate.");
            empty.getStyleClass().add("empty-state");
            comparablesBox.getChildren().add(empty);
            return;
        }
        for (ComparableProperty comparable : comparables) {
            comparablesBox.getChildren().add(buildComparableRow(comparable));
        }
    }

    private VBox buildComparableRow(ComparableProperty comparable) {
        Property comparableProperty = comparable.getProperty();
        Label title = new Label(comparableLabel(comparableProperty));
        Label meta = new Label(Format.area(comparableProperty.getAreaSqm()) + " • "
            + formatMoney(comparableProperty.getAskingPrice()) + " • "
            + Format.percentage(similarityPercent(comparable.getSimilarityScore())) + " similar");
        meta.getStyleClass().add("label-soft");
        return new VBox(4, title, meta);
    }

    private String comparableLabel(Property comparableProperty) {
        return comparableProperty.getAddressLine() != null
            ? comparableProperty.getAddressLine() : comparableProperty.getTitle();
    }

    private BigDecimal similarityPercent(BigDecimal similarityScore) {
        return similarityScore.multiply(BigDecimal.valueOf(100));
    }

    // estimatedValue, its bounds and each factor all carry the same dual
    // meaning as asking_price (DESIGN.md section 8): a sale price for SALE,
    // a monthly rent for RENT, because they were built from comparables of
    // the same deal type. Formatted the same way asking price is.
    private String formatMoney(BigDecimal amount) {
        return property.getDealType() == DealType.RENT
            ? Format.monthlyRent(amount) : Format.salePrice(amount);
    }

    @FXML
    private void handleRunValuation() {
        try {
            valuationService.valueProperty(propertyId);
        } catch (ValuationService.CannotValueException e) {
            AlertUtil.showError("Not enough comparable or historical data to value this property yet.");
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        loadValuation();
    }

    @FXML
    private void handleApprove() {
        submitDecision(PropertyStatus.AVAILABLE, null);
    }

    @FXML
    private void handleRequestInfo() {
        String note = requireNote("Explain what information the owner needs to provide.");
        if (note != null) {
            submitDecision(PropertyStatus.NEEDS_INFO, note);
        }
    }

    @FXML
    private void handleReject() {
        String note = requireNote("Explain why this submission is being rejected.");
        if (note == null) {
            return;
        }
        if (AlertUtil.confirm("Reject this submission? The owner will see your reason.")) {
            submitDecision(PropertyStatus.REJECTED, note);
        }
    }

    @FXML
    private void handleAcceptRemoval() {
        if (AlertUtil.confirm("Accept this removal? The listing will come off the market.")) {
            submitDecision(PropertyStatus.WITHDRAWN, null);
        }
    }

    @FXML
    private void handleDeclineRemoval() {
        submitDecision(PropertyStatus.AVAILABLE, null);
    }

    private String requireNote(String message) {
        FieldError.clear(noteArea, noteError);
        String note = noteArea.getText().trim();
        if (note.isEmpty()) {
            FieldError.show(noteArea, noteError, message);
            return null;
        }
        return note;
    }

    private void submitDecision(PropertyStatus decision, String note) {
        try {
            propertyService.review(propertyId, decision, note);
        } catch (PropertyService.InvalidTransitionException e) {
            AlertUtil.showError("This submission can no longer be decided on.");
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("Your decision has been recorded.");
        Router.show(Panel.REVIEW_QUEUE);
    }

    @FXML
    private void handleBack() {
        Router.back();
    }
}
