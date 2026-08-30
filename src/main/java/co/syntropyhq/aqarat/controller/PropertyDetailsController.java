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
import co.syntropyhq.aqarat.util.Dialogs;
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
    private StackPane mainPhotoContainer;
    @FXML
    private ImageView mainImageView;
    @FXML
    private Label unavailableNote;
    @FXML
    private HBox thumbnailStrip;
    @FXML
    private Label descriptionValue;
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

        // The frame's height is fixed and its width comes from the scroll pane,
        // so the photograph can follow it without feeding a size back upwards.
        mainPhotoContainer.setMinWidth(0);
        mainImageView.fitWidthProperty().bind(mainPhotoContainer.widthProperty());
        mainImageView.fitHeightProperty().bind(mainPhotoContainer.heightProperty());

        Rectangle heroClip = new Rectangle();
        heroClip.widthProperty().bind(mainPhotoContainer.widthProperty());
        heroClip.heightProperty().bind(mainPhotoContainer.heightProperty());
        heroClip.setArcWidth(28);
        heroClip.setArcHeight(28);
        mainImageView.setClip(heroClip);
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
        districtSubtitle.setText(
            (districtName + " · " + typeName + " · " + Format.enumLabel(property.getDealType()))
                .toUpperCase());

        BigDecimal pricePerSqm = BigDecimal.ZERO;
        if (property.getAreaSqm() != null && property.getAreaSqm().compareTo(BigDecimal.ZERO) > 0 && property.getAskingPrice() != null) {
            pricePerSqm = property.getAskingPrice().divide(property.getAreaSqm(), 0, RoundingMode.HALF_UP);
        }
        pricePerSqmLabel.setText(property.getDealType() == DealType.SALE
            ? Format.pricePerSqm(pricePerSqm) + " · Sale"
            : "Per month · Lease");

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

        String note = null;
        if (isOwner) {
            note = "This is your listing. Manage it from your portfolio.";
        } else if (property.getStatus() != PropertyStatus.AVAILABLE) {
            note = "This property is " + Format.enumLabel(property.getStatus()).toLowerCase()
                + ", so viewings and reservations are closed.";
        } else if (!isCustomer) {
            note = "Sign in as a customer to request a viewing or reserve this property.";
        }
        unavailableNote.setText(note == null ? "" : note);
        unavailableNote.setVisible(note != null);
        unavailableNote.setManaged(note != null);
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
            thumbContainer.setPrefSize(104, 72);
            thumbContainer.setMinSize(104, 72);
            thumbContainer.setMaxSize(104, 72);
            thumbContainer.getStyleClass().add("thumb");
            thumbContainer.setCursor(Cursor.HAND);

            Path path = Path.of(IMAGE_ROOT, photo.getFilePath());
            if (Files.exists(path)) {
                ImageView thumbView = new ImageView(
                    new Image(path.toUri().toString(), 104, 72, false, true, true));
                Rectangle clip = new Rectangle(104, 72);
                clip.setArcWidth(12);
                clip.setArcHeight(12);
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
        if (!thumbnailStrip.getChildren().isEmpty()) {
            markActiveThumb(thumbnailStrip.getChildren().get(0));
        }
    }

    private void markActiveThumb(javafx.scene.Node active) {
        for (javafx.scene.Node node : thumbnailStrip.getChildren()) {
            node.getStyleClass().remove("thumb-active");
        }
        if (!active.getStyleClass().contains("thumb-active")) {
            active.getStyleClass().add("thumb-active");
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
        areaValue.setText(Format.area(property.getAreaSqm()));
        bedroomsValue.setText(String.valueOf(property.getBedrooms()));
        bathroomsValue.setText(String.valueOf(property.getBathrooms()));
        floorValue.setText(formatFloor(property));
        yearBuiltValue.setText(formatNullableInt(property.getYearBuilt()));
        addressValue.setText(property.getAddressLine() == null || property.getAddressLine().isBlank()
            ? "Address on file" : property.getAddressLine());
    }

    private void renderAmenities(Property property) {
        amenitiesPane.getChildren().clear();
        addAmenityChip("Parking", property.isHasParking());
        addAmenityChip("Lift", property.isHasElevator());
        addAmenityChip("Balcony", property.isHasBalcony());
        addAmenityChip("Furnished", property.isFurnished());
    }

    /* A feature the property does not have is still worth stating — "no lift"
       matters to a buyer — but it is set back rather than marked with a cross. */
    private void addAmenityChip(String name, boolean active) {
        Label chip = new Label(active ? name : "No " + name.toLowerCase());
        chip.getStyleClass().add(active ? "amenity-chip" : "amenity-chip-absent");
        amenitiesPane.getChildren().add(chip);
    }

    private String formatFloor(Property property) {
        if (property.getFloorNumber() == null) {
            return "—";
        }
        return property.getTotalFloors() == null
            ? String.valueOf(property.getFloorNumber())
            : property.getFloorNumber() + " of " + property.getTotalFloors();
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
            "tone-good", "tone-warn", "tone-bad", "tone-info", "tone-neutral");
        statusPill.getStyleClass().add(toneClassFor(status));
    }

    private String toneClassFor(PropertyStatus status) {
        switch (status) {
            case AVAILABLE:
                return "tone-good";
            case PENDING_REVIEW:
            case NEEDS_INFO:
                return "tone-warn";
            case REJECTED:
                return "tone-bad";
            case RESERVED:
            case UNDER_CONTRACT:
            case DRAFT:
                return "tone-info";
            default:
                return "tone-neutral";
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
        AlertUtil.showInfo("Viewing requested",
            "An agent confirms the slot before it is yours. You will see it under Portfolio either way.");
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
        boolean go = Dialogs.ask("Reserve this property?")
            .about(currentProperty.getTitle())
            .because("A deposit of " + Format.paymentAmount(deposit) + " is recorded against it "
                + "and the listing comes off the market while your reservation stands.")
            .confirm("Reserve it")
            .cancel("Not yet")
            .show();
        if (!go) {
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
        AlertUtil.showInfo("Property reserved",
            "It is off the market while your reservation stands. An agent will be in touch about the contract.");
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
