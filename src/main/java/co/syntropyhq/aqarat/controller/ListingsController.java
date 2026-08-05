package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.dao.AuditDao;
import co.syntropyhq.aqarat.dao.DistrictDao;
import co.syntropyhq.aqarat.dao.PropertyDao;
import co.syntropyhq.aqarat.dao.PropertyPhotoDao;
import co.syntropyhq.aqarat.dao.PropertySearch;
import co.syntropyhq.aqarat.dao.PropertyTypeDao;
import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.DealType;
import co.syntropyhq.aqarat.model.District;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.PropertyStatus;
import co.syntropyhq.aqarat.model.PropertyType;
import co.syntropyhq.aqarat.model.Role;
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
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

// The agent's-eye view of every listing: the same filters BrowseListings
// uses, but searched across every status instead of only AVAILABLE
// (DESIGN.md section 9).
public class ListingsController {

    private static final int PAGE_SIZE = 20;

    @FXML
    private VBox contentBox;
    @FXML
    private Label accessDeniedLabel;
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
    private ListView<Property> propertyList;
    @FXML
    private Button previousButton;
    @FXML
    private Button nextButton;

    private final PropertyService propertyService =
        new PropertyService(new PropertyDao(), new PropertyPhotoDao(), new AuditService(new AuditDao()));
    private final ReferenceService referenceService =
        new ReferenceService(new DistrictDao(), new PropertyTypeDao());

    private final Map<Integer, District> districtsById = new HashMap<>();
    private final Map<Integer, PropertyType> typesById = new HashMap<>();

    private PropertySearch currentFilters = new PropertySearch();
    private int currentOffset = 0;

    @FXML
    private void initialize() {
        AppUser currentUser = SessionManager.getCurrentUser();
        if (currentUser == null
                || (currentUser.getRole() != Role.AGENT && currentUser.getRole() != Role.ADMIN)) {
            denyAccess();
            return;
        }
        Label empty = new Label("No listings match these filters.");
        empty.getStyleClass().add("empty-state");
        propertyList.setPlaceholder(empty);
        propertyList.setCellFactory(list -> new ListingCard());
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
            districtCombo.setConverter(anyOr("Any district", District::getName));
            for (District district : districts) {
                districtsById.put(district.getId(), district);
            }

            List<PropertyType> types = referenceService.findAllPropertyTypes();
            typeCombo.getItems().add(null);
            typeCombo.getItems().addAll(types);
            typeCombo.setConverter(anyOr("Any type", PropertyType::getName));
            for (PropertyType type : types) {
                typesById.put(type.getId(), type);
            }
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
            results = propertyService.searchForStaff(
                List.of(PropertyStatus.values()), currentFilters, offset, PAGE_SIZE);
        } catch (SQLException e) {
            AlertUtil.showError("Could not load listings. Check that SQL Server is running.");
            return;
        }
        currentOffset = offset;
        propertyList.setItems(FXCollections.observableArrayList(results));
        previousButton.setDisable(currentOffset == 0);
        nextButton.setDisable(results.size() < PAGE_SIZE);
    }

    private void handleOpen(Property property) {
        Router.show(Panel.PROPERTY_DETAILS, property.getId());
    }

    // An agent takes a listing down in one move. The two-step road through
    // WITHDRAWAL_REQUESTED is the owner's, where the request is the whole
    // point; using it here would record the owner asking for something they
    // never asked for, and could strand the listing mid-way.
    private void handleTakeDown(Property property) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setHeaderText(null);
        dialog.setTitle("Take down listing");
        dialog.setContentText("Reason for taking this listing down:");
        Optional<String> input = dialog.showAndWait();
        if (input.isEmpty()) {
            return;
        }
        String reason = input.get().trim();
        if (reason.isEmpty()) {
            AlertUtil.showError("A reason is required to take a listing down.");
            return;
        }
        try {
            propertyService.review(property.getId(), PropertyStatus.WITHDRAWN, reason);
        } catch (PropertyService.InvalidTransitionException e) {
            AlertUtil.showError(e.getMessage());
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("The listing has been taken down.");
        runSearch(currentOffset);
    }

    // Matches the status-to-pill table in docs/UI-STYLE.md exactly.
    private String pillClass(PropertyStatus status) {
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
        return districtName + " • " + typeName + " • " + Format.enumLabel(property.getDealType())
            + " • " + priceText(property) + " • " + Format.area(property.getAreaSqm());
    }

    private void clearFieldErrors() {
        FieldError.clear(minPriceField, minPriceError);
        FieldError.clear(maxPriceField, maxPriceError);
        FieldError.clear(minAreaField, minAreaError);
        FieldError.clear(maxAreaField, maxAreaError);
    }

    private final class ListingCard extends ListCell<Property> {

        @Override
        protected void updateItem(Property property, boolean empty) {
            super.updateItem(property, empty);
            setText(null);
            setGraphic(empty || property == null ? null : buildCard(property));
        }

        private VBox buildCard(Property property) {
            Label title = new Label(property.getTitle());
            Label pill = new Label(Format.enumLabel(property.getStatus()));
            pill.getStyleClass().addAll("pill", pillClass(property.getStatus()));
            HBox header = new HBox(8, title, pill);

            Label meta = new Label(metaLine(property));
            meta.getStyleClass().add("label-soft");

            VBox card = new VBox(8, header, meta, buildActions(property));
            card.getStyleClass().add("card");
            card.setPadding(new Insets(16));
            return card;
        }

        private HBox buildActions(Property property) {
            HBox actions = new HBox(8);
            Button open = new Button("Open");
            open.getStyleClass().addAll("button", "button-secondary");
            open.setOnAction(event -> handleOpen(property));
            actions.getChildren().add(open);

            if (property.getStatus() == PropertyStatus.AVAILABLE) {
                Button takeDown = new Button("Take down");
                takeDown.getStyleClass().addAll("button", "button-danger");
                takeDown.setOnAction(event -> handleTakeDown(property));
                actions.getChildren().add(takeDown);
            }
            return actions;
        }
    }
}
