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
import co.syntropyhq.aqarat.util.AnimationUtil;
import co.syntropyhq.aqarat.util.FieldError;
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.NeedsId;
import co.syntropyhq.aqarat.util.Panel;
import co.syntropyhq.aqarat.util.Router;
import co.syntropyhq.aqarat.util.SessionManager;
import co.syntropyhq.aqarat.util.UIHelper;
import co.syntropyhq.aqarat.valuation.ComparableProperty;
import co.syntropyhq.aqarat.valuation.ValuationResult;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

public class ReviewSubmissionController implements NeedsId {

    private static final String IMAGE_ROOT = "uploads";

    @FXML
    private Label accessDeniedLabel;
    @FXML
    private VBox contentBox;
    @FXML
    private Label titleLabel;
    @FXML
    private Label districtSubtitle;
    @FXML
    private Label statusPill;
    @FXML
    private ImageView mainImageView;
    @FXML
    private HBox thumbnailStrip;
    @FXML
    private FlowPane galleryBox;
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
    private FlowPane amenitiesPane;
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

        String districtName = lookupDistrictName(property.getDistrictId());
        String typeName = lookupTypeName(property.getPropertyTypeId());
        districtSubtitle.setText(districtName + " · " + typeName + " · " + Format.enumLabel(property.getDealType()));

        descriptionValue.setText(property.getDescription() == null || property.getDescription().isBlank()
            ? "No detailed description provided." : property.getDescription());

