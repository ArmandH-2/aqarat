package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.dao.AuditDao;
import co.syntropyhq.aqarat.dao.DistrictDao;
import co.syntropyhq.aqarat.dao.PropertyDao;
import co.syntropyhq.aqarat.dao.PropertyMessageDao;
import co.syntropyhq.aqarat.dao.PropertyPhotoDao;
import co.syntropyhq.aqarat.dao.PropertySearch;
import co.syntropyhq.aqarat.dao.PropertyTypeDao;
import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.DealType;
import co.syntropyhq.aqarat.model.District;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.PropertyPhoto;
import co.syntropyhq.aqarat.model.PropertyStatus;
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
import co.syntropyhq.aqarat.util.UIHelper;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.StringConverter;

public class ListingsController {

    private static final int PAGE_SIZE = 15;

    @FXML
    private VBox contentBox;
    @FXML
    private Label accessDeniedLabel;
    @FXML
    private TextField titleField;
    @FXML
    private VBox filterPanel;
    @FXML
    private Button filtersButton;
    @FXML
    private ComboBox<District> districtCombo;
    @FXML
    private ComboBox<PropertyType> typeCombo;
    @FXML
    private ComboBox<DealType> dealTypeCombo;
    @FXML
    private ComboBox<Integer> bedroomsCombo;
    @FXML
    private TextField minPriceField;
    @FXML
    private TextField maxPriceField;
    @FXML
    private TextField minAreaField;
    @FXML
    private TextField maxAreaField;
    @FXML
    private Label minPriceError;
    @FXML
    private Label maxPriceError;
    @FXML
    private Label minAreaError;
    @FXML
    private Label maxAreaError;
    @FXML
    private ListView<Property> propertyList;
    @FXML
    private Button previousButton;
    @FXML
    private Button nextButton;

    private final PropertyService propertyService =
        new PropertyService(new PropertyDao(), new PropertyPhotoDao(), new PropertyMessageDao(), new AuditService(new AuditDao()));
    private final ReferenceService referenceService =
        new ReferenceService(new DistrictDao(), new PropertyTypeDao());

    private final Map<Integer, District> districtsById = new HashMap<>();
    private final Map<Integer, PropertyType> typesById = new HashMap<>();

    private PropertySearch currentFilters = new PropertySearch();
    private int currentOffset = 0;

    @FXML
    private void initialize() {
        AppUser user = SessionManager.getCurrentUser();
        if (user == null || (user.getRole() != Role.AGENT && user.getRole() != Role.ADMIN)) {
            denyAccess();
            return;
        }
        propertyList.setCellFactory(list -> new PropertyCard());
        loadReferenceData();
        configureDealTypeCombo();
        configureBedroomsCombo();
        runSearch(0);
    }

    private void denyAccess() {
        contentBox.setVisible(false);
        contentBox.setManaged(false);
        accessDeniedLabel.setText("You do not have access to listings.");
        accessDeniedLabel.setVisible(true);
        accessDeniedLabel.setManaged(true);
    }

    private void loadReferenceData() {
        try {
            List<District> districts = referenceService.findAllDistricts();
            districtCombo.getItems().add(null);
            districtCombo.getItems().addAll(districts);
            districtCombo.setConverter(anyOr("All districts", District::getName));
            for (District district : districts) {
                districtsById.put(district.getId(), district);
            }

            List<PropertyType> types = referenceService.findAllPropertyTypes();
            typeCombo.getItems().add(null);
            typeCombo.getItems().addAll(types);
            typeCombo.setConverter(anyOr("All types", PropertyType::getName));
            for (PropertyType type : types) {
                typesById.put(type.getId(), type);
            }
        } catch (SQLException e) {
            AlertUtil.showError("Could not load reference data. Check that SQL Server is running.");
        }
    }

    private void configureDealTypeCombo() {
        dealTypeCombo.getItems().addAll(null, DealType.SALE, DealType.RENT);
        dealTypeCombo.setConverter(anyOr("Sale & Rent", Format::enumLabel));
    }

