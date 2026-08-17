package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.dao.AuditDao;
import co.syntropyhq.aqarat.dao.DistrictDao;
import co.syntropyhq.aqarat.dao.PropertyDao;
import co.syntropyhq.aqarat.dao.PropertyMessageDao;
import co.syntropyhq.aqarat.dao.PropertyPhotoDao;
import co.syntropyhq.aqarat.dao.PropertyTypeDao;
import co.syntropyhq.aqarat.dao.ReservationDao;
import co.syntropyhq.aqarat.dao.SystemSettingDao;
import co.syntropyhq.aqarat.dao.ViewingDao;
import co.syntropyhq.aqarat.model.DealType;
import co.syntropyhq.aqarat.model.District;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.PropertyPhoto;
import co.syntropyhq.aqarat.model.PropertyStatus;
import co.syntropyhq.aqarat.model.PropertyType;
import co.syntropyhq.aqarat.service.AuditService;
import co.syntropyhq.aqarat.service.PropertyService;
import co.syntropyhq.aqarat.service.ReferenceService;
import co.syntropyhq.aqarat.service.ReservationService;
import co.syntropyhq.aqarat.service.ViewingService;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.AnimationUtil;
import co.syntropyhq.aqarat.util.FieldError;
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.NeedsId;
import co.syntropyhq.aqarat.util.Router;
import co.syntropyhq.aqarat.util.SessionManager;
import co.syntropyhq.aqarat.util.UIHelper;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

public class PropertyDetailsController implements NeedsId {

    private static final String IMAGE_ROOT = "uploads";

    @FXML
    private Label titleLabel;
    @FXML
    private Label districtSubtitle;
    @FXML
    private Label statusPill;
    @FXML
    private Label priceValue;
    @FXML
    private Label pricePerSqmLabel;
    @FXML
    private ImageView mainImageView;
    @FXML
    private HBox thumbnailStrip;
    @FXML
    private Label descriptionValue;
    @FXML
    private Label districtValue;
    @FXML
    private Label typeValue;
    @FXML
    private Label dealTypeValue;
    @FXML
    private Label bedroomsValue;
    @FXML
    private Label bathroomsValue;
    @FXML
    private Label areaValue;
    @FXML
    private Label floorValue;
    @FXML
    private Label yearBuiltValue;
    @FXML
    private FlowPane amenitiesPane;
    @FXML
    private Label addressValue;
    @FXML
    private VBox actionsBox;
    @FXML
    private DatePicker viewingDatePicker;
    @FXML
    private ComboBox<String> viewingTimeCombo;
    @FXML
    private Label viewingError;
    @FXML
    private TextField depositField;
    @FXML
    private Label depositError;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final PropertyService propertyService =
        new PropertyService(new PropertyDao(), new PropertyPhotoDao(), new PropertyMessageDao(), new AuditService(new AuditDao()));
    private final ReferenceService referenceService =
        new ReferenceService(new DistrictDao(), new PropertyTypeDao());
    private final ViewingService viewingService =
        new ViewingService(new ViewingDao(), propertyService, new AuditService(new AuditDao()));
    private final ReservationService reservationService = new ReservationService(
        new ReservationDao(), new SystemSettingDao(), propertyService, new AuditService(new AuditDao()));

    private Property currentProperty;

    @FXML
    private void initialize() {
        for (int hour = 9; hour <= 18; hour++) {
            viewingTimeCombo.getItems().add(String.format("%02d:00", hour));
        }
    }

    @Override
    public void receiveId(int id) {
        Property property;
        try {
            property = propertyService.findById(id);
        } catch (SQLException e) {
            AlertUtil.showError("Could not load this listing. Check that SQL Server is running.");
            return;
        }
        if (property == null) {
            AlertUtil.showError("This listing no longer exists.");
            return;
        }
        currentProperty = property;
        renderProperty(property);
    }

