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
import co.syntropyhq.aqarat.model.ContractStatus;
import co.syntropyhq.aqarat.model.ContractType;
import co.syntropyhq.aqarat.model.PaymentFrequency;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.Role;
import co.syntropyhq.aqarat.service.AuditService;
import co.syntropyhq.aqarat.service.AuthService;
import co.syntropyhq.aqarat.service.ContractService;
import co.syntropyhq.aqarat.service.PaymentService;
import co.syntropyhq.aqarat.service.PropertyService;
import co.syntropyhq.aqarat.service.ReservationService;
import co.syntropyhq.aqarat.util.Banner;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.AnimationUtil;
import co.syntropyhq.aqarat.util.FieldError;
import co.syntropyhq.aqarat.util.Dialogs;
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.SessionManager;
import co.syntropyhq.aqarat.util.UIHelper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

public class ContractsController {

    @FXML
    private VBox contentBox;
    @FXML
    private Label accessDeniedLabel;
    @FXML
    private Button contractsTabButton;
    @FXML
    private Button draftTabButton;
    @FXML
    private VBox contractsBox;
    @FXML
    private ScrollPane draftBox;
    @FXML
    private ListView<Contract> contractList;

    @FXML
    private TextField propertyIdField;
    @FXML
    private Label propertyError;
    @FXML
    private Label propertySummaryLabel;
    @FXML
    private TextField clientEmailField;
    @FXML
    private Label clientError;
    @FXML
    private Label clientSummaryLabel;
    @FXML
    private ComboBox<ContractType> contractTypeCombo;
    @FXML
    private VBox totalAmountBox;
    @FXML
    private TextField totalAmountField;
    @FXML
    private Label totalAmountError;
    @FXML
    private VBox leaseBox;
    @FXML
    private TextField monthlyRentField;
    @FXML
    private Label monthlyRentError;
    @FXML
    private TextField termMonthsField;
    @FXML
    private Label termMonthsError;
    @FXML
    private DatePicker startDatePicker;
    @FXML
    private Label startDateError;
    @FXML
    private ComboBox<PaymentFrequency> paymentFrequencyCombo;
    @FXML
    private TextField installmentCountField;
    @FXML
    private Label installmentCountError;

    private final AuditService auditService = new AuditService(new AuditDao());
    private final PropertyService propertyService =
        new PropertyService(new PropertyDao(), new PropertyPhotoDao(), new PropertyMessageDao(), auditService);
    private final ReservationService reservationService = new ReservationService(
        new ReservationDao(), new SystemSettingDao(), propertyService, auditService);
    private final PaymentService paymentService = new PaymentService(
        new PaymentScheduleDao(), new PaymentDao(), new ContractDao(), new ReservationDao(),
        new SystemSettingDao(), auditService);
    private final ContractService contractService = new ContractService(
        new ContractDao(), new ReservationDao(), propertyService, reservationService,
        new SystemSettingDao(), auditService, paymentService);
    private final AuthService authService = new AuthService(new UserDao());

    private final Map<Integer, String> propertyTitles = new HashMap<>();
    private final Map<Integer, String> clientNames = new HashMap<>();

    private Property loadedProperty;
    private AppUser loadedClient;

    @FXML
    private void initialize() {
        AppUser user = SessionManager.getCurrentUser();
        if (user == null || (user.getRole() != Role.AGENT && user.getRole() != Role.ADMIN)) {
            denyAccess();
            return;
        }
        contractList.setCellFactory(list -> new ContractCard());
        setLabelConverter(contractTypeCombo, Format::enumLabel);
        contractTypeCombo.setItems(FXCollections.observableArrayList(ContractType.values()));
        contractTypeCombo.getSelectionModel().select(ContractType.SALE);
        contractTypeCombo.valueProperty()
            .addListener((obs, oldValue, newValue) -> updateContractTypeUi(newValue));
        updateContractTypeUi(ContractType.SALE);
        setLabelConverter(paymentFrequencyCombo, Format::enumLabel);
        paymentFrequencyCombo.setItems(FXCollections.observableArrayList(PaymentFrequency.values()));
        paymentFrequencyCombo.getSelectionModel().select(PaymentFrequency.ONE_OFF);
        selectContractsTab();
    }

    private void denyAccess() {
        contentBox.setVisible(false);
        contentBox.setManaged(false);
        accessDeniedLabel.setText("You do not have access to contracts.");
        accessDeniedLabel.setVisible(true);
        accessDeniedLabel.setManaged(true);
    }

    @FXML
    private void handleContractsTab() {
        selectContractsTab();
    }

    @FXML
    private void handleDraftTab() {
        selectDraftTab();
    }