    private void configureBedroomsCombo() {
        bedroomsCombo.getItems().addAll(null, 0, 1, 2, 3, 4, 5);
        bedroomsCombo.setConverter(anyOr("Any bedrooms", val -> val == 0 ? "Studio (0)" : val + "+ Beds"));
    }

    private static <T> StringConverter<T> anyOr(String anyLabel, Function<T, String> label) {
        return new StringConverter<T>() {
            @Override
            public String toString(T value) {
                return value == null ? anyLabel : label.apply(value);
            }

            @Override
            public T fromString(String text) {
                return null;
            }
        };
    }

    /* The structured filters are collapsed until asked for, so the panel opens
       on inventory rather than on a form. */
    @FXML
    private void handleToggleFilters() {
        boolean showing = !filterPanel.isVisible();
        filterPanel.setVisible(showing);
        filterPanel.setManaged(showing);
        filtersButton.setText(showing ? "Hide filters" : "All filters");
    }

    @FXML
    private void handleSearch() {
        PropertySearch filters = buildFilters();
        if (filters == null) {
            return;
        }
        currentFilters = filters;
        runSearch(0);
    }

    @FXML
    private void handleClearFilters() {
        titleField.clear();
        districtCombo.setValue(null);
        typeCombo.setValue(null);
        dealTypeCombo.setValue(null);
        bedroomsCombo.setValue(null);
        minPriceField.clear();
        maxPriceField.clear();
        minAreaField.clear();
        maxAreaField.clear();
        clearFieldErrors();
        currentFilters = new PropertySearch();
        runSearch(0);
    }

    @FXML
    private void handlePrevious() {
        runSearch(Math.max(0, currentOffset - PAGE_SIZE));
    }

    @FXML
    private void handleNext() {
        runSearch(currentOffset + PAGE_SIZE);
    }

    private PropertySearch buildFilters() {
        clearFieldErrors();

        BigDecimal minPrice = readAmount(minPriceField, minPriceError);
        BigDecimal maxPrice = readAmount(maxPriceField, maxPriceError);
        BigDecimal minArea = readAmount(minAreaField, minAreaError);
        BigDecimal maxArea = readAmount(maxAreaField, maxAreaError);
        checkRange(minPrice, maxPrice, maxPriceField, maxPriceError);
        checkRange(minArea, maxArea, maxAreaField, maxAreaError);
        if (hasFieldError()) {
            return null;
        }

        PropertySearch filters = new PropertySearch();
        District district = districtCombo.getValue();
        filters.setDistrictId(district == null ? null : district.getId());
        PropertyType type = typeCombo.getValue();
        filters.setPropertyTypeId(type == null ? null : type.getId());
        filters.setDealType(dealTypeCombo.getValue());
        filters.setBedrooms(bedroomsCombo.getValue());
        String title = titleField.getText() == null ? "" : titleField.getText().trim();
        filters.setTitleContains(title.isEmpty() ? null : title);
        filters.setMinPrice(minPrice);
        filters.setMaxPrice(maxPrice);
        filters.setMinArea(minArea);
        filters.setMaxArea(maxArea);
        return filters;
    }

