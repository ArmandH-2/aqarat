package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.dao.AuditDao;
import co.syntropyhq.aqarat.dao.PropertyDao;
import co.syntropyhq.aqarat.dao.PropertyMessageDao;
import co.syntropyhq.aqarat.dao.PropertyPhotoDao;
import co.syntropyhq.aqarat.dao.UserDao;
import co.syntropyhq.aqarat.dao.ViewingDao;
import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.Role;
import co.syntropyhq.aqarat.model.Viewing;
import co.syntropyhq.aqarat.model.ViewingStatus;
import co.syntropyhq.aqarat.service.AuditService;
import co.syntropyhq.aqarat.service.AuthService;
import co.syntropyhq.aqarat.service.PropertyService;
import co.syntropyhq.aqarat.service.ViewingService;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.SessionManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

// One list with a toggle between "Requests" and "My viewings", the same
// shape ReviewQueueController uses for "Unassigned" and "My queue" - both
// tabs show the same row, only which viewings and which actions differ.
public class ViewingsController {

    @FXML
    private VBox contentBox;
    @FXML
    private Label accessDeniedLabel;
    @FXML
    private Button requestsTabButton;
    @FXML
    private Button myViewingsTabButton;
    @FXML
    private ListView<Viewing> viewingList;

    private final PropertyService propertyService =
        new PropertyService(new PropertyDao(), new PropertyPhotoDao(), new PropertyMessageDao(), new AuditService(new AuditDao()));
    private final ViewingService viewingService =
        new ViewingService(new ViewingDao(), propertyService, new AuditService(new AuditDao()));
    private final AuthService authService = new AuthService(new UserDao());

    private final Map<Integer, String> propertyTitles = new HashMap<>();
    private final Map<Integer, String> clientNames = new HashMap<>();

    private boolean showingRequests = true;

    @FXML
    private void initialize() {
        AppUser user = SessionManager.getCurrentUser();
        if (user == null || (user.getRole() != Role.AGENT && user.getRole() != Role.ADMIN)) {
            denyAccess();
            return;
        }
        viewingList.setCellFactory(list -> new ViewingCard());
        selectRequestsTab();
    }

    private void denyAccess() {
        contentBox.setVisible(false);
        contentBox.setManaged(false);
        accessDeniedLabel.setText("You do not have access to viewings.");
        accessDeniedLabel.setVisible(true);
        accessDeniedLabel.setManaged(true);
    }

    @FXML
    private void handleRequestsTab() {
        selectRequestsTab();
    }

    @FXML
    private void handleMyViewingsTab() {
        selectMyViewingsTab();
    }

    private void selectRequestsTab() {
        showingRequests = true;
        requestsTabButton.getStyleClass().setAll("button", "button-primary");
        myViewingsTabButton.getStyleClass().setAll("button", "button-secondary");
        loadViewings();
    }

    private void selectMyViewingsTab() {
        showingRequests = false;
        requestsTabButton.getStyleClass().setAll("button", "button-secondary");
        myViewingsTabButton.getStyleClass().setAll("button", "button-primary");
        loadViewings();
    }

    private void loadViewings() {
        Label empty = new Label(showingRequests
            ? "No viewing requests are waiting to be confirmed."
            : "You have no upcoming confirmed viewings.");
        empty.getStyleClass().add("empty-state");
        viewingList.setPlaceholder(empty);

        List<Viewing> results;
        try {
            results = showingRequests ? viewingService.findRequested() : myConfirmedViewings();
        } catch (SQLException e) {
            AlertUtil.showError("Could not load viewings. Check that SQL Server is running.");
            return;
        }
        viewingList.setItems(FXCollections.observableArrayList(results));
    }

    private List<Viewing> myConfirmedViewings() throws SQLException {
        int agentId = SessionManager.getCurrentUser().getId();
        return viewingService.findByAgent(agentId).stream()
            .filter(viewing -> viewing.getStatus() == ViewingStatus.CONFIRMED)
            .toList();
    }