    private void selectContractsTab() {
        contractsTabButton.getStyleClass().setAll("tab-pill-button", "active");
        draftTabButton.getStyleClass().setAll("tab-pill-button");
        contractsBox.setVisible(true);
        contractsBox.setManaged(true);
        draftBox.setVisible(false);
        draftBox.setManaged(false);
        loadContracts();
    }

    private void selectDraftTab() {
        contractsTabButton.getStyleClass().setAll("tab-pill-button");
        draftTabButton.getStyleClass().setAll("tab-pill-button", "active");
        contractsBox.setVisible(false);
        contractsBox.setManaged(false);
        draftBox.setVisible(true);
        draftBox.setManaged(true);
    }

    private void loadContracts() {
        contractList.setPlaceholder(UIHelper.createEmptyState("No contracts yet",
            "Contracts appear here once you draft one against a reserved property. "
            + "Open the Draft tab to start."));
        List<Contract> results;
        try {
            results = contractService.findByAgent(SessionManager.getCurrentUser().getId());
        } catch (SQLException e) {
            contractList.getItems().clear();
            contractList.setPlaceholder(Banner.failure("Contracts could not be loaded",
                "The database did not answer. Nothing has been lost - this panel only reads.",
                this::loadContracts));
            return;
        }
        contractList.setItems(FXCollections.observableArrayList(results));
    }

    @FXML
    private void handleLoadProperty() {
        FieldError.clear(propertyIdField, propertyError);
        propertySummaryLabel.setText("");
        loadedProperty = null;
        Integer id = parsePositiveInt(propertyIdField.getText());
        if (id == null) {
            FieldError.show(propertyIdField, propertyError, "Enter a property id.");
            return;
        }
        Property property = fetchProperty(id);
        if (property == null) {
            FieldError.show(propertyIdField, propertyError, "No property with that id.");
            return;
        }
        loadedProperty = property;
        propertySummaryLabel.setText(propertySummary(property));
    }

    private Property fetchProperty(int id) {
        try {
            return propertyService.findById(id);
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return null;
        }
    }

    private String propertySummary(Property property) {
        String termHint = property.getMinTermMonths() == null && property.getMaxTermMonths() == null
            ? "" : " • lease term " + termBounds(property);
        return property.getTitle() + " · " + Format.enumLabel(property.getStatus())
            + " · " + Format.enumLabel(property.getDealType()) + termHint;
    }

    private String termBounds(Property property) {
        String min = property.getMinTermMonths() == null ? "any" : property.getMinTermMonths() + "mo";
        String max = property.getMaxTermMonths() == null ? "any" : property.getMaxTermMonths() + "mo";
        return min + " to " + max;
    }

    @FXML
    private void handleFindClient() {
        FieldError.clear(clientEmailField, clientError);
        clientSummaryLabel.setText("");
        loadedClient = null;
        String email = clientEmailField.getText().trim();
        if (email.isEmpty()) {
            FieldError.show(clientEmailField, clientError, "Enter the client's email.");
            return;
        }
        AppUser client = fetchClient(email);
        if (client == null || client.getRole() != Role.CUSTOMER) {
            FieldError.show(clientEmailField, clientError, "No customer account with that email.");
            return;
        }
        loadedClient = client;
        clientSummaryLabel.setText(client.getFullName() + " (" + client.getEmail() + ")");
    }

    private AppUser fetchClient(String email) {
        try {
            return authService.findByEmail(email);
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return null;
        }
    }

    private void updateContractTypeUi(ContractType type) {
        boolean isLease = type == ContractType.LEASE;
        leaseBox.setVisible(isLease);
        leaseBox.setManaged(isLease);
        totalAmountBox.setVisible(!isLease);
        totalAmountBox.setManaged(!isLease);
        if (isLease) {
            totalAmountField.clear();
            FieldError.clear(totalAmountField, totalAmountError);
        } else {
            monthlyRentField.clear();
            termMonthsField.clear();
            FieldError.clear(monthlyRentField, monthlyRentError);
            FieldError.clear(termMonthsField, termMonthsError);
        }
    }

    @FXML
    private void handleDraft() {
        clearFormErrors();
        if (!checkPropertyAndClientLoaded()) {
            return;
        }
        Contract contract = new Contract();
        contract.setPropertyId(loadedProperty.getId());
        contract.setClientId(loadedClient.getId());
        contract.setAgentId(SessionManager.getCurrentUser().getId());
        ContractType type = contractTypeCombo.getValue();
        contract.setContractType(type);
        contract.setPaymentFrequency(paymentFrequencyCombo.getValue());

        boolean amountsOk = type == ContractType.LEASE
            ? collectLeaseFields(contract) : collectSaleFields(contract);
        contract.setStartDate(requireStartDate());
        Integer installmentCount = requireInstallmentCount();
        if (!amountsOk || contract.getStartDate() == null || installmentCount == null) {
            return;
        }
        contract.setInstallmentCount(installmentCount);
        submitDraft(contract);
    }