    private void renderProperty(Property property) {
        titleLabel.setText(property.getTitle());
        applyStatusPill(property.getStatus());
        priceValue.setText(formatPrice(property));

        String districtName = lookupDistrictName(property.getDistrictId());
        String typeName = lookupTypeName(property.getPropertyTypeId());
        districtSubtitle.setText(districtName + " • " + typeName);

        BigDecimal pricePerSqm = BigDecimal.ZERO;
        if (property.getAreaSqm() != null && property.getAreaSqm().compareTo(BigDecimal.ZERO) > 0 && property.getAskingPrice() != null) {
            pricePerSqm = property.getAskingPrice().divide(property.getAreaSqm(), 0, RoundingMode.HALF_UP);
        }
        pricePerSqmLabel.setText("$" + pricePerSqm + "/m² estimated rate");

        descriptionValue.setText(property.getDescription() == null || property.getDescription().isBlank()
            ? "No detailed description provided." : property.getDescription());

        renderSpecs(property, districtName, typeName);
        renderAmenities(property);
        renderGallery(property.getId());
        updateActionsVisibility(property);
    }

    private void updateActionsVisibility(Property property) {
        boolean isCustomer = SessionManager.isCustomer();
        boolean isOwner = isCustomer && SessionManager.getCurrentUser() != null
            && SessionManager.getCurrentUser().getId() == property.getOwnerId();
        boolean canAct = isCustomer && !isOwner
            && property.getStatus() == PropertyStatus.AVAILABLE;
        actionsBox.setVisible(canAct);
        actionsBox.setManaged(canAct);
    }

