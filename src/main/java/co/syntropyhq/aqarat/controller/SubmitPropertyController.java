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
import co.syntropyhq.aqarat.model.PropertyType;
import co.syntropyhq.aqarat.model.Role;
import co.syntropyhq.aqarat.service.AuditService;
import co.syntropyhq.aqarat.service.PropertyService;
import co.syntropyhq.aqarat.service.ReferenceService;
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
import java.util.function.Function;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
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
    private Label photoCountBadge;
    @FXML
    private Label photoListLabel;

    private final List<Path> selectedPhotos = new ArrayList<>();

    private final ReferenceService referenceService =
        new ReferenceService(new DistrictDao(), new PropertyTypeDao());
    private final PropertyService propertyService =
        new PropertyService(new PropertyDao(), new PropertyPhotoDao(), new PropertyMessageDao(), new AuditService(new AuditDao()));

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
        try {
            propertyService.submit(property, photos);
        } catch (IOException e) {
            AlertUtil.showError("One of the photos could not be read. Check the file exists and is a JPG or PNG, then try again.");
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("Your submission is now awaiting review by our agents.");
        Router.show(Panel.MY_PROPERTIES);
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
