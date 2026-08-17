package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.dao.AuditDao;
import co.syntropyhq.aqarat.dao.ContractDao;
import co.syntropyhq.aqarat.dao.PaymentDao;
import co.syntropyhq.aqarat.dao.PaymentScheduleDao;
import co.syntropyhq.aqarat.dao.PropertyDao;
import co.syntropyhq.aqarat.dao.PropertyMessageDao;
import co.syntropyhq.aqarat.dao.PropertyPhotoDao;
import co.syntropyhq.aqarat.dao.ReservationDao;
import co.syntropyhq.aqarat.dao.SystemSettingDao;
import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.Contract;
import co.syntropyhq.aqarat.model.ContractStatus;
import co.syntropyhq.aqarat.model.ContractType;
import co.syntropyhq.aqarat.model.Payment;
import co.syntropyhq.aqarat.model.PaymentMethod;
import co.syntropyhq.aqarat.model.PaymentSchedule;
import co.syntropyhq.aqarat.model.PaymentStatus;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.Role;
import co.syntropyhq.aqarat.model.ScheduleStatus;
import co.syntropyhq.aqarat.service.AuditService;
import co.syntropyhq.aqarat.service.ContractService;
import co.syntropyhq.aqarat.service.PaymentService;
import co.syntropyhq.aqarat.service.PropertyService;
import co.syntropyhq.aqarat.service.ReservationService;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.AnimationUtil;
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.SessionManager;
import co.syntropyhq.aqarat.util.UIHelper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

public class MyContractsController {

    @FXML
    private VBox contentBox;
    @FXML
    private Label accessDeniedLabel;
    @FXML
    private ListView<Contract> contractList;

    private final AuditService auditService = new AuditService(new AuditDao());
    private final PropertyService propertyService =
        new PropertyService(new PropertyDao(), new PropertyPhotoDao(), new PropertyMessageDao(), auditService);
    private final PaymentService paymentService = new PaymentService(
        new PaymentScheduleDao(), new PaymentDao(), new ContractDao(), new ReservationDao(),
        new SystemSettingDao(), auditService);
    private final ReservationService reservationService = new ReservationService(
        new ReservationDao(), new SystemSettingDao(), propertyService, auditService);
    private final ContractService contractService = new ContractService(
        new ContractDao(), new ReservationDao(), propertyService, reservationService,
        new SystemSettingDao(), auditService, paymentService);

    private final Map<Integer, String> propertyTitles = new HashMap<>();

    @FXML
    private void initialize() {
        AppUser user = SessionManager.getCurrentUser();
        if (user == null || user.getRole() != Role.CUSTOMER) {
            denyAccess();
            return;
        }
        contractList.setPlaceholder(UIHelper.createEmptyState("No Active Contracts", "You have not entered into any lease or purchase contracts yet."));
        contractList.setCellFactory(list -> new ContractCard());
        loadContracts();
    }

    private void denyAccess() {
        contentBox.setVisible(false);
        contentBox.setManaged(false);
        accessDeniedLabel.setText("You do not have access to contracts.");
        accessDeniedLabel.setVisible(true);
        accessDeniedLabel.setManaged(true);
    }

    private void loadContracts() {
        int clientId = SessionManager.getCurrentUser().getId();
        try {
            contractList.setItems(FXCollections.observableArrayList(
                contractService.findByClient(clientId)));
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
        }
    }

    private List<PaymentSchedule> scheduleFor(Contract contract) {
        try {
            return paymentService.findScheduleByContract(contract.getId());
        } catch (SQLException e) {
            return List.of();
        }
    }

    private List<Payment> paymentsFor(Contract contract) {
        try {
            return paymentService.findPaymentsByContract(contract.getId());
        } catch (SQLException e) {
            return List.of();
        }
    }

