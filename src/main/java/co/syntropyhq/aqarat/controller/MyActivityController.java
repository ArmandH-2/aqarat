package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.dao.AuditDao;
import co.syntropyhq.aqarat.dao.PropertyDao;
import co.syntropyhq.aqarat.dao.PropertyMessageDao;
import co.syntropyhq.aqarat.dao.PropertyPhotoDao;
import co.syntropyhq.aqarat.dao.ReservationDao;
import co.syntropyhq.aqarat.dao.SystemSettingDao;
import co.syntropyhq.aqarat.dao.ViewingDao;
import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.Reservation;
import co.syntropyhq.aqarat.model.ReservationStatus;
import co.syntropyhq.aqarat.model.Role;
import co.syntropyhq.aqarat.model.Viewing;
import co.syntropyhq.aqarat.model.ViewingStatus;
import co.syntropyhq.aqarat.service.AuditService;
import co.syntropyhq.aqarat.service.PropertyService;
import co.syntropyhq.aqarat.service.ReservationService;
import co.syntropyhq.aqarat.service.ViewingService;
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

public class MyActivityController {

    @FXML
    private ListView<Reservation> reservationList;
    @FXML
    private ListView<Viewing> viewingList;

    private final PropertyService propertyService =
        new PropertyService(new PropertyDao(), new PropertyPhotoDao(), new PropertyMessageDao(), new AuditService(new AuditDao()));
    private final ReservationService reservationService = new ReservationService(
        new ReservationDao(), new SystemSettingDao(), propertyService, new AuditService(new AuditDao()));
    private final ViewingService viewingService =
        new ViewingService(new ViewingDao(), propertyService, new AuditService(new AuditDao()));

    private final Map<Integer, Property> propertiesById = new HashMap<>();

    @FXML
    private void initialize() {
        AppUser user = SessionManager.getCurrentUser();
        if (user == null || user.getRole() != Role.CUSTOMER) {
            denyAccess();
            return;
        }

        reservationList.setPlaceholder(UIHelper.createEmptyState("No Active Reservations", "You haven't reserved any properties yet."));
        reservationList.setCellFactory(list -> new ReservationCard());
        loadReservations();

        viewingList.setPlaceholder(UIHelper.createEmptyState("No Viewing Appointments", "You have not requested any viewings yet."));
        viewingList.setCellFactory(list -> new ViewingCard());
        loadViewings();
    }

    private void denyAccess() {
        reservationList.setPlaceholder(UIHelper.createEmptyState("Access Restricted", "Only customers can view activity."));
        viewingList.setPlaceholder(UIHelper.createEmptyState("Access Restricted", "Only customers can view activity."));
        reservationList.setItems(FXCollections.observableArrayList());
        viewingList.setItems(FXCollections.observableArrayList());
    }

    /**
     * Sizes a list to what is in it.
     *
     * <p>These lists are short — a person has a handful of reservations, not a
     * page of them — and a fixed height left a card with one row in it two
     * thirds empty, which reads as something that failed to load rather than as
     * a short list. An empty list keeps enough height for its placeholder.
     */
    private void sizeToContents(ListView<?> list, int rows, double rowHeight) {
        double height = rows == 0 ? 96 : rows * rowHeight + 8;
        list.setPrefHeight(height);
        list.setMinHeight(height);
        list.setMaxHeight(height);
    }

    private void loadReservations() {
        try {
            int clientId = SessionManager.getCurrentUser().getId();
            List<Reservation> reservations = reservationService.findByClient(clientId);
            loadReservationProperties(reservations);
            reservationList.setItems(FXCollections.observableArrayList(reservations));
            sizeToContents(reservationList, reservations.size(), 132);
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
        } catch (PropertyService.InvalidTransitionException e) {
            AlertUtil.showError("A reservation could not be moved back to available.");
        }
    }

    private void loadReservationProperties(List<Reservation> reservations) throws SQLException {
        for (Reservation reservation : reservations) {
            ensureProperty(reservation.getPropertyId());
        }
    }

    private void loadViewings() {
        try {
            int clientId = SessionManager.getCurrentUser().getId();
            List<Viewing> viewings = viewingService.findByClient(clientId);
            loadViewingProperties(viewings);
            viewingList.setItems(FXCollections.observableArrayList(viewings));
            sizeToContents(viewingList, viewings.size(), 118);
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
        }
    }

    private void loadViewingProperties(List<Viewing> viewings) throws SQLException {
        for (Viewing viewing : viewings) {
            ensureProperty(viewing.getPropertyId());
        }
    }

    private void ensureProperty(int propertyId) throws SQLException {
        if (!propertiesById.containsKey(propertyId)) {
            propertiesById.put(propertyId, propertyService.findById(propertyId));
        }
    }