    private boolean checkPropertyAndClientLoaded() {
        boolean ok = true;
        if (loadedProperty == null) {
            FieldError.show(propertyIdField, propertyError, "Load a property first.");
            ok = false;
        }
        if (loadedClient == null) {
            FieldError.show(clientEmailField, clientError, "Find a client first.");
            ok = false;
        }
        return ok;
    }

    private boolean collectLeaseFields(Contract contract) {
        BigDecimal monthlyRent =
            requirePositiveDecimal(monthlyRentField, monthlyRentError, "Enter the monthly rent.");
        contract.setMonthlyRent(monthlyRent);
        Integer termMonths = parsePositiveInt(termMonthsField.getText());
        if (termMonths == null) {
            FieldError.show(termMonthsField, termMonthsError, "Enter the lease term in months.");
        } else if (loadedProperty.getMinTermMonths() != null
                && termMonths < loadedProperty.getMinTermMonths()) {
            FieldError.show(termMonthsField, termMonthsError,
                "The owner requires at least " + loadedProperty.getMinTermMonths() + " months.");
        } else if (loadedProperty.getMaxTermMonths() != null
                && termMonths > loadedProperty.getMaxTermMonths()) {
            FieldError.show(termMonthsField, termMonthsError,
                "The owner allows at most " + loadedProperty.getMaxTermMonths() + " months.");
        }
        contract.setTermMonths(termMonths);
        return monthlyRent != null && isValid(termMonthsError);
    }

    private boolean collectSaleFields(Contract contract) {
        BigDecimal totalAmount =
            requirePositiveDecimal(totalAmountField, totalAmountError, "Enter the sale price.");
        contract.setTotalAmount(totalAmount);
        return totalAmount != null;
    }

    private LocalDate requireStartDate() {
        LocalDate date = startDatePicker.getValue();
        if (date == null) {
            FieldError.show(startDatePicker, startDateError, "Choose a start date.");
        }
        return date;
    }

    private Integer requireInstallmentCount() {
        Integer count = parsePositiveInt(installmentCountField.getText());
        if (count == null) {
            FieldError.show(installmentCountField, installmentCountError,
                "Enter a whole number of at least 1.");
        }
        return count;
    }