    private void handleConfirm(Viewing viewing) {
        int agentId = SessionManager.getCurrentUser().getId();
        try {
            viewingService.confirm(viewing.getId(), agentId);
        } catch (ViewingService.SlotTakenException e) {
            AlertUtil.showError(e.getMessage());
            return;
        } catch (ViewingService.InvalidTransitionException e) {
            AlertUtil.showError("This request has already been dealt with.");
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("The viewing is confirmed.");
        loadViewings();
    }

    private void handleCancel(Viewing viewing) {
        if (!AlertUtil.confirm("Cancel this viewing?")) {
            return;
        }
        try {
            viewingService.cancel(viewing.getId());
        } catch (ViewingService.InvalidTransitionException e) {
            AlertUtil.showError("This viewing can no longer be cancelled.");
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("The viewing has been cancelled.");
        loadViewings();
    }

    private void handleOutcome(Viewing viewing, ViewingStatus outcome) {
        String prompt = outcome == ViewingStatus.NO_SHOW
            ? "The client did not show. Add a note:"
            : "The viewing is complete. Add a note:";
        TextInputDialog dialog = new TextInputDialog();
        dialog.setHeaderText(null);
        dialog.setTitle("Record outcome");
        dialog.setContentText(prompt);
        Optional<String> input = dialog.showAndWait();
        if (input.isEmpty()) {
            return;
        }
        String note = input.get().trim();
        if (note.isEmpty()) {
            AlertUtil.showError("A note is required to record an outcome.");
            return;
        }
        recordOutcome(viewing, outcome, note);
    }

    private void recordOutcome(Viewing viewing, ViewingStatus outcome, String note) {
        try {
            viewingService.recordOutcome(viewing.getId(), outcome, note);
        } catch (ViewingService.InvalidTransitionException e) {
            AlertUtil.showError("This viewing can no longer have an outcome recorded.");
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("The outcome has been recorded.");
        loadViewings();
    }

    private String propertyTitle(int propertyId) {
        return propertyTitles.computeIfAbsent(propertyId, id -> {
            try {
                Property property = propertyService.findById(id);
                return property == null ? "Unknown property" : property.getTitle();
            } catch (SQLException e) {
                return "Unknown property";
            }
        });
    }

    private String clientName(int clientId) {
        return clientNames.computeIfAbsent(clientId, id -> {
            try {
                AppUser client = authService.findById(id);
                return client == null ? "Unknown client" : client.getFullName();
            } catch (SQLException e) {
                return "Unknown client";
            }
        });
    }

    // Matches the status-to-pill table in docs/UI-STYLE.md exactly - nothing
    // on this screen is allowed to choose a colour on its own.
    private String pillClass(ViewingStatus status) {
        switch (status) {
            case CONFIRMED:
            case COMPLETED:
                return "pill-good";
            case REQUESTED:
                return "pill-warn";
            case NO_SHOW:
                return "pill-bad";
            default:
                return "pill-neutral";
        }
    }

    private final class ViewingCard extends ListCell<Viewing> {

        @Override
        protected void updateItem(Viewing viewing, boolean empty) {
            super.updateItem(viewing, empty);
            setText(null);
            setGraphic(empty || viewing == null ? null : buildCard(viewing));
        }

        private VBox buildCard(Viewing viewing) {
            Label title = new Label(propertyTitle(viewing.getPropertyId()));
            Label pill = new Label(Format.enumLabel(viewing.getStatus()));
            pill.getStyleClass().addAll("pill", pillClass(viewing.getStatus()));
            HBox header = new HBox(8, title, pill);

            Label meta = new Label("Client: " + clientName(viewing.getClientId()) + " • "
                + Format.dateTime(viewing.getScheduledAt()));
            meta.getStyleClass().add("label-soft");

            VBox card = new VBox(8, header, meta);
            card.getStyleClass().add("card");
            card.setPadding(new Insets(16));

            if (viewing.getOutcomeNote() != null) {
                Label note = new Label("Note: " + viewing.getOutcomeNote());
                note.setWrapText(true);
                note.getStyleClass().add("hint");
                card.getChildren().add(note);
            }

            HBox actions = buildActions(viewing);
            if (!actions.getChildren().isEmpty()) {
                card.getChildren().add(actions);
            }
            return card;
        }

        private HBox buildActions(Viewing viewing) {
            HBox actions = new HBox(8);
            if (viewing.getStatus() == ViewingStatus.REQUESTED) {
                addButton(actions, "Confirm", "button-primary", () -> handleConfirm(viewing));
                addButton(actions, "Decline", "button-danger", () -> handleCancel(viewing));
            }
            if (viewing.getStatus() == ViewingStatus.CONFIRMED) {
                addButton(actions, "Mark completed", "button-secondary",
                    () -> handleOutcome(viewing, ViewingStatus.COMPLETED));
                addButton(actions, "Mark no-show", "button-secondary",
                    () -> handleOutcome(viewing, ViewingStatus.NO_SHOW));
                addButton(actions, "Cancel", "button-danger", () -> handleCancel(viewing));
            }
            return actions;
        }

        private void addButton(HBox actions, String text, String styleClass, Runnable action) {
            Button button = new Button(text);
            button.getStyleClass().addAll("button", styleClass);
            button.setOnAction(event -> action.run());
            actions.getChildren().add(button);
        }
    }
}
