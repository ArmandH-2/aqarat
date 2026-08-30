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
import co.syntropyhq.aqarat.util.AppIcons;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.AnimationUtil;
import co.syntropyhq.aqarat.util.RequiredLabel;
import co.syntropyhq.aqarat.util.SceneCapture;
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
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.Bindings;
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
import javafx.scene.layout.Region;
import javafx.stage.Stage;
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

    /**
     * Asks the client what they paid.
     *
     * <p>Built here rather than in FXML because it is a dialog, so it needs the
     * application's stylesheet and icon applied by hand — without them a JavaFX
     * Dialog opens with toolkit defaults and looks like it belongs to another
     * program. The OK button is disabled until the two fields the handler
     * refuses without have been filled, so the dialog cannot be submitted into
     * an error it already knows about.
     */
    private void openDeclareDialog(Contract contract, PaymentSchedule schedule) {
        DeclareForm form = new DeclareForm(outstanding(schedule));

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Declare a payment");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setContent(form.layout(contract, schedule));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        Button confirm = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        confirm.setText("Declare it");
        confirm.getStyleClass().addAll("button", "button-primary");
        confirm.disableProperty().bind(form.incomplete());
        // Enter submits, which is what someone finishing a short form expects,
        // and it stays inert while the button is disabled.
        confirm.setDefaultButton(true);

        Button cancel = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        cancel.getStyleClass().addAll("button", "button-secondary");

        dialog.getDialogPane().getStyleClass().add("app-dialog");
        dialog.getDialogPane().getStylesheets()
            .add(getClass().getResource("/css/app.css").toExternalForm());
        AppIcons.apply((Stage) dialog.getDialogPane().getScene().getWindow());
        // A dialog owns its own scene, so it needs its own capture hook to be
        // reviewable at all: Windows cannot screenshot it from outside either.
        SceneCapture.install(dialog.getDialogPane().getScene());

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
            // monthlyRent already carries the unit; the term is what this adds.
            : Format.monthlyRent(contract.getMonthlyRent())
                + " for " + contract.getTermMonths() + " months";
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
            methodCombo.setMaxWidth(Double.MAX_VALUE);
            proofPathField.setPromptText("Transfer slip number, or a path to the file");
            referenceField.setPromptText("Bank or cheque reference");
        }

        /** True while a required field is empty, which is what disables the button. */
        private BooleanBinding incomplete() {
            return Bindings.createBooleanBinding(
                () -> amountField.getText() == null || amountField.getText().isBlank()
                    || proofPathField.getText() == null || proofPathField.getText().isBlank(),
                amountField.textProperty(), proofPathField.textProperty());
        }

        private VBox layout(Contract contract, PaymentSchedule schedule) {
            Label eyebrow = new Label(("Instalment " + schedule.getInstallmentNo()
                + " · " + propertyTitle(contract.getPropertyId())).toUpperCase());
            eyebrow.getStyleClass().add("eyebrow");

            Label heading = new Label(Format.paymentAmount(outstanding(schedule)) + " outstanding");
            heading.getStyleClass().add("price-display");

            Label note = new Label("An agent checks this against the proof you give before it "
                + "counts towards the contract.");
            note.getStyleClass().add("hint");
            note.setWrapText(true);
            note.setMaxWidth(360);

            VBox form = new VBox(14,
                field(new RequiredLabel(), "Amount", amountField),
                field(new Label(), "How you paid", methodCombo),
                field(new Label(), "Reference", referenceField),
                field(new RequiredLabel(), "Proof", proofPathField));

            VBox content = new VBox(16, new VBox(3, eyebrow, heading), note, form);
            content.setPadding(new Insets(4, 4, 8, 4));
            content.setPrefWidth(380);
            return content;
        }

        /* One field: its label, and the control under it. RequiredLabel carries
           the asterisk, so which fields are mandatory is visible rather than
           discovered by pressing the button. */
        private VBox field(Label label, String text, javafx.scene.Node control) {
            label.setText(text);
            if (!(label instanceof RequiredLabel)) {
                label.getStyleClass().add("label-soft");
            }
            if (control instanceof Region region) {
                region.setMaxWidth(Double.MAX_VALUE);
            }
            return new VBox(5, label, control);
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
            amountLabel.getStyleClass().add("price-display");

            header.getChildren().addAll(title, pill, amountLabel);

            Label meta = new Label(Format.enumLabel(contract.getContractType())
                + " · started " + Format.date(contract.getStartDate()));
            meta.getStyleClass().add("hint");

            card.getChildren().addAll(header, meta, scheduleSection(contract), paymentsSection(contract));
            AnimationUtil.addHoverLift(card);
            return card;
        }

        /*
         * A payment schedule is a table, and it was being rendered as a stack of
         * sentences that repeated "Installment #", "Due" and "(Paid: …)" on every
         * line. Columns let the eye run down the dates and amounts, and the
         * summary above answers the only question most people open this for:
         * how much is left.
         */
        private VBox scheduleSection(Contract contract) {
            VBox section = new VBox(11);
            section.getStyleClass().add("card-subtle");

            List<PaymentSchedule> schedule = scheduleFor(contract);
            if (schedule.isEmpty()) {
                Label heading = new Label("Payment schedule");
                heading.getStyleClass().add("section-title");
                Label empty = new Label("No schedule has been generated for this contract yet.");
                empty.getStyleClass().add("hint");
                section.getChildren().addAll(heading, empty);
                return section;
            }

            section.getChildren().addAll(scheduleSummary(schedule), scheduleTable(contract, schedule));
            return section;
        }

        /** Instalments settled, money settled, and a bar showing the proportion. */
        private VBox scheduleSummary(List<PaymentSchedule> schedule) {
            int paid = 0;
            BigDecimal settled = BigDecimal.ZERO;
            BigDecimal total = BigDecimal.ZERO;
            for (PaymentSchedule row : schedule) {
                total = total.add(row.getAmountDue());
                if (row.getAmountPaid() != null) {
                    settled = settled.add(row.getAmountPaid());
                }
                if (row.getStatus() == ScheduleStatus.PAID) {
                    paid++;
                }
            }

            Label heading = new Label("Payment schedule");
            heading.getStyleClass().add("section-title");

            Label progress = new Label(paid + " of " + schedule.size() + " instalments settled  ·  "
                + Format.paymentAmount(settled) + " of " + Format.paymentAmount(total));
            progress.getStyleClass().add("hint");

            double fraction = total.signum() == 0
                ? 0 : settled.divide(total, 4, RoundingMode.HALF_UP).doubleValue();
            Region filled = new Region();
            filled.getStyleClass().add("progress-filled");
            Region rest = new Region();
            rest.setMinWidth(0);
            HBox.setHgrow(filled, Priority.ALWAYS);
            HBox.setHgrow(rest, Priority.ALWAYS);
            filled.setPrefWidth(Math.max(0.001, fraction) * 100);
            rest.setPrefWidth((1 - fraction) * 100);

            HBox bar = new HBox(filled, rest);
            bar.getStyleClass().add("progress-track");

            return new VBox(7, heading, progress, bar);
        }

        private VBox scheduleTable(Contract contract, List<PaymentSchedule> schedule) {
            // A column that reads "—" on every row is furniture. It appears only
            // when some instalment is genuinely part paid, which is the single
            // case where due and settled differ and both are worth showing.
            boolean anyPartial = schedule.stream().anyMatch(this::isPartlyPaid);

            VBox table = new VBox(0);
            table.getChildren().add(scheduleHeader(anyPartial));
            for (PaymentSchedule row : schedule) {
                table.getChildren().add(scheduleRow(contract, row, anyPartial));
            }
            return table;
        }

        private boolean isPartlyPaid(PaymentSchedule row) {
            BigDecimal paid = row.getAmountPaid();
            return paid != null
                && paid.signum() > 0
                && paid.compareTo(row.getAmountDue()) < 0;
        }

        private HBox scheduleHeader(boolean withSettled) {
            HBox header = new HBox(12);
            header.getStyleClass().add("schedule-header");
            header.setAlignment(Pos.CENTER_LEFT);
            header.getChildren().addAll(
                columnLabel("#", 34, Pos.CENTER_LEFT),
                columnLabel("DUE", 120, Pos.CENTER_LEFT),
                columnLabel("AMOUNT", 130, Pos.CENTER_RIGHT));
            if (withSettled) {
                header.getChildren().add(columnLabel("PART PAID", 130, Pos.CENTER_RIGHT));
            }
            header.getChildren().addAll(grower(), columnLabel("", 238, Pos.CENTER_RIGHT));
            return header;
        }

        private Label columnLabel(String text, double width, Pos alignment) {
            Label label = new Label(text);
            label.getStyleClass().add("schedule-column");
            label.setMinWidth(width);
            label.setPrefWidth(width);
            label.setAlignment(alignment);
            return label;
        }

        private Region grower() {
            Region spacer = new Region();
            spacer.setMinWidth(0);
            HBox.setHgrow(spacer, Priority.ALWAYS);
            return spacer;
        }

        private HBox scheduleRow(Contract contract, PaymentSchedule row, boolean withSettled) {
            HBox line = new HBox(12);
            line.getStyleClass().add("schedule-row");
            line.setAlignment(Pos.CENTER_LEFT);

            Label number = new Label(String.valueOf(row.getInstallmentNo()));
            number.getStyleClass().add("hint");
            number.setMinWidth(34);
            number.setPrefWidth(34);

            Label due = new Label(Format.date(row.getDueDate()));
            due.getStyleClass().add("body");
            due.setMinWidth(120);
            due.setPrefWidth(120);

            Label amount = new Label(Format.paymentAmount(row.getAmountDue()));
            amount.getStyleClass().add("numeric");
            amount.setMinWidth(130);
            amount.setPrefWidth(130);
            amount.setAlignment(Pos.CENTER_RIGHT);

            Label pill = UIHelper.createPill(
                Format.enumLabel(row.getStatus()), pillClass(row.getStatus()));

            HBox trailing = new HBox(9, pill);
            trailing.setAlignment(Pos.CENTER_RIGHT);
            trailing.setMinWidth(238);
            trailing.setPrefWidth(238);

            if (row.getStatus() != ScheduleStatus.PAID) {
                Button declare = new Button("Declare payment");
                declare.getStyleClass().addAll("button", "button-secondary", "button-compact");
                declare.setOnAction(event -> openDeclareDialog(contract, row));
                trailing.getChildren().add(declare);
            }

            line.getChildren().addAll(number, due, amount);
            if (withSettled) {
                boolean partial = isPartlyPaid(row);
                Label paid = new Label(partial ? Format.paymentAmount(row.getAmountPaid()) : "—");
                paid.getStyleClass().add(partial ? "numeric" : "hint");
                paid.setMinWidth(130);
                paid.setPrefWidth(130);
                paid.setAlignment(Pos.CENTER_RIGHT);
                line.getChildren().add(paid);
            }
            line.getChildren().addAll(grower(), trailing);
            return line;
        }

        private VBox paymentsSection(Contract contract) {
            VBox section = new VBox(8);
            section.getStyleClass().add("card-subtle");

            Label heading = new Label("Payments received");
            heading.getStyleClass().add("section-title");

            List<Payment> payments = paymentsFor(contract);
            if (payments.isEmpty()) {
                Label empty = new Label("No recorded payments yet.");
                empty.getStyleClass().add("hint");
                section.getChildren().addAll(heading, empty);
                return section;
            }

            VBox rows = new VBox(0);
            for (Payment payment : payments) {
                rows.getChildren().add(paymentRow(contract, payment));
            }
            section.getChildren().addAll(heading, rows);
            return section;
        }

        /*
         * Columns, and the date without a time. Every seeded payment was booked
         * at midnight UTC, which rendered as "02:00" in Beirut on every single
         * row — a number that means nothing and looks like a defect. A payment
         * is a thing that happened on a day.
         */
        private HBox paymentRow(Contract contract, Payment payment) {
            HBox line = new HBox(12);
            line.getStyleClass().add("schedule-row");
            line.setAlignment(Pos.CENTER_LEFT);

            Label amount = new Label(Format.paymentAmount(payment.getAmount()));
            amount.getStyleClass().add("numeric");
            amount.setMinWidth(130);
            amount.setPrefWidth(130);

            Label when = new Label(Format.date(payment.getPaidAt().toLocalDate()));
            when.getStyleClass().add("body");
            when.setMinWidth(120);
            when.setPrefWidth(120);

            Label method = new Label(Format.enumLabel(payment.getMethod()));
            method.getStyleClass().add("hint");

            Region spacer = new Region();
            spacer.setMinWidth(0);
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label pill = UIHelper.createPill(
                Format.enumLabel(payment.getStatus()), pillClass(payment.getStatus()));

            HBox trailing = new HBox(9, pill);
            trailing.setAlignment(Pos.CENTER_RIGHT);

            if (payment.getStatus() == PaymentStatus.CONFIRMED) {
                Button receipt = new Button("Receipt");
                receipt.getStyleClass().addAll("button", "button-secondary", "button-compact");
                receipt.setOnAction(event -> showReceipt(contract, payment));
                trailing.getChildren().add(receipt);
            }

            line.getChildren().addAll(amount, when, method, spacer, trailing);
            return line;
        }
    }
}