    private void renderGallery(int propertyId) {
        List<PropertyPhoto> photos;
        try {
            photos = propertyService.findPhotos(propertyId);
        } catch (SQLException e) {
            AlertUtil.showError("Could not load photos for this listing.");
            return;
        }

        thumbnailStrip.getChildren().clear();

        if (photos.isEmpty()) {
            mainImageView.setImage(null);
            return;
        }

        // Set primary photo
        setMainPhoto(photos.get(0));

        // Populate thumbnail strip
        for (PropertyPhoto photo : photos) {
            StackPane thumbContainer = new StackPane();
            thumbContainer.setPrefSize(70, 50);
            thumbContainer.setMinSize(70, 50);
            thumbContainer.setMaxSize(70, 50);
            thumbContainer.setStyle("-fx-background-color: -c-surface-subtle; -fx-background-radius: 6px; -fx-border-color: -c-border-subtle; -fx-border-radius: 6px;");
            thumbContainer.setCursor(Cursor.HAND);

            Path path = Path.of(IMAGE_ROOT, photo.getFilePath());
            if (Files.exists(path)) {
                ImageView thumbView = new ImageView(new Image(path.toUri().toString(), 70, 50, false, true));
                Rectangle clip = new Rectangle(70, 50);
                clip.setArcWidth(10);
                clip.setArcHeight(10);
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

    private String formatPrice(Property property) {
        return property.getDealType() == DealType.SALE
            ? Format.salePrice(property.getAskingPrice())
            : Format.monthlyRent(property.getAskingPrice());
    }

    private void renderSpecs(Property property, String districtName, String typeName) {
        districtValue.setText(districtName);
        typeValue.setText(typeName);
        dealTypeValue.setText(Format.enumLabel(property.getDealType()));
        areaValue.setText(Format.area(property.getAreaSqm()));
        bedroomsValue.setText(String.valueOf(property.getBedrooms()));
        bathroomsValue.setText(String.valueOf(property.getBathrooms()));
        floorValue.setText(formatNullableInt(property.getFloorNumber()));
        yearBuiltValue.setText(formatNullableInt(property.getYearBuilt()));
        addressValue.setText(property.getAddressLine() == null || property.getAddressLine().isBlank()
            ? "Address on file" : property.getAddressLine());
    }

    private void renderAmenities(Property property) {
        amenitiesPane.getChildren().clear();
        addAmenityChip("Parking", property.isHasParking());
        addAmenityChip("Elevator", property.isHasElevator());
        addAmenityChip("Balcony", property.isHasBalcony());
        addAmenityChip("Furnished", property.isFurnished());
    }

    private void addAmenityChip(String name, boolean active) {
        HBox chip = new HBox(6);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.setStyle(active
            ? "-fx-background-color: -c-primary-tint; -fx-padding: 6 12 6 12; -fx-background-radius: 20px; -fx-border-color: -c-primary; -fx-border-radius: 20px;"
            : "-fx-background-color: -c-surface-subtle; -fx-padding: 6 12 6 12; -fx-background-radius: 20px; -fx-border-color: -c-border-subtle; -fx-border-radius: 20px; -fx-opacity: 0.6;");

        Label label = new Label((active ? "✓ " : "✕ ") + name);
        label.setStyle(active
            ? "-fx-font-weight: 600; -fx-text-fill: -c-primary;"
            : "-fx-font-weight: 500; -fx-text-fill: -c-text-muted;");

        chip.getChildren().add(label);
        amenitiesPane.getChildren().add(chip);
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
        statusPill.getStyleClass().add(pillClassFor(status));
    }

    private String pillClassFor(PropertyStatus status) {
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

    @FXML
    private void handleRequestViewing() {
        FieldError.clear(viewingDatePicker, viewingError);
        LocalDate date = viewingDatePicker.getValue();
        String time = viewingTimeCombo.getValue();
        if (date == null || time == null) {
            FieldError.show(viewingDatePicker, viewingError, "Choose a date and a time.");
            return;
        }
        LocalDateTime localPick = LocalDateTime.of(date, LocalTime.parse(time, TIME_FORMAT));
        if (!localPick.isAfter(LocalDateTime.now())) {
            FieldError.show(viewingDatePicker, viewingError, "Choose a time in the future.");
            return;
        }
        LocalDateTime scheduledAt = Format.toUtc(localPick);
        try {
            viewingService.request(
                currentProperty.getId(), SessionManager.getCurrentUser().getId(), scheduledAt);
        } catch (ViewingService.CannotRequestOwnPropertyException e) {
            AlertUtil.showError(e.getMessage());
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("Your viewing request has been sent.");
        viewingDatePicker.setValue(null);
        viewingTimeCombo.setValue(null);
        receiveId(currentProperty.getId());
    }

    @FXML
    private void handleReserve() {
        FieldError.clear(depositField, depositError);
        BigDecimal deposit = parseDeposit();
        if (deposit == null) {
            return;
        }
        if (!AlertUtil.confirm("Reserve this property with a deposit of "
                + Format.paymentAmount(deposit) + "? This takes it off the market.")) {
            return;
        }
        try {
            reservationService.create(
                currentProperty.getId(), SessionManager.getCurrentUser().getId(), deposit);
        } catch (ReservationService.PropertyNotAvailableException e) {
            AlertUtil.showError("This property is not available to reserve.");
            return;
        } catch (ReservationService.DuplicateReservationException e) {
            AlertUtil.showError("This property already has an active reservation.");
            return;
        } catch (ReservationService.CannotReserveOwnPropertyException e) {
            AlertUtil.showError(e.getMessage());
            return;
        } catch (PropertyService.InvalidTransitionException e) {
            AlertUtil.showError(
                "The property could not be moved to reserved. Ask an agent to look into it.");
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("Property reserved. An agent will be in touch about the next steps.");
        depositField.clear();
        receiveId(currentProperty.getId());
    }

    private BigDecimal parseDeposit() {
        String text = depositField.getText().trim();
        if (text.isEmpty()) {
            FieldError.show(depositField, depositError, "Enter a deposit amount.");
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(text).setScale(2, RoundingMode.HALF_UP);
            if (value.signum() <= 0) {
                FieldError.show(depositField, depositError, "Enter an amount greater than zero.");
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            FieldError.show(depositField, depositError, "Enter a valid number.");
            return null;
        }
    }

    @FXML
    private void handleBack() {
        Router.back();
    }
}