    private void handleCancel(Reservation reservation) {
        boolean go = Dialogs.ask("Cancel this reservation?")
            .because("The property goes back on the market immediately and anyone else can "
                + "reserve it. Your deposit is handled by the agency, not by this step.")
            .confirm("Cancel the reservation")
            .cancel("Keep it")
            .destructive()
            .show();
        if (!go) {
            return;
        }
        try {
            reservationService.cancel(reservation.getId());
        } catch (ReservationService.InvalidTransitionException e) {
            AlertUtil.showError("This reservation can no longer be cancelled.");
            return;
        } catch (PropertyService.InvalidTransitionException e) {
            AlertUtil.showError("The property could not be returned to available.");
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showUndone("Reservation cancelled",
            "The property is back on the market. Your agent handles the deposit separately.");
        loadReservations();
    }

    private void handleCancelViewing(Viewing viewing) {
        boolean go = Dialogs.ask("Cancel this viewing?")
            .because("The slot is released and the agent is told. You can request another "
                + "viewing of the same property afterwards.")
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
            "The slot is released and the agent has been told. You can request another viewing of the same property.");
        loadViewings();
    }

    private String pillClass(ReservationStatus status) {
        switch (status) {
            case ACTIVE:
                return "pill-info";
            case CONVERTED:
                return "pill-good";
            default:
                return "pill-neutral";
        }
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

    private final class ReservationCard extends ListCell<Reservation> {

        @Override
        protected void updateItem(Reservation reservation, boolean empty) {
            super.updateItem(reservation, empty);
            setText(null);
            setGraphic(empty || reservation == null ? null : buildCard(reservation));
        }

        private VBox buildCard(Reservation reservation) {
            Property property = propertiesById.get(reservation.getPropertyId());
            String title = property == null ? "Property #" + reservation.getPropertyId()
                : property.getTitle();

            Label titleLabel = new Label(title);
            titleLabel.getStyleClass().add("section-title");
            HBox.setHgrow(titleLabel, Priority.ALWAYS);

            Label pill = UIHelper.createPill(Format.enumLabel(reservation.getStatus()), pillClass(reservation.getStatus()));
            Label depositLabel = new Label("Deposit: " + Format.paymentAmount(reservation.getDepositAmount()));
            depositLabel.getStyleClass().add("section-title");
            depositLabel.setStyle("-fx-text-fill: -c-primary; -fx-font-size: 14px;");

            HBox header = new HBox(12, titleLabel, pill, depositLabel);
            header.setAlignment(Pos.CENTER_LEFT);

            Label meta = new Label("Reserved on " + Format.dateTime(reservation.getReservedAt())
                + " • Lock Expires " + Format.dateTime(reservation.getExpiresAt()));
            meta.getStyleClass().add("label-soft");

            VBox card = new VBox(8, header, meta);
            card.getStyleClass().addAll("card-subtle", "card-hoverable");
            card.setPadding(new Insets(12));

            HBox actions = new HBox(8);
            actions.setAlignment(Pos.CENTER_LEFT);

            Button viewBtn = new Button("View property");
            viewBtn.getStyleClass().addAll("button", "button-secondary");
            viewBtn.setOnAction(e -> Router.show(Panel.PROPERTY_DETAILS, reservation.getPropertyId()));
            actions.getChildren().add(viewBtn);

            if (reservation.getStatus() == ReservationStatus.ACTIVE) {
                Button cancel = new Button("Cancel reservation");
                cancel.getStyleClass().addAll("button", "button-danger");
                cancel.setOnAction(event -> handleCancel(reservation));
                actions.getChildren().add(cancel);
            }

            card.getChildren().add(actions);
            AnimationUtil.addHoverLift(card);
            return card;
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
            Property property = propertiesById.get(viewing.getPropertyId());
            String title = property == null ? "Property #" + viewing.getPropertyId()
                : property.getTitle();

            Label titleLabel = new Label(title);
            titleLabel.getStyleClass().add("section-title");
            HBox.setHgrow(titleLabel, Priority.ALWAYS);

            Label pill = UIHelper.createPill(Format.enumLabel(viewing.getStatus()), pillClass(viewing.getStatus()));
            HBox header = new HBox(12, titleLabel, pill);
            header.setAlignment(Pos.CENTER_LEFT);

            Label meta = new Label(Format.dateTime(viewing.getScheduledAt()));
            meta.getStyleClass().add("label-soft");

            VBox card = new VBox(8, header, meta);
            card.getStyleClass().addAll("card-subtle", "card-hoverable");
            card.setPadding(new Insets(12));

            HBox actions = new HBox(8);
            actions.setAlignment(Pos.CENTER_LEFT);

            Button viewBtn = new Button("View property");
            viewBtn.getStyleClass().addAll("button", "button-secondary");
            viewBtn.setOnAction(e -> Router.show(Panel.PROPERTY_DETAILS, viewing.getPropertyId()));
            actions.getChildren().add(viewBtn);

            boolean canCancel = viewing.getStatus() == ViewingStatus.REQUESTED
                || viewing.getStatus() == ViewingStatus.CONFIRMED;
            if (canCancel) {
                Button cancel = new Button("Cancel appointment");
                cancel.getStyleClass().addAll("button", "button-danger");
                cancel.setOnAction(event -> handleCancelViewing(viewing));
                actions.getChildren().add(cancel);
            }

            card.getChildren().add(actions);
            AnimationUtil.addHoverLift(card);
            return card;
        }
    }
}
