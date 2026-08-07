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
import co.syntropyhq.aqarat.dao.UserDao;
import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.Contract;
import co.syntropyhq.aqarat.model.Payment;
import co.syntropyhq.aqarat.model.PaymentMethod;
import co.syntropyhq.aqarat.model.PaymentSchedule;
import co.syntropyhq.aqarat.model.PaymentStatus;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.Role;
import co.syntropyhq.aqarat.model.ScheduleStatus;
import co.syntropyhq.aqarat.service.AuditService;
import co.syntropyhq.aqarat.service.AuthService;
import co.syntropyhq.aqarat.service.ContractService;
import co.syntropyhq.aqarat.service.PaymentService;
import co.syntropyhq.aqarat.service.PropertyService;
import co.syntropyhq.aqarat.service.ReservationService;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.FieldError;
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

// The agent's screen (docs/DESIGN.md section 9): schedules, declared
// payments awaiting confirmation, record a payment, issue a receipt. Same
// two-tab shape as ContractsController.
public class PaymentsController {

    @FXML
    private VBox contentBox;
    @FXML
    private Label accessDeniedLabel;
    @FXML
    private Button awaitingTabButton;
    @FXML
    private Button recordTabButton;
    @FXML
    private VBox awaitingBox;
    @FXML
    private VBox recordBox;
    @FXML
    private ListView<Payment> declaredList;
    @FXML
    private TextField contractIdField;
    @FXML
    private Label contractError;
    @FXML
    private Label contractSummaryLabel;
    @FXML
    private ListView<PaymentSchedule> scheduleList;

    private final AuditService auditService = new AuditService(new AuditDao());
    private final PropertyService propertyService =
        new PropertyService(new PropertyDao(), new PropertyPhotoDao(), new PropertyMessageDao(), auditService);
    private final ReservationService reservationService = new ReservationService(
        new ReservationDao(), new SystemSettingDao(), propertyService, auditService);
    // No schedule generator: this panel never activates a contract, so
    // ContractService's seam is never called from here.
    private final ContractService contractService = new ContractService(
        new ContractDao(), new ReservationDao(), propertyService, reservationService,
        new SystemSettingDao(), auditService, null);
    private final PaymentService paymentService = new PaymentService(
        new PaymentScheduleDao(), new PaymentDao(), new ContractDao(), new ReservationDao(),
        new SystemSettingDao(), auditService);
    private final AuthService authService = new AuthService(new UserDao());

    private final Map<Integer, String> userNames = new HashMap<>();
    private final Map<Integer, String> propertyTitles = new HashMap<>();

    private Contract loadedContract;

    @FXML
    private void initialize() {
        AppUser user = SessionManager.getCurrentUser();
        if (user == null || (user.getRole() != Role.AGENT && user.getRole() != Role.ADMIN)) {
            denyAccess();
            return;
        }
        declaredList.setCellFactory(list -> new DeclaredPaymentCard());
        scheduleList.setCellFactory(list -> new ScheduleCard());
        selectAwaitingTab();
    }

    private void denyAccess() {
        contentBox.setVisible(false);
        contentBox.setManaged(false);
        accessDeniedLabel.setText("You do not have access to payments.");
        accessDeniedLabel.setVisible(true);
        accessDeniedLabel.setManaged(true);
    }

    @FXML
    private void handleAwaitingTab() {
        selectAwaitingTab();
    }

    @FXML
    private void handleRecordTab() {
        selectRecordTab();
    }

    private void selectAwaitingTab() {
        awaitingTabButton.getStyleClass().setAll("button", "button-primary");
        recordTabButton.getStyleClass().setAll("button", "button-secondary");
        awaitingBox.setVisible(true);
        awaitingBox.setManaged(true);
        recordBox.setVisible(false);
        recordBox.setManaged(false);
        loadDeclaredPayments();
    }

    private void selectRecordTab() {
        awaitingTabButton.getStyleClass().setAll("button", "button-secondary");
        recordTabButton.getStyleClass().setAll("button", "button-primary");
        awaitingBox.setVisible(false);
        awaitingBox.setManaged(false);
        recordBox.setVisible(true);
        recordBox.setManaged(true);
    }

