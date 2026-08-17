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
import co.syntropyhq.aqarat.util.AnimationUtil;
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.SessionManager;
import co.syntropyhq.aqarat.util.UIHelper;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

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
        requestsTabButton.getStyleClass().setAll("tab-pill-button", "active");
        myViewingsTabButton.getStyleClass().setAll("tab-pill-button");
        loadViewings();
    }

    private void selectMyViewingsTab() {
        showingRequests = false;
        requestsTabButton.getStyleClass().setAll("tab-pill-button");
        myViewingsTabButton.getStyleClass().setAll("tab-pill-button", "active");
        loadViewings();
    }

    private void loadViewings() {
        viewingList.setPlaceholder(UIHelper.createEmptyState(
            showingRequests ? "No Incoming Requests" : "No Confirmed Appointments",
            showingRequests ? "No viewing appointments are currently awaiting agent confirmation." : "You have no upcoming confirmed appointments scheduled."
        ));

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
        AlertUtil.showInfo("Viewing appointment confirmed.");
        loadViewings();
    }

    private void handleCancel(Viewing viewing) {
        if (!AlertUtil.confirm("Cancel this viewing appointment?")) {
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
            ? "Client did not show. Add an outcome note:"
            : "Viewing completed. Add an outcome note:";
        TextInputDialog dialog = new TextInputDialog();
        dialog.setHeaderText(null);
        dialog.setTitle("Record Appointment Outcome");
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
        AlertUtil.showInfo("The outcome has been recorded successfully.");
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
            VBox card = new VBox(10);
            card.getStyleClass().addAll("card", "card-hoverable");
            card.setPadding(new Insets(16));

            HBox header = new HBox(12);
            header.setAlignment(Pos.CENTER_LEFT);

            Label title = new Label(propertyTitle(viewing.getPropertyId()));
            title.getStyleClass().add("section-title");
            HBox.setHgrow(title, Priority.ALWAYS);

            Label pill = UIHelper.createPill(Format.enumLabel(viewing.getStatus()), pillClass(viewing.getStatus()));
            header.getChildren().addAll(title, pill);

            Label meta = new Label("Client: " + clientName(viewing.getClientId()) + " • Slot: "
                + Format.dateTime(viewing.getScheduledAt()));
            meta.getStyleClass().add("label-soft");

            card.getChildren().addAll(header, meta);

            if (viewing.getOutcomeNote() != null) {
                Label note = new Label("Outcome note: " + viewing.getOutcomeNote());
                note.setWrapText(true);
                note.getStyleClass().add("hint");
                card.getChildren().add(note);
            }

            HBox actions = buildActions(viewing);
            if (!actions.getChildren().isEmpty()) {
                card.getChildren().add(actions);
            }
            AnimationUtil.addHoverLift(card);
            return card;
        }

        private HBox buildActions(Viewing viewing) {
            HBox actions = new HBox(8);
            actions.setAlignment(Pos.CENTER_LEFT);

            if (viewing.getStatus() == ViewingStatus.REQUESTED) {
                addButton(actions, "Confirm appointment", "button-primary", () -> handleConfirm(viewing));
                addButton(actions, "Decline", "button-danger", () -> handleCancel(viewing));
            }
            if (viewing.getStatus() == ViewingStatus.CONFIRMED) {
                addButton(actions, "Mark completed", "button-primary",
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
