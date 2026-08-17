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
import co.syntropyhq.aqarat.model.PropertyPhoto;
import co.syntropyhq.aqarat.model.PropertyType;
import co.syntropyhq.aqarat.service.AuditService;
import co.syntropyhq.aqarat.service.PropertyService;
import co.syntropyhq.aqarat.service.ReferenceService;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.AnimationUtil;
import co.syntropyhq.aqarat.util.FieldError;
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.Panel;
import co.syntropyhq.aqarat.util.Router;
import co.syntropyhq.aqarat.util.UIHelper;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.StringConverter;

public class BrowseListingsController {

    private static final int PAGE_SIZE = 12;

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
    private Label resultsCountLabel;
    @FXML
    private Label pageIndicatorLabel;
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

    private final Map<Integer, District> districtsById = new HashMap<>();
    private final Map<Integer, PropertyType> typesById = new HashMap<>();

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
            districtCombo.setConverter(anyOr("All Districts", District::getName));
            for (District district : districts) {
                districtsById.put(district.getId(), district);
            }

            List<PropertyType> types = referenceService.findAllPropertyTypes();
            typeCombo.getItems().add(null);
            typeCombo.getItems().addAll(types);
            typeCombo.setConverter(anyOr("All Property Types", PropertyType::getName));
            for (PropertyType type : types) {
                typesById.put(type.getId(), type);
            }
        } catch (SQLException e) {
            AlertUtil.showError("Could not load districts and property types.");
        }
    }

    private void configureDealTypeCombo() {
        dealTypeCombo.getItems().addAll(null, DealType.SALE, DealType.RENT);
        dealTypeCombo.setConverter(anyOr("Sale & Rent", Format::enumLabel));
    }

    private void configureBedroomsCombo() {
        bedroomsCombo.getItems().addAll(null, 0, 1, 2, 3, 4, 5);
        bedroomsCombo.setConverter(anyOr("Any Bedrooms", val -> val == 0 ? "Studio (0 bed)" : val + "+ Bedrooms"));
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
        List<Property> results;
        try {
            results = propertyService.searchPublished(currentFilters, offset, PAGE_SIZE);
        } catch (SQLException e) {
            AlertUtil.showError("Could not load listings. Check that SQL Server is running.");
            return;
        }
        currentOffset = offset;
        renderResults(results);

        int currentPage = (currentOffset / PAGE_SIZE) + 1;
        pageIndicatorLabel.setText("Page " + currentPage);
        previousButton.setDisable(currentOffset == 0);
        nextButton.setDisable(results.size() < PAGE_SIZE);
    }

    private void renderResults(List<Property> results) {
        resultsBox.getChildren().clear();
        resultsCountLabel.setText(results.size() + (results.size() == 1 ? " property" : " properties"));

        if (results.isEmpty()) {
            resultsBox.getChildren().add(
                UIHelper.createEmptyState("No listings match these filters", "Try expanding your search parameters or clearing filters.")
            );
            return;
        }

        Node[] cards = new Node[results.size()];
        for (int i = 0; i < results.size(); i++) {
            Node card = buildRichPropertyCard(results.get(i));
            cards[i] = card;
            resultsBox.getChildren().add(card);
        }
        AnimationUtil.staggerIn(cards, 40);
    }

    private Node buildRichPropertyCard(Property property) {
        HBox card = new HBox(16);
        card.getStyleClass().addAll("card", "card-hoverable");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(14));
        card.setCursor(Cursor.HAND);

        // Photo Thumbnail
        StackPane photoContainer = new StackPane();
        photoContainer.setPrefSize(140, 100);
        photoContainer.setMinSize(140, 100);
        photoContainer.setMaxSize(140, 100);
        photoContainer.setStyle("-fx-background-color: -c-surface-subtle; -fx-background-radius: 8px; -fx-border-color: -c-border-subtle; -fx-border-radius: 8px;");

        ImageView thumbnail = new ImageView();
        thumbnail.setFitWidth(140);
        thumbnail.setFitHeight(100);
        thumbnail.setPreserveRatio(false);

        Rectangle clip = new Rectangle(140, 100);
        clip.setArcWidth(16);
        clip.setArcHeight(16);
        thumbnail.setClip(clip);

        try {
            List<PropertyPhoto> photos = propertyService.findPhotos(property.getId());
            if (!photos.isEmpty()) {
                File photoFile = new File("uploads/" + photos.get(0).getFilePath());
                if (photoFile.exists()) {
                    thumbnail.setImage(new Image(photoFile.toURI().toString(), 140, 100, false, true));
                }
            }
        } catch (Exception ignored) {
        }

        if (thumbnail.getImage() != null) {
            photoContainer.getChildren().add(thumbnail);
        } else {
            Label placeholder = new Label("🏠");
            placeholder.setStyle("-fx-font-size: 28px; -fx-opacity: 0.6;");
            photoContainer.getChildren().add(placeholder);
        }

        // Details Container
        VBox details = new VBox(6);
        HBox.setHgrow(details, Priority.ALWAYS);

        // Title and Deal Type Badge
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(property.getTitle());
        title.getStyleClass().add("section-title");
        HBox.setHgrow(title, Priority.ALWAYS);

        Label dealBadge = new Label(Format.enumLabel(property.getDealType()));
        dealBadge.getStyleClass().addAll("pill", property.getDealType() == DealType.SALE ? "pill-good" : "pill-info");

        header.getChildren().addAll(title, dealBadge);

        // Location & Type line
        District district = districtsById.get(property.getDistrictId());
        PropertyType type = typesById.get(property.getPropertyTypeId());
        String locationStr = (district != null ? district.getName() : "Lebanon")
            + " • " + (type != null ? type.getName() : "Property");
        Label subtitle = new Label(locationStr);
        subtitle.getStyleClass().add("label-soft");

        // Spec chips
        HBox specChips = new HBox(8);
        specChips.setAlignment(Pos.CENTER_LEFT);
        specChips.getChildren().add(UIHelper.createSpecChip(property.getBedrooms() + " Beds"));
        specChips.getChildren().add(UIHelper.createSpecChip(property.getBathrooms() + " Baths"));
        specChips.getChildren().add(UIHelper.createSpecChip(Format.area(property.getAreaSqm())));
        if (property.isHasParking()) {
            specChips.getChildren().add(UIHelper.createSpecChip("Parking"));
        }

        details.getChildren().addAll(header, subtitle, specChips);

        // Price Callout Box
        VBox priceBox = new VBox(2);
        priceBox.setAlignment(Pos.CENTER_RIGHT);
        priceBox.setPrefWidth(160);

        Label price = new Label(formatPrice(property));
        price.getStyleClass().add("section-title");
        price.setStyle("-fx-font-size: 17px; -fx-font-weight: 700; -fx-text-fill: -c-primary;");

        BigDecimal pricePerSqm = BigDecimal.ZERO;
        if (property.getAreaSqm() != null && property.getAreaSqm().compareTo(BigDecimal.ZERO) > 0 && property.getAskingPrice() != null) {
            pricePerSqm = property.getAskingPrice().divide(property.getAreaSqm(), 0, RoundingMode.HALF_UP);
        }
        Label pricePerSqmLabel = new Label("$" + pricePerSqm + "/m²");
        pricePerSqmLabel.getStyleClass().add("hint");

        priceBox.getChildren().addAll(price, pricePerSqmLabel);

        card.getChildren().addAll(photoContainer, details, priceBox);
        card.setOnMouseClicked(event -> Router.show(Panel.PROPERTY_DETAILS, property.getId()));
        AnimationUtil.addHoverLift(card);
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
