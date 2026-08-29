package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.ai.AssistantUnavailableException;
import co.syntropyhq.aqarat.ai.ChatClient;
import co.syntropyhq.aqarat.ai.ChatMessage;
import co.syntropyhq.aqarat.ai.Conversation;
import co.syntropyhq.aqarat.ai.QueryRouter;
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
import co.syntropyhq.aqarat.model.PropertyStatus;
import co.syntropyhq.aqarat.model.PropertyType;
import co.syntropyhq.aqarat.service.AssistantService;
import co.syntropyhq.aqarat.service.AuditService;
import co.syntropyhq.aqarat.service.PropertyService;
import co.syntropyhq.aqarat.service.ReferenceService;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.AnimationUtil;
import co.syntropyhq.aqarat.util.FieldError;
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.SessionManager;
import co.syntropyhq.aqarat.util.UIHelper;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Discover — searching and browsing published listings.
 *
 * <p>This panel replaces the separate Browse Listings and Assistant screens.
 * They were two doors onto the same catalogue, which forced a visitor to choose
 * a search style before seeing anything. Here one field accepts a sentence; the
 * assistant turns it into filters, those filters are shown as chips that can be
 * removed one at a time, and the results land in the same grid either way.
 *
 * <p>When no model is configured the field still works as a keyword search, so
 * the panel is fully usable with {@code ai.enabled=false}.
 */
public class DiscoverController {

    private static final int PAGE_SIZE = 12;

    @FXML
    private Label catalogueLabel;
    @FXML
    private TextField searchField;
    @FXML
    private Button searchButton;
    @FXML
    private FontIcon searchIcon;
    @FXML
    private VBox readingRow;
    @FXML
    private Label readingLabel;
    @FXML
    private FlowPane chipBar;
    @FXML
    private Button filtersButton;
    @FXML
    private VBox filterPanel;
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
    private Button previousButton;
    @FXML
    private Button nextButton;
    @FXML
    private VBox statusBox;
    @FXML
    private FlowPane resultsGrid;
    @FXML
    private ScrollPane resultsScroll;

    private final PropertyService propertyService = new PropertyService(
        new PropertyDao(), new PropertyPhotoDao(), new PropertyMessageDao(),
        new AuditService(new AuditDao()));
    private final ReferenceService referenceService =
        new ReferenceService(new DistrictDao(), new PropertyTypeDao());
    private final AssistantService assistantService = new AssistantService(propertyService);

    private final Map<Integer, District> districtsById = new HashMap<>();
    private final Map<Integer, PropertyType> typesById = new HashMap<>();
    private List<District> districts = List.of();
    private List<PropertyType> propertyTypes = List.of();

    /* One conversation for the life of the panel, so "cheaper" or "closer to
       the sea" refines the previous answer instead of starting over. */
    private final Conversation conversation = new Conversation();

    private PropertySearch currentFilters = new PropertySearch();
    private int currentOffset;
    private int totalMatches;
    private int catalogueSize;
    private Timeline promptIntro;

    @FXML
    private void initialize() {
        loadReferenceData();
        configureCombos();
        introducePrompt();
        // Re-flow the grid whenever the window changes width.
        resultsGrid.widthProperty().addListener((observable, was, now) -> applyCardWidths());
        runSearch(0);
    }

    // ------------------------------------------------------------------ setup

    private void loadReferenceData() {
        try {
            districts = referenceService.findAllDistricts();
            propertyTypes = referenceService.findAllPropertyTypes();
        } catch (SQLException e) {
            AlertUtil.showError("Could not load districts and property types.");
            return;
        }
        districtCombo.getItems().add(null);
        districtCombo.getItems().addAll(districts);
        districtCombo.setConverter(anyOr("Any district", District::getName));
        districts.forEach(district -> districtsById.put(district.getId(), district));

        typeCombo.getItems().add(null);
        typeCombo.getItems().addAll(propertyTypes);
        typeCombo.setConverter(anyOr("Any type", PropertyType::getName));
        propertyTypes.forEach(type -> typesById.put(type.getId(), type));
    }

