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
import co.syntropyhq.aqarat.service.PaymentService;
import co.syntropyhq.aqarat.service.ContractService;
import co.syntropyhq.aqarat.service.PropertyService;
import co.syntropyhq.aqarat.service.ReservationService;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.SessionManager;
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
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

// The client's screen (docs/DESIGN.md section 9): own contracts, payment
// schedule, declare payment, receipts. Everything is scoped to the current
// user's id, never filtered after fetching (DESIGN.md section 8).
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
        Label empty = new Label("You have no contracts yet.");
        empty.getStyleClass().add("empty-state");
        contractList.setPlaceholder(empty);
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
        dialog.setTitle("Declare payment");
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
        } catch (PaymentService.InvalidPaymentTargetException e) {
            AlertUtil.showError(e.getMessage());
            return;
        } catch (PaymentService.InvalidPaymentAmountException e) {
            AlertUtil.showError(e.getMessage());
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("Payment declared. An agent will confirm it.");
        loadContracts();
    }

    private void showReceipt(Contract contract, Payment payment) {
        String receipt = "RECEIPT\n" + propertyTitle(contract.getPropertyId())
            + "\nContract #" + contract.getId()
            + "\nAmount: " + Format.paymentAmount(payment.getAmount())
            + "\nMethod: " + Format.enumLabel(payment.getMethod())
            + "\nPaid: " + Format.dateTime(payment.getPaidAt())
            + "\nStatus: " + Format.enumLabel(payment.getStatus());
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
            : Format.monthlyRent(contract.getMonthlyRent()) + " for " + contract.getTermMonths()
                + " months";
    }

    // Matches docs/UI-STYLE.md exactly, same mapping as ContractsController.
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

    // The declare-payment dialog's fields, built in Java - one small form,
    // not worth a second FXML file or a builder (CLAUDE.md).
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
            grid.setHgap(8);
            grid.setVgap(8);
            grid.setPadding(new Insets(16));
            grid.addRow(0, new Label("Amount"), amountField);
            grid.addRow(1, new Label("Method"), methodCombo);
            grid.addRow(2, new Label("Reference"), referenceField);
            grid.addRow(3, new Label("Proof (file path or reference)"), proofPathField);
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
            Label title = new Label(propertyTitle(contract.getPropertyId()));
            Label pill = new Label(Format.enumLabel(contract.getStatus()));
            pill.getStyleClass().addAll("pill", pillClass(contract.getStatus()));
            HBox header = new HBox(8, title, pill);

            Label meta = new Label(Format.enumLabel(contract.getContractType()) + " • "
                + amountText(contract) + " • from " + Format.date(contract.getStartDate()));
            meta.getStyleClass().add("label-soft");

            VBox card = new VBox(12, header, meta, scheduleSection(contract), paymentsSection(contract));
            card.getStyleClass().add("card");
            card.setPadding(new Insets(16));
            return card;
        }

        private VBox scheduleSection(Contract contract) {
            Label heading = new Label("Payment schedule");
            heading.getStyleClass().add("section-title");
            List<PaymentSchedule> schedule = scheduleFor(contract);
            if (schedule.isEmpty()) {
                Label empty = new Label("No payment schedule yet.");
                empty.getStyleClass().add("hint");
                return new VBox(4, heading, empty);
            }
            VBox rows = new VBox(8);
            for (PaymentSchedule row : schedule) {
                rows.getChildren().add(scheduleRow(contract, row));
            }
            return new VBox(4, heading, rows);
        }

        private HBox scheduleRow(Contract contract, PaymentSchedule row) {
            Label text = new Label("Installment " + row.getInstallmentNo() + " • due "
                + Format.date(row.getDueDate()) + " • " + Format.paymentAmount(row.getAmountDue())
                + " (paid " + Format.paymentAmount(row.getAmountPaid()) + ")");
            text.getStyleClass().add("label-soft");
            Label pill = new Label(Format.enumLabel(row.getStatus()));
            pill.getStyleClass().addAll("pill", pillClass(row.getStatus()));
            HBox line = new HBox(8, text, pill);
            if (row.getStatus() != ScheduleStatus.PAID) {
                Button declare = new Button("Declare payment");
                declare.getStyleClass().addAll("button", "button-secondary");
                declare.setOnAction(event -> openDeclareDialog(contract, row));
                line.getChildren().add(declare);
            }
            return line;
        }

        private VBox paymentsSection(Contract contract) {
            Label heading = new Label("Payments");
            heading.getStyleClass().add("section-title");
            List<Payment> payments = paymentsFor(contract);
            if (payments.isEmpty()) {
                Label empty = new Label("No payments recorded yet.");
                empty.getStyleClass().add("hint");
                return new VBox(4, heading, empty);
            }
            VBox rows = new VBox(8);
            for (Payment payment : payments) {
                rows.getChildren().add(paymentRow(contract, payment));
            }
            return new VBox(4, heading, rows);
        }

        private HBox paymentRow(Contract contract, Payment payment) {
            Label text = new Label(Format.paymentAmount(payment.getAmount()) + " • "
                + Format.enumLabel(payment.getMethod()) + " • " + Format.dateTime(payment.getPaidAt()));
            text.getStyleClass().add("label-soft");
            Label pill = new Label(Format.enumLabel(payment.getStatus()));
            pill.getStyleClass().addAll("pill", pillClass(payment.getStatus()));
            HBox line = new HBox(8, text, pill);
            if (payment.getStatus() == PaymentStatus.CONFIRMED) {
                Button receipt = new Button("Receipt");
                receipt.getStyleClass().addAll("button", "button-secondary");
                receipt.setOnAction(event -> showReceipt(contract, payment));
                line.getChildren().add(receipt);
            }
            return line;
        }
    }
}