    private void checkRange(BigDecimal min, BigDecimal max, TextField maxField, Label maxErrorLabel) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            FieldError.show(maxField, maxErrorLabel, "Must be at least the minimum.");
        }
    }

    private BigDecimal readAmount(TextField field, Label errorLabel) {
        String text = field.getText() == null ? "" : field.getText().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(text);
            if (value.signum() < 0) {
                FieldError.show(field, errorLabel, "Enter a positive number.");
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            FieldError.show(field, errorLabel, "Enter a positive number.");
            return null;
        }
    }

    private boolean hasFieldError() {
        return FieldError.isShown(minPriceError) || FieldError.isShown(maxPriceError)
            || FieldError.isShown(minAreaError) || FieldError.isShown(maxAreaError);
    }

    private void runSearch(int offset) {
        propertyList.setPlaceholder(UIHelper.createEmptyState("No Listings Match Filters", "Try widening search criteria or clearing filter fields."));

        List<Property> results;
        try {
            results = propertyService.searchForStaff(
                List.of(PropertyStatus.AVAILABLE, PropertyStatus.RESERVED, PropertyStatus.UNDER_CONTRACT, PropertyStatus.CLOSED),
                currentFilters, offset, PAGE_SIZE);
        } catch (SQLException e) {
            AlertUtil.showError("Could not load listings. Check that SQL Server is running.");
            return;
        }
        currentOffset = offset;
        propertyList.setItems(FXCollections.observableArrayList(results));
        previousButton.setDisable(currentOffset == 0);
        nextButton.setDisable(results.size() < PAGE_SIZE);
    }

    private String priceText(Property property) {
        return property.getDealType() == DealType.RENT
            ? Format.monthlyRent(property.getAskingPrice())
            : Format.salePrice(property.getAskingPrice());
    }

    private String metaLine(Property property) {
        District district = districtsById.get(property.getDistrictId());
        PropertyType type = typesById.get(property.getPropertyTypeId());
        String districtName = district == null ? "-" : district.getName();
        String typeName = type == null ? "-" : type.getName();
        return districtName + " · " + typeName + " · " + Format.enumLabel(property.getDealType())
            + " · " + Format.area(property.getAreaSqm())
            + " · " + property.getBedrooms() + " beds";
    }

    private final class PropertyCard extends ListCell<Property> {

        @Override
        protected void updateItem(Property property, boolean empty) {
            super.updateItem(property, empty);
            setText(null);
            setGraphic(empty || property == null ? null : buildCard(property));
        }

        private HBox buildCard(Property property) {
            HBox row = new HBox(16);
            row.getStyleClass().addAll("card", "card-hoverable");
            row.setPadding(new Insets(16));
            row.setAlignment(Pos.TOP_LEFT);

            VBox card = new VBox(10);
            HBox.setHgrow(card, Priority.ALWAYS);

            HBox header = new HBox(12);
            header.setAlignment(Pos.CENTER_LEFT);

            Label title = new Label(property.getTitle());
            title.getStyleClass().add("body-medium");
            title.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(title, Priority.ALWAYS);

            Label pill = UIHelper.createStatusPill(property.getStatus());
            Label price = new Label(priceText(property));
            price.getStyleClass().add("price-display");

            header.getChildren().addAll(title, pill, price);

            Label meta = new Label(metaLine(property));
            meta.getStyleClass().add("hint");

            card.getChildren().addAll(header, meta, buildActions(property));

            row.getChildren().addAll(
                UIHelper.createRowThumbnail(firstPhotoPath(property.getId()), 132, 96), card);
            AnimationUtil.addHoverLift(row);
            return row;
        }
        // A row without its photograph is still usable, so a failed lookup is
        // not allowed to take the whole queue down with it.
        private String firstPhotoPath(int propertyId) {
            try {
                List<PropertyPhoto> photos = propertyService.findPhotos(propertyId);
                return photos.isEmpty() ? null : photos.get(0).getFilePath();
            } catch (SQLException e) {
                return null;
            }
        }

        private HBox buildActions(Property property) {
            HBox actions = new HBox(8);
            actions.setAlignment(Pos.CENTER_LEFT);

            Button view = new Button("View details");
            view.getStyleClass().addAll("button", "button-secondary");
            view.setOnAction(event -> Router.show(Panel.PROPERTY_DETAILS, property.getId()));
            actions.getChildren().add(view);

            return actions;
        }
    }

    private void clearFieldErrors() {
        FieldError.clear(minPriceField, minPriceError);
        FieldError.clear(maxPriceField, maxPriceError);
        FieldError.clear(minAreaField, minAreaError);
        FieldError.clear(maxAreaField, maxAreaError);
    }
}
