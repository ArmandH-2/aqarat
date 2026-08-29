package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.ai.ChatClient;
import co.syntropyhq.aqarat.ai.ChatMessage;
import co.syntropyhq.aqarat.ai.Conversation;
import co.syntropyhq.aqarat.ai.Suggestion;
import co.syntropyhq.aqarat.dao.AuditDao;
import co.syntropyhq.aqarat.dao.DistrictDao;
import co.syntropyhq.aqarat.dao.PropertyDao;
import co.syntropyhq.aqarat.dao.PropertyMessageDao;
import co.syntropyhq.aqarat.dao.PropertyPhotoDao;
import co.syntropyhq.aqarat.dao.PropertySearch;
import co.syntropyhq.aqarat.dao.PropertyTypeDao;
import co.syntropyhq.aqarat.model.District;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.PropertyPhoto;
import co.syntropyhq.aqarat.model.PropertyType;
import co.syntropyhq.aqarat.service.AssistantService;
import co.syntropyhq.aqarat.service.AuditService;
import co.syntropyhq.aqarat.service.PropertyService;
import co.syntropyhq.aqarat.service.ReferenceService;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.AnimationUtil;
import co.syntropyhq.aqarat.util.SessionManager;
import co.syntropyhq.aqarat.util.UIHelper;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class AssistantController {

    @FXML
    private ScrollPane scrollPane;
    @FXML
    private VBox transcriptBox;
    @FXML
    private TextField inputField;
    @FXML
    private Button sendButton;
    @FXML
    private Button startOverButton;

    private final PropertyService propertyService =
        new PropertyService(new PropertyDao(), new PropertyPhotoDao(), new PropertyMessageDao(), new AuditService(new AuditDao()));
    private final ReferenceService referenceService =
        new ReferenceService(new DistrictDao(), new PropertyTypeDao());
    private final AssistantService assistantService =
        new AssistantService(propertyService);

    private final List<District> districts = new ArrayList<>();
    private final List<PropertyType> propertyTypes = new ArrayList<>();
    private final Map<Integer, District> districtsById = new HashMap<>();
    private final Map<Integer, PropertyType> typesById = new HashMap<>();

    // A conversation is capped so a stuck user, or a held-down Enter key, cannot
    // run up an unbounded bill. Start over resets it.
    private static final int MAX_TURNS = 20;

    private int turnsUsed;

    private final Conversation conversation = new Conversation();
    private PropertySearch carriedFilters = new PropertySearch();
    private Node typingIndicatorNode;

    @FXML
    private void initialize() {
        loadReferenceData();

        if (!ChatClient.isEnabled()) {
            inputField.setDisable(true);
            sendButton.setDisable(true);
            startOverButton.setDisable(true);
            transcriptBox.getChildren().add(
                UIHelper.createEmptyState(
                    "Search Assistant Unavailable",
                    "The assistant is not configured or disabled. You can still search with filters on Browse listings."
                )
            );
            return;
        }

        // Reached only if a guest navigates here directly; the sidebar does not
        // offer it. Every turn spends against the agency key, so it stays behind
        // a signed-in account.
        if (SessionManager.getCurrentUser() == null) {
            lockInput();
            transcriptBox.getChildren().add(
                UIHelper.createEmptyState(
                    "Sign in to use the assistant",
                    "The search assistant is available to registered users. You can browse and filter every listing without an account."
                )
            );
            return;
        }

        renderGreeting();
    }

    private void loadReferenceData() {
        try {
            districts.clear();
            districts.addAll(referenceService.findAllDistricts());
            for (District d : districts) {
                districtsById.put(d.getId(), d);
            }

            propertyTypes.clear();
            propertyTypes.addAll(referenceService.findAllPropertyTypes());
            for (PropertyType t : propertyTypes) {
                typesById.put(t.getId(), t);
            }
        } catch (SQLException e) {
            AlertUtil.showError("Could not load districts and property types.");
        }
    }

    private void renderGreeting() {
        renderAssistantBubble(
            "Hello! Describe what kind of property you are looking for (location, budget, bedrooms, or amenities) and I will find matching listings for you."
        );
    }

    @FXML
    private void handleSend() {
        String text = inputField.getText() == null ? "" : inputField.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        if (turnsUsed >= MAX_TURNS) {
            return;
        }
        turnsUsed++;

        renderUserBubble(text);
        conversation.append(ChatMessage.user(text));
        inputField.clear();

        inputField.setDisable(true);
        sendButton.setDisable(true);
        showTypingIndicator();

        Task<AssistantService.Response> task = new Task<>() {
            @Override
            protected AssistantService.Response call() throws Exception {
                return assistantService.respond(conversation, carriedFilters, districts, propertyTypes);
            }
        };

        task.setOnSucceeded(event -> {
            hideTypingIndicator();
            unlockInputUnlessCapped();

            AssistantService.Response response = task.getValue();
            if (response != null) {
                carriedFilters = response.getUpdatedFilters();
                if (response.getMessageText() != null && !response.getMessageText().isBlank()) {
                    renderAssistantBubble(response.getMessageText());
                }
                renderSuggestions(response.getSuggestions());
            }
            scrollToBottom();
            Platform.runLater(() -> inputField.requestFocus());
        });

        task.setOnFailed(event -> {
            hideTypingIndicator();
            unlockInputUnlessCapped();

            Throwable ex = task.getException();
            if (isSqlException(ex)) {
                AlertUtil.showError("Could not load listings. Check that SQL Server is running.");
            } else {
                renderAssistantBubble("The assistant is unavailable right now. You can still search with filters on Browse listings.");
            }
            scrollToBottom();
            Platform.runLater(() -> inputField.requestFocus());
        });

        Thread backgroundThread = new Thread(task, "AssistantServiceTask");
        backgroundThread.setDaemon(true);
        backgroundThread.start();
    }

    private boolean isSqlException(Throwable t) {
        while (t != null) {
            if (t instanceof SQLException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    @FXML
    private void handleStartOver() {
        conversation.clear();
        carriedFilters = new PropertySearch();
        turnsUsed = 0;
        inputField.setPromptText("Ask anything (e.g. 3 bedroom apartment in Beirut under 300k)...");
        transcriptBox.getChildren().clear();
        inputField.setDisable(false);
        sendButton.setDisable(false);
        renderGreeting();
        inputField.clear();
        inputField.requestFocus();
    }

    private void renderUserBubble(String text) {
        HBox container = new HBox();
        container.setAlignment(Pos.CENTER_RIGHT);

        Label bubble = new Label(text);
        bubble.getStyleClass().add("chat-bubble-user");
        bubble.setMaxWidth(620);
        bubble.setWrapText(true);

        container.getChildren().add(bubble);
        transcriptBox.getChildren().add(container);
        scrollToBottom();
    }

    private void renderAssistantBubble(String text) {
        HBox container = new HBox();
        container.setAlignment(Pos.CENTER_LEFT);

        Label bubble = new Label(text);
        bubble.getStyleClass().add("chat-bubble-assistant");
        bubble.setMaxWidth(680);
        bubble.setWrapText(true);

        container.getChildren().add(bubble);
        transcriptBox.getChildren().add(container);
        scrollToBottom();
    }

    private void renderSuggestions(List<Suggestion> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            return;
        }

        VBox cardsContainer = new VBox(10);
        cardsContainer.setPadding(new Insets(4, 0, 4, 16));

        List<Node> cardNodes = new ArrayList<>();
        for (Suggestion suggestion : suggestions) {
            Node card = buildSuggestionCard(suggestion);
            cardsContainer.getChildren().add(card);
            cardNodes.add(card);
        }

        transcriptBox.getChildren().add(cardsContainer);
        AnimationUtil.staggerIn(cardNodes.toArray(new Node[0]), 35);
        scrollToBottom();
    }

    private Node buildSuggestionCard(Suggestion suggestion) {
        Property property = suggestion.getProperty();
        Node card = UIHelper.createPropertyCard(
            property,
            districtsById.get(property.getDistrictId()),
            typesById.get(property.getPropertyTypeId()),
            firstPhotoPath(property.getId()),
            suggestion.getReason());
        // Chat is a narrow column; a full-width card reads as a page, not a reply.
        ((HBox) card).setMaxWidth(800);
        return card;
    }

    // A card without its thumbnail is still a usable card, so a photo lookup
    // that fails is not allowed to take the whole reply down with it.
    private String firstPhotoPath(int propertyId) {
        try {
            List<PropertyPhoto> photos = propertyService.findPhotos(propertyId);
            return photos.isEmpty() ? null : photos.get(0).getFilePath();
        } catch (SQLException e) {
            return null;
        }
    }

    private void lockInput() {
        inputField.setDisable(true);
        sendButton.setDisable(true);
    }

    private void unlockInputUnlessCapped() {
        if (turnsUsed >= MAX_TURNS) {
            lockInput();
            inputField.setPromptText("Conversation limit reached - use Start over to begin a new one.");
            renderAssistantBubble("That is as far as one conversation goes. "
                + "Choose Start over above to begin a new search.");
            return;
        }
        inputField.setDisable(false);
        sendButton.setDisable(false);
    }

    private void showTypingIndicator() {
        HBox container = new HBox();
        container.setAlignment(Pos.CENTER_LEFT);

        Label typing = new Label("Assistant is thinking...");
        typing.getStyleClass().add("chat-typing");

        container.getChildren().add(typing);
        typingIndicatorNode = container;
        transcriptBox.getChildren().add(container);
        scrollToBottom();
    }

    private void hideTypingIndicator() {
        if (typingIndicatorNode != null) {
            transcriptBox.getChildren().remove(typingIndicatorNode);
            typingIndicatorNode = null;
        }
    }

    private void scrollToBottom() {
        Platform.runLater(() -> {
            scrollPane.layout();
            scrollPane.setVvalue(1.0);
        });
    }
}
