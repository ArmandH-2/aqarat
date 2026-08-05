package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.dao.AuditDao;
import co.syntropyhq.aqarat.dao.DistrictDao;
import co.syntropyhq.aqarat.dao.PropertyDao;
import co.syntropyhq.aqarat.dao.PropertyPhotoDao;
import co.syntropyhq.aqarat.dao.PropertyTypeDao;
import co.syntropyhq.aqarat.model.DealType;
import co.syntropyhq.aqarat.model.District;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.PropertyType;
import co.syntropyhq.aqarat.service.AuditService;
import co.syntropyhq.aqarat.service.PropertyService;
import co.syntropyhq.aqarat.service.ReferenceService;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.FieldError;
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.Panel;
import co.syntropyhq.aqarat.util.Router;
import co.syntropyhq.aqarat.util.SessionManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.function.Function;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

public class SubmitPropertyController {

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

    private final ReferenceService referenceService =
        new ReferenceService(new DistrictDao(), new PropertyTypeDao());
    private final PropertyService propertyService =
        new PropertyService(new PropertyDao(), new PropertyPhotoDao(), new AuditService(new AuditDao()));

    @FXML
    private void initialize() {
        loadReferenceData();
        setLabelConverter(dealTypeCombo, Format::enumLabel);
        dealTypeCombo.setItems(FXCollections.observableArrayList(DealType.values()));
        dealTypeCombo.getSelectionModel().select(DealType.SALE);
        dealTypeCombo.valueProperty().addListener((obs, oldValue, newValue) -> updateDealTypeUi(newValue));
        updateDealTypeUi(DealType.SALE);
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

    // The term fields only mean something for a lease (ck_property_terms_sale),
    // and the price field carries two different meanings depending on deal
    // type (DESIGN.md section 8), so both are driven from one place.
    private void updateDealTypeUi(DealType dealType) {
        boolean isRent = dealType == DealType.RENT;
        askingPriceLabel.setText(isRent ? "Monthly rent" : "Asking price");
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
        try {
            propertyService.submit(property);
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("Your submission is now awaiting review.");
        Router.show(Panel.MY_PROPERTIES);
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

    // Term months are only ever parsed for a lease, so a sale carries them as
    // null unconditionally - this is what keeps ck_property_terms_sale from
    // ever being violated, regardless of what is left over in a hidden field.
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

    // Bedrooms and bathrooms mirror the column default: left blank, they are 0.
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

    // Floor number, total floors, year built and lease terms are all nullable
    // columns - left blank, they stay null rather than becoming zero.
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