        renderSpecs(districtName, typeName);
        renderAmenities();
        renderAskingPrice();
    }

    private void renderSpecs(String districtName, String typeName) {
        districtValue.setText(districtName);
        typeValue.setText(typeName);
        dealTypeValue.setText(Format.enumLabel(property.getDealType()));
        areaValue.setText(Format.area(property.getAreaSqm()));
        bedroomsValue.setText(String.valueOf(property.getBedrooms()));
        bathroomsValue.setText(String.valueOf(property.getBathrooms()));
        floorValue.setText(formatNullableInt(property.getFloorNumber()));
        yearBuiltValue.setText(formatNullableInt(property.getYearBuilt()));
        addressValue.setText(property.getAddressLine() == null || property.getAddressLine().isBlank()
            ? "No exact street address provided" : property.getAddressLine());
    }

    private void renderAmenities() {
        if (amenitiesPane == null) return;
        amenitiesPane.getChildren().clear();
        addAmenityChip("Dedicated Parking", property.isHasParking());
        addAmenityChip("Lift", property.isHasElevator());
        addAmenityChip("Balcony", property.isHasBalcony());
        addAmenityChip("Furnished", property.isFurnished());
    }

    /* Same chips the owner sees on the listing: a feature the property lacks is
       stated in words and set back, rather than marked with a tick or a cross. */
    private void addAmenityChip(String name, boolean active) {
        Label chip = new Label(active ? name : "No " + name.toLowerCase());
        chip.getStyleClass().add(active ? "amenity-chip" : "amenity-chip-absent");
        amenitiesPane.getChildren().add(chip);
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

    private void renderGallery() {
        List<PropertyPhoto> photos;
        try {
            photos = propertyService.findPhotos(propertyId);
        } catch (SQLException e) {
            AlertUtil.showError("Could not load photos for this submission.");
            return;
        }

        thumbnailStrip.getChildren().clear();

        if (photos.isEmpty()) {
            mainImageView.setImage(null);
            return;
        }

        setMainPhoto(photos.get(0));

        for (PropertyPhoto photo : photos) {
            StackPane thumbContainer = new StackPane();
            thumbContainer.setPrefSize(64, 46);
            thumbContainer.setMinSize(64, 46);
            thumbContainer.setMaxSize(64, 46);
            thumbContainer.setStyle("-fx-background-color: -c-surface-subtle; -fx-background-radius: 6px; -fx-border-color: -c-border-subtle; -fx-border-radius: 6px;");
            thumbContainer.setCursor(Cursor.HAND);

            Path path = Path.of(IMAGE_ROOT, photo.getFilePath());
            if (Files.exists(path)) {
                ImageView thumbView = new ImageView(new Image(path.toUri().toString(), 64, 46, false, true));
                Rectangle clip = new Rectangle(64, 46);
                clip.setArcWidth(8);
                clip.setArcHeight(8);
                thumbView.setClip(clip);
                thumbContainer.getChildren().add(thumbView);
            } else {
                Label placeholder = new Label("🖼️");
                thumbContainer.getChildren().add(placeholder);
            }

            thumbContainer.setOnMouseClicked(e -> setMainPhoto(photo));
            AnimationUtil.addHoverLift(thumbContainer);
            thumbnailStrip.getChildren().add(thumbContainer);
        }
    }

    private void setMainPhoto(PropertyPhoto photo) {
        Path path = Path.of(IMAGE_ROOT, photo.getFilePath());
        if (Files.exists(path)) {
            Image img = new Image(path.toUri().toString());
            mainImageView.setImage(img);
            AnimationUtil.fadeIn(mainImageView, 200);
        }
    }

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

    private void renderDiscussion() {
        List<PropertyMessage> messages;
        try {
            messages = propertyService.findMessages(propertyId);
        } catch (SQLException e) {
            AlertUtil.showError("Could not load discussion for this submission.");
            return;
        }
        discussionBox.getChildren().clear();
        if (messages.isEmpty()) {
            Label empty = new Label("No discussion messages yet.");
            empty.getStyleClass().add("hint");
            discussionBox.getChildren().add(empty);
            return;
        }
        for (PropertyMessage message : messages) {
            Label senderLine = new Label(
                message.getAuthorName() + " · " + Format.dateTime(message.getCreatedAt()));
            senderLine.getStyleClass().add("hint");
            Label body = new Label(message.getMessage());
            body.setWrapText(true);
            body.getStyleClass().add("body");
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
        noValuationLabel.setText("This property has not been valued yet. Click 'Re-run' to execute price estimation engine.");
        noValuationLabel.setVisible(true);
        noValuationLabel.setManaged(true);
        valuationContent.setVisible(false);
        valuationContent.setManaged(false);
    }

    private void renderValuation(Valuation saved, ValuationResult result) {
        noValuationLabel.setVisible(false);
        noValuationLabel.setManaged(false);
        valuationContent.setVisible(true);
        valuationContent.setManaged(true);

        estimateValue.setText(formatMoney(result.getEstimatedValue()));
        rangeValue.setText(formatMoney(result.getLowerBound()) + " – " + formatMoney(result.getUpperBound()));
        pricePerSqmValue.setText(Format.pricePerSqm(result.getPricePerSqm()));
        valuationMetaLabel.setText(
            "Model " + saved.getModelVersion() + " · " + Format.dateTime(saved.getCreatedAt()));
        applyFlagPill(result.getFlag());
        applyRangeCaption(result.getLowerBound(), result.getUpperBound());
        applyEvidenceLine(result.getComparables().size());
        renderFactors(result.getFactorContributions());
        renderComparables(result.getComparables());
    }

    private void applyEvidenceLine(int comparableCount) {
        if (comparableCount == 0) {
            evidenceLabel.setText("Based on algorithmic base model (no recent matching comparables in district).");
            return;
        }
        evidenceLabel.setText("Calculated against " + comparableCount + " verified comparable sale(s) in this district.");
    }

    private void applyFlagPill(ValuationFlag flag) {
        flagPill.setText(flagWording(flag));
        flagPill.getStyleClass().removeAll("pill-good", "pill-warn", "pill-bad", "pill-info", "pill-neutral");
        flagPill.getStyleClass().add(flagPillClass(flag));
    }

    private void applyRangeCaption(BigDecimal lowerBound, BigDecimal upperBound) {
        BigDecimal askingPrice = property.getAskingPrice();
        if (askingPrice.compareTo(upperBound) > 0) {
            flagCaption.setText("Asking price is ABOVE estimated market corridor");
        } else if (askingPrice.compareTo(lowerBound) < 0) {
            flagCaption.setText("Asking price is BELOW estimated market corridor");
        } else {
            flagCaption.setText("Asking price falls within realistic market corridor");
        }
    }

    /** What the flag means for the decision in front of the agent. */
    private String flagWording(ValuationFlag flag) {
        if (flag == null) {
            return "Not assessed";
        }
        return switch (flag) {
            case OK -> "Within range";
            case ABOVE_MARKET -> "Above the market";
            case IMPLAUSIBLE -> "Implausible";
        };
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
            Label empty = new Label("No factor breakdown recorded.");
            empty.getStyleClass().add("hint");
            factorsBox.getChildren().add(empty);
            return;
        }
        for (Map.Entry<String, BigDecimal> factor : factors.entrySet()) {
            factorsBox.getChildren().add(buildFactorRow(factor.getKey(), factor.getValue()));
        }
    }

    /*
     * These are signed adjustments to a base figure, so the sign belongs in
     * front of the money and carries the colour: formatMoney on a negative
     * produced "$-4,496", with the minus inside the amount where it reads as
     * part of the number rather than as a direction.
     */
    private HBox buildFactorRow(String key, BigDecimal value) {
        Label label = new Label(sentenceCase(key));
        label.getStyleClass().add("hint");

        boolean negative = value.signum() < 0;
        Label amount = new Label((negative ? "−" : "+") + formatMoney(value.abs()));
        amount.getStyleClass().addAll("body-medium", negative ? "tone-bad" : "tone-good");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return new HBox(8, label, spacer, amount);
    }

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
            Label empty = new Label("No comparable properties used.");
            empty.getStyleClass().add("hint");
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
        title.getStyleClass().add("label-soft");
        title.setStyle("-fx-font-weight: 600;");

        Label meta = new Label(Format.area(comparableProperty.getAreaSqm()) + " · "
            + formatMoney(comparableProperty.getAskingPrice()) + " · "
            + Format.percentage(similarityPercent(comparable.getSimilarityScore())) + " match");
        meta.getStyleClass().add("hint");

        VBox row = new VBox(2, title, meta);
        row.setStyle("-fx-background-color: -c-surface-subtle; -fx-padding: 8 10 8 10; -fx-background-radius: 6px;");
        return row;
    }

    private String comparableLabel(Property comparableProperty) {
        return comparableProperty.getAddressLine() != null && !comparableProperty.getAddressLine().isBlank()
            ? comparableProperty.getAddressLine() : comparableProperty.getTitle();
    }

    private BigDecimal similarityPercent(BigDecimal similarityScore) {
        return similarityScore.multiply(BigDecimal.valueOf(100));
    }

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
