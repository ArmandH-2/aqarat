package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.dao.AuditDao;
import co.syntropyhq.aqarat.dao.DistrictDao;
import co.syntropyhq.aqarat.dao.PropertyDao;
import co.syntropyhq.aqarat.dao.PropertyMessageDao;
import co.syntropyhq.aqarat.dao.PropertyPhotoDao;
import co.syntropyhq.aqarat.dao.PropertySearch;
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
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

public class BrowseListingsController {

    // 2000 seeded rows behind a scrolling card list - 20 keeps a page short
    // enough to read without paging through a hundred screens for it.
    private static final int PAGE_SIZE = 20;

    @FXML
    private TextField titleField;
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
    private VBox resultsBox;
    @FXML
    private Button previousButton;
    @FXML
    private Button nextButton;

    private final PropertyService propertyService =
        new PropertyService(new PropertyDao(), new PropertyPhotoDao(), new PropertyMessageDao(), new AuditService(new AuditDao()));
    private final ReferenceService referenceService =
        new ReferenceService(new DistrictDao(), new PropertyTypeDao());

    // Cards show a district name, not an id, and a page holds twenty of them.
    // Looking the name up from a map filled once beats twenty queries.
    private final Map<Integer, District> districtsById = new HashMap<>();

    private PropertySearch currentFilters = new PropertySearch();
    private int currentOffset = 0;

    @FXML
    private void initialize() {
        loadReferenceData();
        configureDealTypeCombo();
        configureBedroomsCombo();
        runSearch(0);
    }

    private void loadReferenceData() {
        try {
            List<District> districts = referenceService.findAllDistricts();
            districtCombo.getItems().add(null);
            districtCombo.getItems().addAll(districts);
            districtCombo.setConverter(anyOr("Any district", District::getName));
            for (District district : districts) {
                districtsById.put(district.getId(), district);
            }

            List<PropertyType> types = referenceService.findAllPropertyTypes();
            typeCombo.getItems().add(null);
            typeCombo.getItems().addAll(types);
            typeCombo.setConverter(anyOr("Any type", PropertyType::getName));
        } catch (SQLException e) {
            AlertUtil.showError("Could not load districts and property types.");
        }
    }

    private void configureDealTypeCombo() {
        dealTypeCombo.getItems().addAll(null, DealType.SALE, DealType.RENT);
        dealTypeCombo.setConverter(anyOr("Any", Format::enumLabel));
    }

    private void configureBedroomsCombo() {
        bedroomsCombo.getItems().addAll(null, 0, 1, 2, 3, 4, 5);
        bedroomsCombo.setConverter(anyOr("Any", String::valueOf));
    }

    // Every dropdown carries a null entry meaning "no filter", so all four
    // converters differ only in the word shown for null and how a value is
    // labelled. fromString is never called: these dropdowns are not editable.
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

    // An empty box and an unreadable one both come back null, so the caller
    // asks the error labels whether anything was rejected rather than trying
    // to tell those two apart from the return value.
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
        List<Property> results;
        try {
            results = propertyService.searchPublished(currentFilters, offset, PAGE_SIZE);
        } catch (SQLException e) {
            AlertUtil.showError("Could not load listings. Check that SQL Server is running.");
            return;
        }
        currentOffset = offset;
        renderResults(results);
        previousButton.setDisable(currentOffset == 0);
        nextButton.setDisable(results.size() < PAGE_SIZE);
    }

    private void renderResults(List<Property> results) {
        resultsBox.getChildren().clear();
        if (results.isEmpty()) {
            Label empty = new Label("No listings match these filters.");
            empty.getStyleClass().add("empty-state");
            resultsBox.getChildren().add(empty);
            return;
        }
        for (Property property : results) {
            resultsBox.getChildren().add(buildCard(property));
        }
    }

    private Node buildCard(Property property) {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");
        card.setCursor(Cursor.HAND);

        Label title = new Label(property.getTitle());
        title.getStyleClass().add("section-title");

        District district = districtsById.get(property.getDistrictId());
        String districtName = district == null ? "" : district.getName();
        Label subtitle = new Label(districtName + " · " + Format.enumLabel(property.getDealType()));
        subtitle.getStyleClass().add("label-soft");

        String specsText = property.getBedrooms() + " bed · " + property.getBathrooms()
            + " bath · " + Format.area(property.getAreaSqm());
        Label specs = new Label(specsText);
        specs.getStyleClass().add("hint");

        Label price = new Label(formatPrice(property));
        price.getStyleClass().add("section-title");

        card.getChildren().addAll(title, subtitle, specs, price);
        card.setOnMouseClicked(event -> Router.show(Panel.PROPERTY_DETAILS, property.getId()));
        return card;
    }

    private String formatPrice(Property property) {
        return property.getDealType() == DealType.SALE
            ? Format.salePrice(property.getAskingPrice())
            : Format.monthlyRent(property.getAskingPrice());
    }

    private void clearFieldErrors() {
        FieldError.clear(minPriceField, minPriceError);
        FieldError.clear(maxPriceField, maxPriceError);
        FieldError.clear(minAreaField, minAreaError);
        FieldError.clear(maxAreaField, maxAreaError);
    }
}
