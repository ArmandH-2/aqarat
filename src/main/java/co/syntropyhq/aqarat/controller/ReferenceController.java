package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.dao.DistrictDao;
import co.syntropyhq.aqarat.dao.PropertyTypeDao;
import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.District;
import co.syntropyhq.aqarat.model.PropertyType;
import co.syntropyhq.aqarat.model.Role;
import co.syntropyhq.aqarat.model.SystemSetting;
import co.syntropyhq.aqarat.service.ReferenceService;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.AnimationUtil;
import co.syntropyhq.aqarat.util.FieldError;
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.SessionManager;
import co.syntropyhq.aqarat.util.UIHelper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class ReferenceController {

    @FXML
    private VBox contentBox;
    @FXML
    private Label accessDeniedLabel;
    @FXML
    private Button districtsTabButton;
    @FXML
    private Button typesTabButton;
    @FXML
    private Button settingsTabButton;
    @FXML
    private VBox districtsBox;
    @FXML
    private VBox typesBox;
    @FXML
    private VBox settingsBox;

    @FXML
    private TextField newDistrictNameField;
    @FXML
    private Label newDistrictNameError;
    @FXML
    private TextField newDistrictGovernorateField;
    @FXML
    private Label newDistrictGovernorateError;
    @FXML
    private TextField newDistrictPriceField;
    @FXML
    private Label newDistrictPriceError;
    @FXML
    private ListView<District> districtList;

    @FXML
    private TextField newTypeNameField;
    @FXML
    private Label newTypeNameError;
    @FXML
    private ListView<PropertyType> typeList;

    @FXML
    private ListView<SystemSetting> settingList;

    private final ReferenceService referenceService =
        new ReferenceService(new DistrictDao(), new PropertyTypeDao());

    @FXML
    private void initialize() {
        AppUser currentUser = SessionManager.getCurrentUser();
        if (currentUser == null || currentUser.getRole() != Role.ADMIN) {
            denyAccess();
            return;
        }
        districtList.setCellFactory(list -> new DistrictCard());
        typeList.setCellFactory(list -> new TypeCard());
        settingList.setCellFactory(list -> new SettingCard());
        emptyState(districtList, "No districts recorded.");
        emptyState(typeList, "No property types found.");
        emptyState(settingList, "No system settings found.");
        selectDistrictsTab();
    }

    private void denyAccess() {
        contentBox.setVisible(false);
        contentBox.setManaged(false);
        accessDeniedLabel.setText("You do not have access to reference data.");
        accessDeniedLabel.setVisible(true);
        accessDeniedLabel.setManaged(true);
    }

    private void emptyState(ListView<?> list, String text) {
        list.setPlaceholder(UIHelper.createEmptyState(text, "Use the form above to add new entries."));
    }

    @FXML
    private void handleDistrictsTab() {
        selectDistrictsTab();
    }

    @FXML
    private void handleTypesTab() {
        selectTypesTab();
    }

    @FXML
    private void handleSettingsTab() {
        selectSettingsTab();
    }

    private void selectDistrictsTab() {
        setActiveTab(districtsTabButton, typesTabButton, settingsTabButton);
        showOnly(districtsBox, typesBox, settingsBox);
        loadDistricts();
    }

    private void selectTypesTab() {
        setActiveTab(typesTabButton, districtsTabButton, settingsTabButton);
        showOnly(typesBox, districtsBox, settingsBox);
        loadTypes();
    }

    private void selectSettingsTab() {
        setActiveTab(settingsTabButton, districtsTabButton, typesTabButton);
        showOnly(settingsBox, districtsBox, typesBox);
        loadSettings();
    }

    private void setActiveTab(Button active, Button... inactive) {
        active.getStyleClass().setAll("tab-pill-button", "active");
        for (Button button : inactive) {
            button.getStyleClass().setAll("tab-pill-button");
        }
    }

    private void showOnly(VBox visibleBox, VBox... hiddenBoxes) {
        visibleBox.setVisible(true);
        visibleBox.setManaged(true);
        for (VBox box : hiddenBoxes) {
            box.setVisible(false);
            box.setManaged(false);
        }
    }

    private void loadDistricts() {
        try {
            List<District> districts = referenceService.findAllDistricts();
            districtList.setItems(FXCollections.observableArrayList(districts));
        } catch (SQLException e) {
            AlertUtil.showError("Could not load districts. Check that SQL Server is running.");
        }
    }

    private void loadTypes() {
        try {
            List<PropertyType> types = referenceService.findAllPropertyTypes();
            typeList.setItems(FXCollections.observableArrayList(types));
        } catch (SQLException e) {
            AlertUtil.showError("Could not load property types. Check that SQL Server is running.");
        }
    }

    private void loadSettings() {
        try {
            List<SystemSetting> settings = referenceService.findAllSettings();
            settingList.setItems(FXCollections.observableArrayList(settings));
        } catch (SQLException e) {
            AlertUtil.showError("Could not load settings. Check that SQL Server is running.");
        }
    }

    @FXML
    private void handleAddDistrict() {
        FieldError.clear(newDistrictNameField, newDistrictNameError);
        FieldError.clear(newDistrictGovernorateField, newDistrictGovernorateError);
        FieldError.clear(newDistrictPriceField, newDistrictPriceError);

        String name = newDistrictNameField.getText().trim();
        String governorate = newDistrictGovernorateField.getText().trim();
        BigDecimal price = requirePositivePrice(newDistrictPriceField, newDistrictPriceError);
        boolean ok = true;
        if (name.isEmpty()) {
            FieldError.show(newDistrictNameField, newDistrictNameError, "Enter a district name.");
            ok = false;
        }
        if (governorate.isEmpty()) {
            FieldError.show(newDistrictGovernorateField, newDistrictGovernorateError,
                "Enter a governorate.");
            ok = false;
        }
        if (price == null || FieldError.isShown(newDistrictPriceError)) {
            ok = false;
        }
        if (!ok) {
            return;
        }

        District district = new District();
        district.setName(name);
        district.setGovernorate(governorate);
        district.setAvgPricePerSqm(price);
        try {
            referenceService.insertDistrict(district);
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        newDistrictNameField.clear();
        newDistrictGovernorateField.clear();
        newDistrictPriceField.clear();
        loadDistricts();
    }

    private void handleSaveDistrict(District district, TextField nameField,
            TextField governorateField, TextField priceField, Label priceError) {
        FieldError.clear(priceField, priceError);
        String name = nameField.getText().trim();
        String governorate = governorateField.getText().trim();
        BigDecimal price = requirePositivePrice(priceField, priceError);
        if (name.isEmpty() || governorate.isEmpty()) {
            AlertUtil.showError("Name and governorate are required.");
            return;
        }
        if (price == null) {
            return;
        }
        district.setName(name);
        district.setGovernorate(governorate);
        district.setAvgPricePerSqm(price);
        try {
            referenceService.updateDistrict(district);
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("Baseline rates updated",
            "Valuations calculated from now on use the new figures. Valuations already recorded are unchanged.");
        loadDistricts();
    }

    @FXML
    private void handleAddType() {
        FieldError.clear(newTypeNameField, newTypeNameError);
        String name = newTypeNameField.getText().trim();
        if (name.isEmpty()) {
            FieldError.show(newTypeNameField, newTypeNameError, "Enter a property type name.");
            return;
        }
        PropertyType propertyType = new PropertyType();
        propertyType.setName(name);
        try {
            referenceService.insertPropertyType(propertyType);
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        newTypeNameField.clear();
        loadTypes();
    }

    private void handleSaveSetting(SystemSetting setting, TextField valueField, Label valueError) {
        FieldError.clear(valueField, valueError);
        String newValue = valueField.getText().trim();
        if (newValue.isEmpty()) {
            FieldError.show(valueField, valueError, "Enter a value.");
            return;
        }
        if (isNumeric(setting.getValue()) && !isNumeric(newValue)) {
            FieldError.show(valueField, valueError, "This setting must stay a number.");
            return;
        }
        try {
            referenceService.updateSetting(setting.getSettingKey(), newValue);
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        setting.setValue(newValue);
        AlertUtil.showInfo("Setting updated",
            "It applies to work done from now on, not to records already written.");
    }

    private BigDecimal requirePositivePrice(TextField field, Label errorLabel) {
        String text = field.getText() == null ? "" : field.getText().trim();
        if (text.isEmpty()) {
            FieldError.show(field, errorLabel, "Enter a price per m².");
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(text);
            if (value.signum() <= 0) {
                FieldError.show(field, errorLabel, "Enter a positive number.");
                return null;
            }
            return value.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            FieldError.show(field, errorLabel, "Enter a positive number.");
            return null;
        }
    }

    private boolean isNumeric(String text) {
        try {
            new BigDecimal(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private final class DistrictCard extends ListCell<District> {

        @Override
        protected void updateItem(District district, boolean empty) {
            super.updateItem(district, empty);
            setText(null);
            setGraphic(empty || district == null ? null : buildCard(district));
        }

        private VBox buildCard(District district) {
            HBox fields = new HBox(12);
            fields.setAlignment(Pos.CENTER_LEFT);

            TextField nameField = new TextField(district.getName());
            TextField governorateField = new TextField(district.getGovernorate());
            TextField priceField = new TextField(district.getAvgPricePerSqm().toPlainString());
            HBox.setHgrow(nameField, Priority.ALWAYS);
            HBox.setHgrow(governorateField, Priority.ALWAYS);
            HBox.setHgrow(priceField, Priority.ALWAYS);

            Label priceError = new Label();
            priceError.getStyleClass().add("field-error");
            priceError.setManaged(false);
            priceError.setVisible(false);

            Button save = new Button("Save changes");
            save.getStyleClass().addAll("button", "button-secondary");
            save.setOnAction(event -> handleSaveDistrict(
                district, nameField, governorateField, priceField, priceError));

            fields.getChildren().addAll(nameField, governorateField, priceField, save);

            VBox card = new VBox(6, fields, priceError);
            card.getStyleClass().addAll("card-subtle", "card-hoverable");
            card.setPadding(new Insets(12));
            AnimationUtil.addHoverLift(card);
            return card;
        }
    }

    private final class TypeCard extends ListCell<PropertyType> {

        @Override
        protected void updateItem(PropertyType propertyType, boolean empty) {
            super.updateItem(propertyType, empty);
            setText(null);
            setGraphic(empty || propertyType == null ? null : buildCard(propertyType));
        }

        private VBox buildCard(PropertyType propertyType) {
            Label name = new Label(propertyType.getName());
            name.getStyleClass().add("section-title");
            VBox card = new VBox(name);
            card.getStyleClass().addAll("card-subtle", "card-hoverable");
            card.setPadding(new Insets(14));
            AnimationUtil.addHoverLift(card);
            return card;
        }
    }

    private final class SettingCard extends ListCell<SystemSetting> {

        @Override
        protected void updateItem(SystemSetting setting, boolean empty) {
            super.updateItem(setting, empty);
            setText(null);
            setGraphic(empty || setting == null ? null : buildCard(setting));
        }

        private VBox buildCard(SystemSetting setting) {
            Label key = new Label(setting.getSettingKey());
            key.getStyleClass().add("section-title");

            Label description = new Label(setting.getDescription());
            description.getStyleClass().add("hint");
            description.setWrapText(true);

            TextField valueField = new TextField(setting.getValue());
            HBox.setHgrow(valueField, Priority.ALWAYS);

            Label valueError = new Label();
            valueError.getStyleClass().add("field-error");
            valueError.setManaged(false);
            valueError.setVisible(false);

            Button save = new Button("Save");
            save.getStyleClass().addAll("button", "button-primary");
            save.setOnAction(event -> handleSaveSetting(setting, valueField, valueError));

            HBox row = new HBox(12, valueField, save);
            row.setAlignment(Pos.CENTER_LEFT);

            VBox card = new VBox(8, key, description, row, valueError);
            card.getStyleClass().addAll("card", "card-hoverable");
            card.setPadding(new Insets(16));
            AnimationUtil.addHoverLift(card);
            return card;
        }
    }
}
