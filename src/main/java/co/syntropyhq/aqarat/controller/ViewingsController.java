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
import co.syntropyhq.aqarat.util.Banner;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.AnimationUtil;
import co.syntropyhq.aqarat.util.Dialogs;
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.Panel;
import co.syntropyhq.aqarat.util.Router;
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
            viewingList.getItems().clear();
            viewingList.setPlaceholder(Banner.failure("Viewings could not be loaded",
                "The database did not answer. Every appointment already booked is unaffected.",
                () -> loadViewings()));
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
        AlertUtil.showInfo("Viewing confirmed",
            "The slot is held against your name. Nobody else can be booked into it.");
        loadViewings();
    }

    private void handleCancel(Viewing viewing) {
        boolean go = Dialogs.ask("Cancel this viewing?")
            .because("The slot is released and the client is told. Cancel rather than mark an "
                + "outcome when the appointment did not take place at all.")
            .confirm("Cancel the viewing")
            .cancel("Keep it")
            .show();
        if (!go) {
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
        AlertUtil.showUndone("Viewing cancelled",
            "The slot is released and the client has been told.");
        loadViewings();
    }

    private void handleOutcome(Viewing viewing, ViewingStatus outcome) {
        String prompt = outcome == ViewingStatus.NO_SHOW
            ? "Client did not show. Add an outcome note:"
            : "Viewing completed. Add an outcome note:";
        Optional<String> input = Dialogs.note(outcome == ViewingStatus.NO_SHOW
                ? "The client did not show"
                : "The viewing went ahead")
            .about(clientName(viewing.getClientId()) + " - " + propertyTitle(viewing.getPropertyId()))
            .explaining(prompt + " It stays on the property record, so whoever handles this "
                + "client next can read what happened.")
            .field("What happened")
            .placeholder(outcome == ViewingStatus.NO_SHOW
                ? "Whether they made contact, and whether to offer another slot"
                : "Their reaction, questions raised, and what happens next")
            .confirm("Record the outcome")
            .cancel("Not yet")
            .show();
        if (input.isEmpty()) {
            return;
        }
        recordOutcome(viewing, outcome, input.get());
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
        AlertUtil.showInfo("Outcome recorded",
            "Your note stays on the property record for whoever handles this client next.");
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

            Label meta = new Label(clientName(viewing.getClientId()) + " · "
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

            // An agent deciding whether to give up an afternoon for this
            // appointment is deciding about a property, and the card carries
            // only its title. Offered on every status: the question "which one
            // was that?" outlives the appointment.
            addButton(actions, "View property", "button-secondary",
                () -> Router.show(Panel.PROPERTY_DETAILS, viewing.getPropertyId()));

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
