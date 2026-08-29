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
import co.syntropyhq.aqarat.util.AnimationUtil;
import co.syntropyhq.aqarat.util.FieldError;
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
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

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
        awaitingTabButton.getStyleClass().setAll("tab-pill-button", "active");
        recordTabButton.getStyleClass().setAll("tab-pill-button");
        awaitingBox.setVisible(true);
        awaitingBox.setManaged(true);
        recordBox.setVisible(false);
        recordBox.setManaged(false);
        loadDeclaredPayments();
    }

    private void selectRecordTab() {
        awaitingTabButton.getStyleClass().setAll("tab-pill-button");
        recordTabButton.getStyleClass().setAll("tab-pill-button", "active");
        awaitingBox.setVisible(false);
        awaitingBox.setManaged(false);
        recordBox.setVisible(true);
        recordBox.setManaged(true);
    }

    private void loadDeclaredPayments() {
        declaredList.setPlaceholder(UIHelper.createEmptyState("No Declared Payments", "No customer-declared payments are waiting for confirmation."));
        List<Payment> results;
        try {
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
            FieldError.show(contractIdField, contractError, "No contract found with that ID.");
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
        scheduleList.setPlaceholder(UIHelper.createEmptyState("No Payment Schedule", "This contract has no payment installments scheduled yet."));
        try {
            scheduleList.setItems(FXCollections.observableArrayList(
                paymentService.findScheduleByContract(contractId)));
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
        }
    }

    private String contractSummary(Contract contract) {
        return propertyTitle(contract.getPropertyId()) + " • " + userName(contract.getClientId())
            + " · " + Format.enumLabel(contract.getStatus());
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
        if (!AlertUtil.confirm("Reject this declared payment?")) {
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
        dialog.setTitle("Record Direct Payment");
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
        } catch (PaymentService.InvalidPaymentTargetException | PaymentService.InvalidPaymentAmountException e) {
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
        return "OFFICIAL RECEIPT\n\n"
            + "Property: " + propertyTitle(loadedContract.getPropertyId()) + "\n"
            + "Contract Ref: #" + loadedContract.getId() + " - Installment #" + schedule.getInstallmentNo() + "\n"
            + "Amount Paid: " + Format.paymentAmount(amount) + "\n"
            + "Payment Method: " + Format.enumLabel(method) + "\n"
            + "Status: " + status;
    }

    private String receiptText(Payment payment, String status) {
        String target = payment.getScheduleId() != null
            ? "Installment Payment" : "Reservation Deposit";
        return "OFFICIAL RECEIPT\n\n"
            + "Payment Type: " + target + "\n"
            + "Amount Paid: " + Format.paymentAmount(payment.getAmount()) + "\n"
            + "Payment Method: " + Format.enumLabel(payment.getMethod()) + "\n"
            + "Declared By: " + userName(payment.getDeclaredBy()) + "\n"
            + "Status: " + status;
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
            grid.setHgap(12);
            grid.setVgap(10);
            grid.setPadding(new Insets(16));
            grid.addRow(0, new Label("Payment Amount ($)"), amountField);
            grid.addRow(1, new Label("Payment Method"), methodCombo);
            grid.addRow(2, new Label("Reference Ref"), referenceField);
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

    private final class DeclaredPaymentCard extends ListCell<Payment> {

        @Override
        protected void updateItem(Payment payment, boolean empty) {
            super.updateItem(payment, empty);
            setText(null);
            setGraphic(empty || payment == null ? null : buildCard(payment));
        }

        private VBox buildCard(Payment payment) {
            VBox card = new VBox(10);
            card.getStyleClass().addAll("card", "card-hoverable");
            card.setPadding(new Insets(16));

            String target = payment.getScheduleId() != null
                ? "Contract Installment Payment" : "Property Reservation Deposit";

            HBox header = new HBox(12);
            header.setAlignment(Pos.CENTER_LEFT);

            Label title = new Label(target);
            title.getStyleClass().add("section-title");
            HBox.setHgrow(title, Priority.ALWAYS);

            Label pill = UIHelper.createPill(Format.enumLabel(payment.getStatus()), pillClass(payment.getStatus()));
            Label amountLabel = new Label(Format.paymentAmount(payment.getAmount()));
            amountLabel.getStyleClass().add("section-title");
            amountLabel.setStyle("-fx-text-fill: -c-primary; -fx-font-weight: 700;");

            header.getChildren().addAll(title, pill, amountLabel);

            Label meta = new Label("Method: " + Format.enumLabel(payment.getMethod()) + " • Declared by: "
                + userName(payment.getDeclaredBy()) + " · " + Format.dateTime(payment.getCreatedAt()));
            meta.getStyleClass().add("label-soft");

            Label proof = new Label("Proof reference: "
                + (payment.getProofPath() == null ? "None provided" : payment.getProofPath()));
            proof.getStyleClass().add("hint");

            card.getChildren().addAll(header, meta, proof, actions(payment));
            AnimationUtil.addHoverLift(card);
            return card;
        }

        private HBox actions(Payment payment) {
            HBox actions = new HBox(8);
            actions.setAlignment(Pos.CENTER_LEFT);

            Button confirm = new Button("Confirm payment");
            confirm.getStyleClass().addAll("button", "button-primary");
            confirm.setOnAction(event -> handleConfirm(payment));

            Button reject = new Button("Reject");
            reject.getStyleClass().addAll("button", "button-danger");
            reject.setOnAction(event -> handleReject(payment));

            actions.getChildren().addAll(confirm, reject);
            return actions;
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
            VBox card = new VBox(8);
            card.getStyleClass().addAll("card-subtle", "card-hoverable");
            card.setPadding(new Insets(12));

            HBox header = new HBox(12);
            header.setAlignment(Pos.CENTER_LEFT);

            Label title = new Label("Installment #" + schedule.getInstallmentNo()
                + " • Due " + Format.date(schedule.getDueDate()));
            title.getStyleClass().add("section-title");
            title.setStyle("-fx-font-size: 13px;");
            HBox.setHgrow(title, Priority.ALWAYS);

            Label pill = UIHelper.createPill(Format.enumLabel(schedule.getStatus()), pillClass(schedule.getStatus()));
            header.getChildren().addAll(title, pill);

            Label meta = new Label("Due: " + Format.paymentAmount(schedule.getAmountDue())
                + " • Paid: " + Format.paymentAmount(schedule.getAmountPaid())
                + " • Outstanding: " + Format.paymentAmount(outstanding(schedule)));
            meta.getStyleClass().add("label-soft");

            card.getChildren().addAll(header, meta);

            if (schedule.getStatus() != ScheduleStatus.PAID) {
                Button record = new Button("Record payment");
                record.getStyleClass().addAll("button", "button-primary");
                record.setStyle("-fx-font-size: 11px; -fx-pref-height: 28px;");
                record.setOnAction(event -> openRecordDialog(schedule));
                card.getChildren().add(new HBox(8, record));
            }
            AnimationUtil.addHoverLift(card);
            return card;
        }
    }
}