    private void configureCombos() {
        dealTypeCombo.getItems().addAll(null, DealType.SALE, DealType.RENT);
        dealTypeCombo.setConverter(anyOr("Sale and lease", Format::enumLabel));

        bedroomsCombo.getItems().addAll(null, 0, 1, 2, 3, 4, 5);
        bedroomsCombo.setConverter(anyOr("Any bedrooms",
            value -> value == 0 ? "Studio" : value + "+ bedrooms"));
    }

    private static <T> StringConverter<T> anyOr(String anyLabel, Function<T, String> label) {
        return new StringConverter<>() {
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

    // ------------------------------------------------------------- the prompt

    /**
     * Tells the visitor what this field accepts.
     *
     * <p>Nothing about a text box says "you may write a sentence here", and the
     * old placeholder — a comma-separated list of filters — actively suggested
     * the opposite. Where the assistant is available the prompt is written in
     * the first person and typed out once, because the motion is what makes
     * someone read it; where it is not, the prompt says plainly that this is a
     * keyword search rather than promising an assistant that will not answer.
     *
     * <p>It types once and stops. A looping animation in the corner of the eye
     * competes with the photographs, which are the actual content, and JavaFX
     * offers no equivalent of prefers-reduced-motion for a viewer who needs it
     * to stop.
     */
    private void introducePrompt() {
        if (!assistantAvailable()) {
            searchIcon.setIconLiteral("fth-search");
            searchField.setPromptText("Search by district, property type or price");
            return;
        }

        searchIcon.setIconLiteral("fth-message-square");
        String invitation = "I'm looking for a family home in Achrafieh under $400,000";

        promptIntro = new Timeline();
        for (int i = 1; i <= invitation.length(); i++) {
            String shown = invitation.substring(0, i);
            promptIntro.getKeyFrames().add(new KeyFrame(
                Duration.millis(26.0 * i), event -> searchField.setPromptText(shown)));
        }
        promptIntro.setOnFinished(event -> promptIntro = null);

        // Anyone who starts typing has already understood the invitation, so it
        // gets out of the way rather than animating underneath them.
        searchField.textProperty().addListener((observable, was, now) -> settlePrompt(invitation));
        searchField.focusedProperty().addListener((observable, was, focused) -> {
            if (focused) {
                settlePrompt(invitation);
            }
        });

        searchField.setPromptText("");
        promptIntro.play();
    }

    private void settlePrompt(String invitation) {
        if (promptIntro != null) {
            promptIntro.stop();
            promptIntro = null;
            searchField.setPromptText(invitation);
        }
    }

    // ----------------------------------------------------------------- search

    /**
     * Whether this visitor may spend a model call.
     *
     * <p>Signing in is required as well as configuration: every conversation is
     * billed to the agency's API key, so it stays behind a name the spend can be
     * attributed to. A guest still gets the keyword search and every filter.
     */
    private boolean assistantAvailable() {
        return ChatClient.isEnabled() && SessionManager.getCurrentUser() != null;
    }

    @FXML
    private void handleSearch() {
        String phrase = searchField.getText() == null ? "" : searchField.getText().trim();
        if (phrase.isEmpty()) {
            currentFilters = new PropertySearch();
            hideReading();
            runSearch(0);
            return;
        }

        // Most searches in a property application are filters typed in a hurry —
        // a district, a bedroom count, a ceiling price. Where the phrase is
        // entirely made of those, it is answered here: no model call, no wait,
        // and it works for a guest and with ai.enabled=false. Anything the
        // router does not fully recognise, including a typo, goes to the
        // assistant untouched.
        Optional<QueryRouter.Routed> routed =
            QueryRouter.route(phrase, districts, propertyTypes);
        if (routed.isPresent()) {
            currentFilters = routed.get().filters();
            syncControlsFromFilters();
            showReading(routed.get().explanation());
            runSearch(0);
            return;
        }

        if (assistantAvailable()) {
            askAssistant(phrase);
            return;
        }

        currentFilters = new PropertySearch();
        currentFilters.setTitleContains(phrase);
        showReading("Matching the words \"" + phrase + "\" in listing titles.");
        runSearch(0);
    }

    /**
     * Sends the phrase to the assistant, which answers with the filters it
     * understood. The call is a network round trip, so it runs off the UI thread
     * and the field is disabled while it is in flight.
     */
    private void askAssistant(String phrase) {
        setThinking(true);
        conversation.append(ChatMessage.user(phrase));

        Task<AssistantService.Response> task = new Task<>() {
            @Override
            protected AssistantService.Response call()
                    throws AssistantUnavailableException, SQLException {
                return assistantService.respond(conversation, currentFilters, districts, propertyTypes);
            }
        };

        task.setOnSucceeded(event -> {
            setThinking(false);
            AssistantService.Response response = task.getValue();
            if (response.getUpdatedFilters() != null) {
                currentFilters = response.getUpdatedFilters();
                syncControlsFromFilters();
            }
            showReading(response.getMessageText());
            runSearch(0);
        });

        // A model that is unreachable must not cost the visitor their search:
        // the phrase falls back to a keyword match and the panel says so.
        task.setOnFailed(event -> {
            setThinking(false);
            currentFilters = new PropertySearch();
            currentFilters.setTitleContains(phrase);
            showReading("The assistant is unavailable, so this is a keyword match on \""
                + phrase + "\".");
            runSearch(0);
        });

        Thread worker = new Thread(task, "discover-assistant");
        worker.setDaemon(true);
        worker.start();
    }

    private void setThinking(boolean thinking) {
        searchButton.setDisable(thinking);
        searchField.setDisable(thinking);
        searchButton.setText(thinking ? "Thinking…" : "Search");
    }

    @FXML
    private void handleApplyFilters() {
        PropertySearch filters = buildFiltersFromControls();
        if (filters == null) {
            return;
        }
        currentFilters = filters;
        hideReading();
        runSearch(0);
    }

    @FXML
    private void handleClearFilters() {
        searchField.clear();
        districtCombo.setValue(null);
        typeCombo.setValue(null);
        dealTypeCombo.setValue(null);
        bedroomsCombo.setValue(null);
        minPriceField.clear();
        maxPriceField.clear();
        minAreaField.clear();
        maxAreaField.clear();
        clearFieldErrors();
        conversation.clear();
        currentFilters = new PropertySearch();
        hideReading();
        runSearch(0);
    }

    @FXML
    private void handleToggleFilters() {
        boolean showing = !filterPanel.isVisible();
        filterPanel.setVisible(showing);
        filterPanel.setManaged(showing);
        filtersButton.setText(showing ? "Hide filters" : "All filters");
    }

    @FXML
    private void handlePrevious() {
        runSearch(Math.max(0, currentOffset - PAGE_SIZE));
    }

    @FXML
    private void handleNext() {
        runSearch(currentOffset + PAGE_SIZE);
    }

    private void runSearch(int offset) {
        List<Property> results;
        int matches;
        try {
            results = propertyService.searchPublished(currentFilters, offset, PAGE_SIZE);
            matches = propertyService.count(List.of(PropertyStatus.AVAILABLE), currentFilters);
            if (catalogueSize == 0) {
                // The eyebrow describes the whole catalogue, so it is counted
                // once without filters rather than following the result set.
                catalogueSize = propertyService.count(
                    List.of(PropertyStatus.AVAILABLE), new PropertySearch());
                catalogueLabel.setText(Format.count(catalogueSize).toUpperCase()
                    + (catalogueSize == 1 ? " LISTING" : " LISTINGS") + " ACROSS LEBANON");
            }
        } catch (SQLException e) {
            AlertUtil.showError("Could not load listings. Check that SQL Server is running.");
            return;
        }
        currentOffset = offset;
        totalMatches = matches;
        renderChips();
        renderResults(results);

        int lastPage = Math.max(1, (int) Math.ceil(totalMatches / (double) PAGE_SIZE));
        int currentPage = (currentOffset / PAGE_SIZE) + 1;
        pageIndicatorLabel.setText("Page " + currentPage + " of " + lastPage);
        previousButton.setDisable(currentOffset == 0);
        nextButton.setDisable(currentOffset + PAGE_SIZE >= totalMatches);
        resultsScroll.setVvalue(0);
    }

    // ---------------------------------------------------------------- render

    private void renderResults(List<Property> results) {
        resultsGrid.getChildren().clear();
        resultsCountLabel.setText(totalMatches == 1
            ? "1 property matches"
            : Format.count(totalMatches) + " properties match");

        if (results.isEmpty()) {
            resultsGrid.getChildren().add(UIHelper.createEmptyState(
                "Nothing matches yet",
                "Try removing a filter above, or describe what you are looking for in your own words."));
            return;
        }

        Node[] cards = new Node[results.size()];
        for (int i = 0; i < results.size(); i++) {
            Property property = results.get(i);
            cards[i] = UIHelper.createListingCard(
                property,
                districtsById.get(property.getDistrictId()),
                typesById.get(property.getPropertyTypeId()),
                firstPhotoPath(property.getId()));
            resultsGrid.getChildren().add(cards[i]);
        }
        applyCardWidths();
        AnimationUtil.staggerIn(cards, 35);
    }

    /**
     * Sizes every card so a whole number of columns fills the grid exactly.
     *
     * <p>Fixed-width cards leave a ragged gutter that grows with the window,
     * and lose a column entirely when a scrollbar appears and takes the last
     * few pixels. Deriving the width from the space actually available means
     * the grid is always flush and never drops a column by a hair.
     */
    private void applyCardWidths() {
        // A couple of pixels are held back: the pane reports its full width, but
        // rounding and the scrollbar leave slightly less to lay out in, and
        // overshooting by one pixel costs a whole column.
        double available = resultsGrid.getWidth() - 4;
        if (available <= 0) {
            return;
        }
        int columns = Math.max(1,
            (int) Math.floor((available + UIHelper.CARD_GAP)
                / (UIHelper.CARD_MIN_WIDTH + UIHelper.CARD_GAP)));
        double width = Math.floor(
            (available - (columns - 1) * UIHelper.CARD_GAP) / columns);

        for (Node node : resultsGrid.getChildren()) {
            if (node instanceof Region card && card.getStyleClass().contains("listing-card")) {
                card.setMinWidth(width);
                card.setPrefWidth(width);
                card.setMaxWidth(width);
            }
        }
    }

    /**
     * Chips for whatever is currently filtering the list, each removable.
     *
     * <p>This is what replaces the wall of dropdowns: the common case is that
     * nothing is filtered and the row is empty, and the moment something is
     * filtered it is named in one line rather than hidden in eight controls.
     */
    private void renderChips() {
        chipBar.getChildren().clear();

        addChip(districtLabel(currentFilters.getDistrictId()), () -> currentFilters.setDistrictId(null));
        addChip(typeLabel(currentFilters.getPropertyTypeId()), () -> currentFilters.setPropertyTypeId(null));
        if (currentFilters.getDealType() != null) {
            addChip(Format.enumLabel(currentFilters.getDealType()), () -> currentFilters.setDealType(null));
        }
        if (currentFilters.getBedrooms() != null) {
            addChip(currentFilters.getBedrooms() == 0
                ? "Studio" : currentFilters.getBedrooms() + "+ bedrooms",
                () -> currentFilters.setBedrooms(null));
        }
        addChip(rangeLabel("$", currentFilters.getMinPrice(), currentFilters.getMaxPrice()), () -> {
            currentFilters.setMinPrice(null);
            currentFilters.setMaxPrice(null);
        });
        addChip(rangeLabel("", currentFilters.getMinArea(), currentFilters.getMaxArea()), () -> {
            currentFilters.setMinArea(null);
            currentFilters.setMaxArea(null);
        });
        if (currentFilters.getTitleContains() != null) {
            addChip("“" + currentFilters.getTitleContains() + "”",
                () -> currentFilters.setTitleContains(null));
        }
        updateReadingRow();
    }

    private void addChip(String text, Runnable clear) {
        if (text == null) {
            return;
        }
        Label label = new Label(text + "  ×");
        label.getStyleClass().add("filter-chip");
        label.setOnMouseClicked(event -> {
            clear.run();
            syncControlsFromFilters();
            runSearch(0);
        });
        chipBar.getChildren().add(label);
    }

    private String districtLabel(Integer districtId) {
        District district = districtId == null ? null : districtsById.get(districtId);
        return district == null ? null : district.getName();
    }

    private String typeLabel(Integer typeId) {
        PropertyType type = typeId == null ? null : typesById.get(typeId);
        return type == null ? null : type.getName();
    }

    private String rangeLabel(String prefix, BigDecimal min, BigDecimal max) {
        if (min == null && max == null) {
            return null;
        }
        String unit = prefix.isEmpty() ? " m²" : "";
        if (min != null && max != null) {
            return prefix + trim(min) + "–" + prefix + trim(max) + unit;
        }
        return min != null
            ? "From " + prefix + trim(min) + unit
            : "Up to " + prefix + trim(max) + unit;
    }

    private String trim(BigDecimal value) {
        return Format.count(value.intValue());
    }

    private void showReading(String message) {
        boolean hasMessage = message != null && !message.isBlank();
        readingLabel.setText(hasMessage ? message : "");
        readingLabel.setVisible(hasMessage);
        readingLabel.setManaged(hasMessage);
        updateReadingRow();
    }

    private void hideReading() {
        readingLabel.setText("");
        readingLabel.setVisible(false);
        readingLabel.setManaged(false);
        updateReadingRow();
    }

    /* The row exists only when it has something to say — either an explanation
       from the assistant or at least one chip. */
    private void updateReadingRow() {
        boolean show = readingLabel.isManaged() || !chipBar.getChildren().isEmpty();
        readingRow.setVisible(show);
        readingRow.setManaged(show);
    }

    /** Keeps the collapsed filter controls in step with filters the assistant set. */
    private void syncControlsFromFilters() {
        districtCombo.setValue(currentFilters.getDistrictId() == null
            ? null : districtsById.get(currentFilters.getDistrictId()));
        typeCombo.setValue(currentFilters.getPropertyTypeId() == null
            ? null : typesById.get(currentFilters.getPropertyTypeId()));
        dealTypeCombo.setValue(currentFilters.getDealType());
        bedroomsCombo.setValue(currentFilters.getBedrooms());
        minPriceField.setText(text(currentFilters.getMinPrice()));
        maxPriceField.setText(text(currentFilters.getMaxPrice()));
        minAreaField.setText(text(currentFilters.getMinArea()));
        maxAreaField.setText(text(currentFilters.getMaxArea()));
    }

    private String text(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    // --------------------------------------------------------------- filters

    private PropertySearch buildFiltersFromControls() {
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
        filters.setMinPrice(minPrice);
        filters.setMaxPrice(maxPrice);
        filters.setMinArea(minArea);
        filters.setMaxArea(maxArea);

        String phrase = searchField.getText() == null ? "" : searchField.getText().trim();
        filters.setTitleContains(phrase.isEmpty() || assistantAvailable() ? null : phrase);
        return filters;
    }

    private void checkRange(BigDecimal min, BigDecimal max, TextField maxField, Label maxErrorLabel) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            FieldError.show(maxField, maxErrorLabel, "Must be at least the minimum.");
        }
    }

    private BigDecimal readAmount(TextField field, Label errorLabel) {
        String text = field.getText() == null ? "" : field.getText().trim().replace(",", "");
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

    private void clearFieldErrors() {
        FieldError.clear(minPriceField, minPriceError);
        FieldError.clear(maxPriceField, maxPriceError);
        FieldError.clear(minAreaField, minAreaError);
        FieldError.clear(maxAreaField, maxAreaError);
    }

    // A card without its photograph is still a usable card, so a lookup that
    // fails is not allowed to take the whole result grid down with it.
    private String firstPhotoPath(int propertyId) {
        try {
            List<PropertyPhoto> photos = propertyService.findPhotos(propertyId);
            return photos.isEmpty() ? null : photos.get(0).getFilePath();
        } catch (SQLException e) {
            return null;
        }
    }
}