    private void submitDraft(Contract contract) {
        try {
            contractService.draft(contract);
        } catch (ContractService.DraftRefusedException e) {
            FieldError.show(propertyIdField, propertyError, e.getMessage());
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("Contract drafted",
            "Nothing is committed yet. Activating it is what moves the property under contract and starts the payment schedule.");
        clearDraftForm();
        selectContractsTab();
    }

    private void handleActivate(Contract contract) {
        boolean go = Dialogs.ask("Activate this contract?")
            .about("Contract #" + contract.getId())
            .because("The property moves under contract and comes off the market, and the payment "
                + "schedule starts running. A contract cannot be returned to draft.")
            .confirm("Activate it")
            .cancel("Not yet")
            .destructive()
            .show();
        if (!go) {
            return;
        }
        try {
            contractService.activate(contract.getId());
        } catch (ContractService.InvalidTransitionException | ContractService.DraftRefusedException
                | PropertyService.InvalidTransitionException
                | ReservationService.InvalidTransitionException e) {
            AlertUtil.showError(e.getMessage());
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("Contract active",
            "The property is under contract and off the market. The payment schedule is now running.");
        loadContracts();
    }

    private void handleClose(Contract contract) {
        boolean go = Dialogs.ask("Close this contract?")
            .about("Contract #" + contract.getId())
            .because("Closing records the contract as completed. Do this once every instalment "
                + "has been paid - the schedule stays on the record either way.")
            .confirm("Close it")
            .cancel("Keep it open")
            .show();
        if (!go) {
            return;
        }
        try {
            contractService.close(contract.getId());
        } catch (ContractService.InvalidTransitionException
                | PropertyService.InvalidTransitionException e) {
            AlertUtil.showError(e.getMessage());
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("Contract closed",
            "Recorded as completed. The schedule and every payment against it stay on the record.");
        loadContracts();
    }

    private void handleTerminate(Contract contract) {
        boolean go = Dialogs.ask("Terminate this contract?")
            .about("Contract #" + contract.getId())
            .because("The property returns to the market and the instalments still scheduled are "
                + "cancelled. Everything already paid stays on the record. This cannot be undone.")
            .confirm("Terminate the contract")
            .cancel("Keep it")
            .destructive()
            .show();
        if (!go) {
            return;
        }
        try {
            contractService.terminate(contract.getId());
        } catch (ContractService.InvalidTransitionException
                | PropertyService.InvalidTransitionException e) {
            AlertUtil.showError(e.getMessage());
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showUndone("Contract terminated",
            "The property is back on the market and the remaining instalments are cancelled. What was paid stays on the record.");
        loadContracts();
    }

    private BigDecimal requirePositiveDecimal(TextField field, Label errorLabel, String emptyMessage) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            FieldError.show(field, errorLabel, emptyMessage);
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(text);
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                FieldError.show(field, errorLabel, "Enter a number greater than zero.");
                return null;
            }
            return value.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            FieldError.show(field, errorLabel, "Enter a valid number.");
            return null;
        }
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

    private boolean isValid(Label errorLabel) {
        return !FieldError.isShown(errorLabel);
    }

    private void clearFormErrors() {
        FieldError.clear(propertyIdField, propertyError);
        FieldError.clear(clientEmailField, clientError);
        FieldError.clear(totalAmountField, totalAmountError);
        FieldError.clear(monthlyRentField, monthlyRentError);
        FieldError.clear(termMonthsField, termMonthsError);
        FieldError.clear(startDatePicker, startDateError);
        FieldError.clear(installmentCountField, installmentCountError);
    }

    private void clearDraftForm() {
        clearFormErrors();
        propertyIdField.clear();
        propertySummaryLabel.setText("");
        clientEmailField.clear();
        clientSummaryLabel.setText("");
        totalAmountField.clear();
        monthlyRentField.clear();
        termMonthsField.clear();
        startDatePicker.setValue(null);
        installmentCountField.clear();
        loadedProperty = null;
        loadedClient = null;
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

    private String amountText(Contract contract) {
        return contract.getContractType() == ContractType.SALE
            ? Format.salePrice(contract.getTotalAmount())
            // monthlyRent already carries the unit; the term is what this adds.
            : Format.monthlyRent(contract.getMonthlyRent())
                + " for " + contract.getTermMonths() + " months";
    }

    private final class ContractCard extends ListCell<Contract> {

        @Override
        protected void updateItem(Contract contract, boolean empty) {
            super.updateItem(contract, empty);
            setText(null);
            setGraphic(empty || contract == null ? null : buildCard(contract));
        }

        private VBox buildCard(Contract contract) {
            VBox card = new VBox(10);
            card.getStyleClass().addAll("card", "card-hoverable");
            card.setPadding(new Insets(16));

            // The number every other screen identifies this contract by - the
            // Payments lookup asks for it, and the receipt prints it - so it
            // has to be readable somewhere rather than guessed at.
            Label reference = new Label("CONTRACT #" + contract.getId());
            reference.getStyleClass().add("eyebrow");

            HBox header = new HBox(12);
            header.setAlignment(Pos.CENTER_LEFT);

            Label title = new Label(propertyTitle(contract.getPropertyId()));
            title.getStyleClass().add("section-title");
            HBox.setHgrow(title, Priority.ALWAYS);

            Label pill = UIHelper.createPill(Format.enumLabel(contract.getStatus()), pillClass(contract.getStatus()));
            Label amountLabel = new Label(amountText(contract));
            amountLabel.getStyleClass().add("price-display");

            header.getChildren().addAll(title, pill, amountLabel);

            Label meta = new Label("" + clientName(contract.getClientId()) + " · "
                + Format.enumLabel(contract.getContractType())
                + " • started " + Format.date(contract.getStartDate()));
            meta.getStyleClass().add("label-soft");

            card.getChildren().addAll(reference, header, meta);

            HBox actions = buildActions(contract);
            if (!actions.getChildren().isEmpty()) {
                card.getChildren().add(actions);
            }

            AnimationUtil.addHoverLift(card);
            return card;
        }

        private HBox buildActions(Contract contract) {
            HBox actions = new HBox(8);
            actions.setAlignment(Pos.CENTER_LEFT);

            if (contract.getStatus() == ContractStatus.DRAFT) {
                addButton(actions, "Activate contract", "button-primary", () -> handleActivate(contract));
            }
            if (contract.getStatus() == ContractStatus.ACTIVE) {
                addButton(actions, "Close contract", "button-secondary", () -> handleClose(contract));
                addButton(actions, "Terminate contract", "button-danger", () -> handleTerminate(contract));
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