    private void openDeclareDialog(Contract contract, PaymentSchedule schedule) {
        DeclareForm form = new DeclareForm(outstanding(schedule));
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Declare Payment");
        dialog.getDialogPane().setContent(form.layout());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            submitDeclaredPayment(contract, schedule, form);
        }
    }

    private void submitDeclaredPayment(Contract contract, PaymentSchedule schedule, DeclareForm form) {
        BigDecimal amount = form.readAmount();
        if (amount == null) {
            AlertUtil.showError("Enter an amount greater than zero.");
            return;
        }
        if (form.proofPath() == null) {
            AlertUtil.showError("Enter proof of payment - a reference or a file path.");
            return;
        }
        int clientId = SessionManager.getCurrentUser().getId();
        try {
            paymentService.declare(schedule.getId(), null, amount, form.method(), form.reference(),
                form.proofPath(), clientId);
        } catch (PaymentService.InvalidPaymentTargetException | PaymentService.InvalidPaymentAmountException e) {
            AlertUtil.showError(e.getMessage());
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("Payment declared successfully. An agent will confirm it shortly.");
        loadContracts();
    }

    private void showReceipt(Contract contract, Payment payment) {
        String receipt = "OFFICIAL PAYMENT RECEIPT\n\n"
            + "Property: " + propertyTitle(contract.getPropertyId()) + "\n"
            + "Contract Ref: #" + contract.getId() + "\n"
            + "Amount Paid: " + Format.paymentAmount(payment.getAmount()) + "\n"
            + "Payment Method: " + Format.enumLabel(payment.getMethod()) + "\n"
            + "Date Confirmed: " + Format.dateTime(payment.getPaidAt()) + "\n"
            + "Payment Status: " + Format.enumLabel(payment.getStatus());
        AlertUtil.showInfo(receipt);
    }

    private BigDecimal outstanding(PaymentSchedule schedule) {
        return schedule.getAmountDue().subtract(schedule.getAmountPaid());
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

    private String amountText(Contract contract) {
        return contract.getContractType() == ContractType.SALE
            ? Format.salePrice(contract.getTotalAmount())
            : Format.monthlyRent(contract.getMonthlyRent()) + " / month (" + contract.getTermMonths() + " mos)";
    }

    private String pillClass(ContractStatus status) {
        switch (status) {
            case DRAFT:
            case ACTIVE:
                return "pill-info";
            case COMPLETED:
                return "pill-good";
            case TERMINATED:
                return "pill-bad";
            default:
                return "pill-neutral";
        }
    }

    private String pillClass(ScheduleStatus status) {
        switch (status) {
            case PAID:
                return "pill-good";
            case PARTIALLY_PAID:
                return "pill-warn";
            case OVERDUE:
                return "pill-bad";
            default:
                return "pill-neutral";
        }
    }

    private String pillClass(PaymentStatus status) {
        switch (status) {
            case CONFIRMED:
                return "pill-good";
            case REJECTED:
                return "pill-bad";
            default:
                return "pill-warn";
        }
    }

    private <T> void setLabelConverter(ComboBox<T> combo, Function<T, String> labelFunction) {
        combo.setConverter(new StringConverter<T>() {
            @Override
            public String toString(T item) {
                return item == null ? "" : labelFunction.apply(item);
            }

            @Override
            public T fromString(String text) {
                return null;
            }
        });
    }

    private final class DeclareForm {
        private final TextField amountField = new TextField();
        private final ComboBox<PaymentMethod> methodCombo = new ComboBox<>();
        private final TextField referenceField = new TextField();
        private final TextField proofPathField = new TextField();

        private DeclareForm(BigDecimal outstanding) {
            amountField.setText(outstanding.toPlainString());
            methodCombo.setItems(FXCollections.observableArrayList(PaymentMethod.values()));
            setLabelConverter(methodCombo, Format::enumLabel);
            methodCombo.getSelectionModel().select(PaymentMethod.BANK_TRANSFER);
        }

        private GridPane layout() {
            GridPane grid = new GridPane();
            grid.setHgap(12);
            grid.setVgap(10);
            grid.setPadding(new Insets(16));
            grid.addRow(0, new Label("Payment Amount ($)"), amountField);
            grid.addRow(1, new Label("Payment Method"), methodCombo);
            grid.addRow(2, new Label("Transaction Reference"), referenceField);
            grid.addRow(3, new Label("Proof Ref / File"), proofPathField);
            return grid;
        }

        private BigDecimal readAmount() {
            try {
                BigDecimal value = new BigDecimal(amountField.getText().trim());
                return value.signum() > 0 ? value.setScale(2, RoundingMode.HALF_UP) : null;
            } catch (NumberFormatException | NullPointerException e) {
                return null;
            }
        }

        private PaymentMethod method() {
            return methodCombo.getValue();
        }

        private String reference() {
            String text = referenceField.getText();
            return text == null || text.isBlank() ? null : text.trim();
        }

        private String proofPath() {
            String text = proofPathField.getText();
            return text == null || text.isBlank() ? null : text.trim();
        }
    }

    private final class ContractCard extends ListCell<Contract> {

        @Override
        protected void updateItem(Contract contract, boolean empty) {
            super.updateItem(contract, empty);
            setText(null);
            setGraphic(empty || contract == null ? null : buildCard(contract));
        }

        private VBox buildCard(Contract contract) {
            VBox card = new VBox(14);
            card.getStyleClass().addAll("card", "card-hoverable");
            card.setPadding(new Insets(18));

            // Header
            HBox header = new HBox(12);
            header.setAlignment(Pos.CENTER_LEFT);

            Label title = new Label(propertyTitle(contract.getPropertyId()));
            title.getStyleClass().add("section-title");
            HBox.setHgrow(title, Priority.ALWAYS);

            Label pill = UIHelper.createPill(Format.enumLabel(contract.getStatus()), pillClass(contract.getStatus()));
            Label amountLabel = new Label(amountText(contract));
            amountLabel.getStyleClass().add("section-title");
            amountLabel.setStyle("-fx-text-fill: -c-primary; -fx-font-weight: 700;");

            header.getChildren().addAll(title, pill, amountLabel);

            Label meta = new Label(Format.enumLabel(contract.getContractType())
                + " • Start Date: " + Format.date(contract.getStartDate()));
            meta.getStyleClass().add("label-soft");

            card.getChildren().addAll(header, meta, scheduleSection(contract), paymentsSection(contract));
            AnimationUtil.addHoverLift(card);
            return card;
        }

        private VBox scheduleSection(Contract contract) {
            VBox section = new VBox(8);
            section.getStyleClass().add("card-subtle");

            Label heading = new Label("Installment Payment Schedule");
            heading.getStyleClass().add("section-title");
            heading.setStyle("-fx-font-size: 13px;");

            List<PaymentSchedule> schedule = scheduleFor(contract);
            if (schedule.isEmpty()) {
                Label empty = new Label("No payment schedule items.");
                empty.getStyleClass().add("hint");
                section.getChildren().addAll(heading, empty);
                return section;
            }

            VBox rows = new VBox(6);
            for (PaymentSchedule row : schedule) {
                rows.getChildren().add(scheduleRow(contract, row));
            }
            section.getChildren().addAll(heading, rows);
            return section;
        }

        private HBox scheduleRow(Contract contract, PaymentSchedule row) {
            HBox line = new HBox(10);
            line.setAlignment(Pos.CENTER_LEFT);
            line.setPadding(new Insets(4, 0, 4, 0));

            Label text = new Label("Installment #" + row.getInstallmentNo() + " • Due "
                + Format.date(row.getDueDate()) + " • " + Format.paymentAmount(row.getAmountDue())
                + " (Paid: " + Format.paymentAmount(row.getAmountPaid()) + ")");
            text.getStyleClass().add("body");
            HBox.setHgrow(text, Priority.ALWAYS);

            Label pill = UIHelper.createPill(Format.enumLabel(row.getStatus()), pillClass(row.getStatus()));
            line.getChildren().addAll(text, pill);

            if (row.getStatus() != ScheduleStatus.PAID) {
                Button declare = new Button("Declare payment");
                declare.getStyleClass().addAll("button", "button-primary");
                declare.setStyle("-fx-font-size: 11px; -fx-pref-height: 28px;");
                declare.setOnAction(event -> openDeclareDialog(contract, row));
                line.getChildren().add(declare);
            }
            return line;
        }

        private VBox paymentsSection(Contract contract) {
            VBox section = new VBox(8);
            section.getStyleClass().add("card-subtle");

            Label heading = new Label("Recorded Payments & Receipts");
            heading.getStyleClass().add("section-title");
            heading.setStyle("-fx-font-size: 13px;");

            List<Payment> payments = paymentsFor(contract);
            if (payments.isEmpty()) {
                Label empty = new Label("No recorded payments yet.");
                empty.getStyleClass().add("hint");
                section.getChildren().addAll(heading, empty);
                return section;
            }

            VBox rows = new VBox(6);
            for (Payment payment : payments) {
                rows.getChildren().add(paymentRow(contract, payment));
            }
            section.getChildren().addAll(heading, rows);
            return section;
        }

        private HBox paymentRow(Contract contract, Payment payment) {
            HBox line = new HBox(10);
            line.setAlignment(Pos.CENTER_LEFT);

            Label text = new Label(Format.paymentAmount(payment.getAmount()) + " via "
                + Format.enumLabel(payment.getMethod()) + " • " + Format.dateTime(payment.getPaidAt()));
            text.getStyleClass().add("body");
            HBox.setHgrow(text, Priority.ALWAYS);

            Label pill = UIHelper.createPill(Format.enumLabel(payment.getStatus()), pillClass(payment.getStatus()));
            line.getChildren().addAll(text, pill);

            if (payment.getStatus() == PaymentStatus.CONFIRMED) {
                Button receipt = new Button("Receipt");
                receipt.getStyleClass().addAll("button", "button-secondary");
                receipt.setStyle("-fx-font-size: 11px; -fx-pref-height: 28px;");
                receipt.setOnAction(event -> showReceipt(contract, payment));
                line.getChildren().add(receipt);
            }
            return line;
        }
    }
}