    private void loadDeclaredPayments() {
        Label empty = new Label("No payments are waiting for confirmation.");
        empty.getStyleClass().add("empty-state");
        declaredList.setPlaceholder(empty);
        List<Payment> results;
        try {
            // An agent confirms payments declared against their own contracts
            // and reservations; only an admin sees the whole agency's queue.
            AppUser user = SessionManager.getCurrentUser();
            Integer agentId = user.getRole() == Role.ADMIN ? null : user.getId();
            results = paymentService.findDeclaredAwaitingConfirmation(agentId);
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        declaredList.setItems(FXCollections.observableArrayList(results));
    }

    @FXML
    private void handleLoadContract() {
        FieldError.clear(contractIdField, contractError);
        contractSummaryLabel.setText("");
        loadedContract = null;
        Integer id = parsePositiveInt(contractIdField.getText());
        if (id == null) {
            FieldError.show(contractIdField, contractError, "Enter a contract id.");
            return;
        }
        loadContractAndSchedule(id);
    }

    private void loadContractAndSchedule(int id) {
        Contract contract = fetchContract(id);
        if (contract == null) {
            FieldError.show(contractIdField, contractError, "No contract with that id.");
            return;
        }
        loadedContract = contract;
        contractSummaryLabel.setText(contractSummary(contract));
        loadSchedule(contract.getId());
    }

    private Contract fetchContract(int id) {
        try {
            Contract contract = contractService.findById(id);
            if (contract == null) {
                return null;
            }
            // A contract is this agent's business only when it carries their
            // id. Someone else's contract is answered with the same message
            // as a missing id, so its existence is not even revealed.
            AppUser user = SessionManager.getCurrentUser();
            if (user.getRole() != Role.ADMIN && contract.getAgentId() != user.getId()) {
                return null;
            }
            return contract;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return null;
        }
    }

    private void loadSchedule(int contractId) {
        Label empty = new Label("This contract has no payment schedule yet.");
        empty.getStyleClass().add("empty-state");
        scheduleList.setPlaceholder(empty);
        try {
            scheduleList.setItems(FXCollections.observableArrayList(
                paymentService.findScheduleByContract(contractId)));
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
        }
    }

    private String contractSummary(Contract contract) {
        return propertyTitle(contract.getPropertyId()) + " • " + userName(contract.getClientId())
            + " • " + Format.enumLabel(contract.getStatus());
    }

    private void handleConfirm(Payment payment) {
        int agentId = SessionManager.getCurrentUser().getId();
        try {
            paymentService.confirm(payment.getId(), agentId);
        } catch (PaymentService.InvalidPaymentStateException e) {
            AlertUtil.showError(e.getMessage());
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo(receiptText(payment, "Confirmed"));
        loadDeclaredPayments();
    }

    private void handleReject(Payment payment) {
        if (!AlertUtil.confirm("Reject this payment?")) {
            return;
        }
        int agentId = SessionManager.getCurrentUser().getId();
        try {
            paymentService.reject(payment.getId(), agentId);
        } catch (PaymentService.InvalidPaymentStateException e) {
            AlertUtil.showError(e.getMessage());
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("The payment has been rejected.");
        loadDeclaredPayments();
    }

    private void openRecordDialog(PaymentSchedule schedule) {
        RecordForm form = new RecordForm(outstanding(schedule));
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Record payment");
        dialog.getDialogPane().setContent(form.layout());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            submitRecordedPayment(schedule, form);
        }
    }

    private void submitRecordedPayment(PaymentSchedule schedule, RecordForm form) {
        BigDecimal amount = form.readAmount();
        if (amount == null) {
            AlertUtil.showError("Enter an amount greater than zero.");
            return;
        }
        int agentId = SessionManager.getCurrentUser().getId();
        try {
            paymentService.recordConfirmedPayment(schedule.getId(), null, amount,
                form.method(), form.reference(), form.proofPath(), agentId);
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
        AlertUtil.showInfo(receiptText(schedule, amount, form.method(), "Confirmed"));
        loadSchedule(loadedContract.getId());
    }

    private BigDecimal outstanding(PaymentSchedule schedule) {
        return schedule.getAmountDue().subtract(schedule.getAmountPaid());
    }

    private String receiptText(PaymentSchedule schedule, BigDecimal amount, PaymentMethod method,
            String status) {
        return "RECEIPT\n"
            + propertyTitle(loadedContract.getPropertyId()) + "\n"
            + "Contract #" + loadedContract.getId() + " - Installment " + schedule.getInstallmentNo()
            + "\nAmount: " + Format.paymentAmount(amount)
            + "\nMethod: " + Format.enumLabel(method)
            + "\nStatus: " + status;
    }

    private String receiptText(Payment payment, String status) {
        String target = payment.getScheduleId() != null
            ? "Installment payment" : "Reservation deposit";
        return "RECEIPT\n" + target
            + "\nAmount: " + Format.paymentAmount(payment.getAmount())
            + "\nMethod: " + Format.enumLabel(payment.getMethod())
            + "\nDeclared by: " + userName(payment.getDeclaredBy())
            + "\nStatus: " + status;
    }

    private Integer parsePositiveInt(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            int value = Integer.parseInt(trimmed);
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
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

    private String userName(int userId) {
        return userNames.computeIfAbsent(userId, id -> {
            try {
                AppUser found = authService.findById(id);
                return found == null ? "Unknown user" : found.getFullName();
            } catch (SQLException e) {
                return "Unknown user";
            }
        });
    }

    // Matches docs/UI-STYLE.md exactly. PENDING is not listed there, so it
    // falls to the same neutral default DRAFT would get if it were missing.
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

    // The record-payment dialog's fields, built in Java rather than a second
    // FXML file for one small form (CLAUDE.md: no builders or extra files
    // for something this small).
    private final class RecordForm {
        private final TextField amountField = new TextField();
        private final ComboBox<PaymentMethod> methodCombo = new ComboBox<>();
        private final TextField referenceField = new TextField();
        private final TextField proofPathField = new TextField();

        private RecordForm(BigDecimal outstanding) {
            amountField.setText(outstanding.toPlainString());
            methodCombo.setItems(FXCollections.observableArrayList(PaymentMethod.values()));
            setLabelConverter(methodCombo, Format::enumLabel);
            methodCombo.getSelectionModel().select(PaymentMethod.CASH);
        }

        private GridPane layout() {
            GridPane grid = new GridPane();
            grid.setHgap(8);
            grid.setVgap(8);
            grid.setPadding(new Insets(16));
            grid.addRow(0, new Label("Amount"), amountField);
            grid.addRow(1, new Label("Method"), methodCombo);
            grid.addRow(2, new Label("Reference"), referenceField);
            grid.addRow(3, new Label("Proof path"), proofPathField);
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

    private final class DeclaredPaymentCard extends ListCell<Payment> {

        @Override
        protected void updateItem(Payment payment, boolean empty) {
            super.updateItem(payment, empty);
            setText(null);
            setGraphic(empty || payment == null ? null : buildCard(payment));
        }

        private VBox buildCard(Payment payment) {
            String target = payment.getScheduleId() != null
                ? "Installment payment" : "Reservation deposit";
            Label title = new Label(target);
            Label pill = new Label(Format.enumLabel(payment.getStatus()));
            pill.getStyleClass().addAll("pill", pillClass(payment.getStatus()));
            HBox header = new HBox(8, title, pill);

            Label meta = new Label(Format.paymentAmount(payment.getAmount()) + " • "
                + Format.enumLabel(payment.getMethod()) + " • declared by "
                + userName(payment.getDeclaredBy()) + " • " + Format.dateTime(payment.getCreatedAt()));
            meta.getStyleClass().add("label-soft");

            Label proof = new Label("Proof: "
                + (payment.getProofPath() == null ? "none provided" : payment.getProofPath()));
            proof.getStyleClass().add("hint");

            VBox card = new VBox(8, header, meta, proof, actions(payment));
            card.getStyleClass().add("card");
            card.setPadding(new Insets(16));
            return card;
        }

        private HBox actions(Payment payment) {
            Button confirm = new Button("Confirm");
            confirm.getStyleClass().addAll("button", "button-primary");
            confirm.setOnAction(event -> handleConfirm(payment));
            Button reject = new Button("Reject");
            reject.getStyleClass().addAll("button", "button-danger");
            reject.setOnAction(event -> handleReject(payment));
            return new HBox(8, confirm, reject);
        }
    }

    private final class ScheduleCard extends ListCell<PaymentSchedule> {

        @Override
        protected void updateItem(PaymentSchedule schedule, boolean empty) {
            super.updateItem(schedule, empty);
            setText(null);
            setGraphic(empty || schedule == null ? null : buildCard(schedule));
        }

        private VBox buildCard(PaymentSchedule schedule) {
            Label title = new Label("Installment " + schedule.getInstallmentNo()
                + " • due " + Format.date(schedule.getDueDate()));
            Label pill = new Label(Format.enumLabel(schedule.getStatus()));
            pill.getStyleClass().addAll("pill", pillClass(schedule.getStatus()));
            HBox header = new HBox(8, title, pill);

            Label meta = new Label("Due " + Format.paymentAmount(schedule.getAmountDue())
                + " • paid " + Format.paymentAmount(schedule.getAmountPaid())
                + " • outstanding " + Format.paymentAmount(outstanding(schedule)));
            meta.getStyleClass().add("label-soft");

            VBox card = new VBox(8, header, meta);
            card.getStyleClass().add("card");
            card.setPadding(new Insets(16));
            if (schedule.getStatus() != ScheduleStatus.PAID) {
                Button record = new Button("Record payment");
                record.getStyleClass().addAll("button", "button-primary");
                record.setOnAction(event -> openRecordDialog(schedule));
                card.getChildren().add(new HBox(8, record));
            }
            return card;
        }
    }
}
