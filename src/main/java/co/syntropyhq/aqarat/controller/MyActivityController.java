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
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.SessionManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

// This panel is the client's own viewings and reservations (DESIGN.md
// section 9).
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
        // placeholder text and other setup continues below
        Label emptyReservations = new Label("You have no reservations yet.");
        emptyReservations.getStyleClass().add("empty-state");
        reservationList.setPlaceholder(emptyReservations);
        reservationList.setCellFactory(list -> new ReservationCard());
        loadReservations();

        Label emptyViewings = new Label("You have not requested any viewings yet.");
        emptyViewings.getStyleClass().add("empty-state");
        viewingList.setPlaceholder(emptyViewings);
        viewingList.setCellFactory(list -> new ViewingCard());
        loadViewings();
    }

    private void denyAccess() {
        Label denied = new Label("Only customers can see their activity here.");
        denied.getStyleClass().add("empty-state");
        reservationList.setPlaceholder(denied);
        viewingList.setPlaceholder(denied);
        reservationList.setItems(FXCollections.observableArrayList());
        viewingList.setItems(FXCollections.observableArrayList());
    }

    private void loadReservations() {
        try {
            int clientId = SessionManager.getCurrentUser().getId();
            List<Reservation> reservations = reservationService.findByClient(clientId);
            loadReservationProperties(reservations);
            reservationList.setItems(FXCollections.observableArrayList(reservations));
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
        } catch (PropertyService.InvalidTransitionException e) {
            AlertUtil.showError("A reservation could not be moved back to available. "
                + "Ask an agent to look into it.");
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
        if (!AlertUtil.confirm("Cancel this reservation? This cannot be undone.")) {
            return;
        }
        try {
            reservationService.cancel(reservation.getId());
        } catch (ReservationService.InvalidTransitionException e) {
            AlertUtil.showError("This reservation can no longer be cancelled.");
            return;
        } catch (PropertyService.InvalidTransitionException e) {
            AlertUtil.showError("The property could not be returned to available. "
                + "Ask an agent to look into it.");
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("The reservation has been cancelled.");
        loadReservations();
    }

    private void handleCancelViewing(Viewing viewing) {
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

    // Maps each status to the one pill style docs/UI-STYLE.md assigns it.
    // CONVERTED is now listed there alongside COMPLETED as a successful
    // outcome (docs/UI-STYLE.md, status colours table).
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

    // Same mapping, for a viewing's status.
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
            Label pill = new Label(Format.enumLabel(reservation.getStatus()));
            pill.getStyleClass().addAll("pill", pillClass(reservation.getStatus()));
            HBox header = new HBox(8, titleLabel, pill);

            Label meta = new Label("Deposit " + Format.paymentAmount(reservation.getDepositAmount())
                + " • Reserved " + Format.dateTime(reservation.getReservedAt())
                + " • Expires " + Format.dateTime(reservation.getExpiresAt()));
            meta.getStyleClass().add("label-soft");

            VBox card = new VBox(8, header, meta);
            card.getStyleClass().add("card");
            card.setPadding(new Insets(16));

            if (reservation.getStatus() == ReservationStatus.ACTIVE) {
                Button cancel = new Button("Cancel");
                cancel.getStyleClass().addAll("button", "button-danger");
                cancel.setOnAction(event -> handleCancel(reservation));
                card.getChildren().add(new HBox(8, cancel));
            }
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
            Label pill = new Label(Format.enumLabel(viewing.getStatus()));
            pill.getStyleClass().addAll("pill", pillClass(viewing.getStatus()));
            HBox header = new HBox(8, titleLabel, pill);

            Label meta = new Label("Scheduled " + Format.dateTime(viewing.getScheduledAt()));
            meta.getStyleClass().add("label-soft");

            VBox card = new VBox(8, header, meta);
            card.getStyleClass().add("card");
            card.setPadding(new Insets(16));

            boolean canCancel = viewing.getStatus() == ViewingStatus.REQUESTED
                || viewing.getStatus() == ViewingStatus.CONFIRMED;
            if (canCancel) {
                Button cancel = new Button("Cancel");
                cancel.getStyleClass().addAll("button", "button-danger");
                cancel.setOnAction(event -> handleCancelViewing(viewing));
                card.getChildren().add(new HBox(8, cancel));
            }
            return card;
        }
    }
}
