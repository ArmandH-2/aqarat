package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.dao.AuditDao;
import co.syntropyhq.aqarat.dao.ContractDao;
import co.syntropyhq.aqarat.dao.PaymentDao;
import co.syntropyhq.aqarat.dao.PaymentScheduleDao;
import co.syntropyhq.aqarat.dao.PropertyDao;
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
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.FieldError;
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.SessionManager;
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
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

// Two tabs, the same shape ReviewQueueController and ViewingsController
// already use: a list of what the agent has, and a form to add to it.
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
    private VBox draftBox;
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
        new PropertyService(new PropertyDao(), new PropertyPhotoDao(), auditService);
    private final ReservationService reservationService = new ReservationService(
        new ReservationDao(), new SystemSettingDao(), propertyService, auditService);
    // PaymentService generates the schedule on the connection activate() is
    // already holding, so the contract and its installments arrive together.
    private final PaymentService paymentService = new PaymentService(
        new PaymentScheduleDao(), new PaymentDao(), new SystemSettingDao(), auditService);
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
        contractsTabButton.getStyleClass().setAll("button", "button-primary");
        draftTabButton.getStyleClass().setAll("button", "button-secondary");
        contractsBox.setVisible(true);
        contractsBox.setManaged(true);
        draftBox.setVisible(false);
        draftBox.setManaged(false);
        loadContracts();
    }

    private void selectDraftTab() {
        contractsTabButton.getStyleClass().setAll("button", "button-secondary");
        draftTabButton.getStyleClass().setAll("button", "button-primary");
        contractsBox.setVisible(false);
        contractsBox.setManaged(false);
        draftBox.setVisible(true);
        draftBox.setManaged(true);
    }

    private void loadContracts() {
        Label empty = new Label("You have not drafted any contracts yet.");
        empty.getStyleClass().add("empty-state");
        contractList.setPlaceholder(empty);
        List<Contract> results;
        try {
            results = contractService.findByAgent(SessionManager.getCurrentUser().getId());
        } catch (SQLException e) {
            AlertUtil.showError("Could not load contracts. Check that SQL Server is running.");
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
        return property.getTitle() + " • " + Format.enumLabel(property.getStatus())
            + " • " + Format.enumLabel(property.getDealType()) + termHint;
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
        clientSummaryLabel.setText(client.getFullName());
    }

    private AppUser fetchClient(String email) {
        try {
            return authService.findByEmail(email);
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return null;
        }
    }

    // The term-only fields only mean something for a lease
    // (ck_contract_lease), matching how SubmitPropertyController drives its
    // own deal-type-dependent fields from one listener.
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

    // The term is checked against the property's own min/max here, inline,
    // before the service is ever called - BUILD-ORDER.md phase 5 calls this
    // out by name: "term validation fires here".
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
        AlertUtil.showInfo("The contract has been drafted. Activate it from the contracts list.");
        clearDraftForm();
        selectContractsTab();
    }

    private void handleActivate(Contract contract) {
        if (!AlertUtil.confirm(
                "Activate this contract? The property moves under contract and this cannot be undone.")) {
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
        AlertUtil.showInfo("The contract is now active.");
        loadContracts();
    }

    private void handleClose(Contract contract) {
        if (!AlertUtil.confirm("Close this contract?")) {
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
        AlertUtil.showInfo("The contract is closed.");
        loadContracts();
    }

    private void handleTerminate(Contract contract) {
        if (!AlertUtil.confirm("Terminate this contract? The property returns to the market.")) {
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
        AlertUtil.showInfo("The contract is terminated.");
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

    // Matches the status-to-pill table in docs/UI-STYLE.md exactly.
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
            : Format.monthlyRent(contract.getMonthlyRent()) + " for " + contract.getTermMonths() + " months";
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

            Label meta = new Label(clientName(contract.getClientId()) + " • "
                + Format.enumLabel(contract.getContractType()) + " • " + amountText(contract)
                + " • from " + Format.date(contract.getStartDate()));
            meta.getStyleClass().add("label-soft");

            VBox card = new VBox(8, header, meta);
            card.getStyleClass().add("card");
            card.setPadding(new Insets(16));

            HBox actions = buildActions(contract);
            if (!actions.getChildren().isEmpty()) {
                card.getChildren().add(actions);
            }
            return card;
        }

        private HBox buildActions(Contract contract) {
            HBox actions = new HBox(8);
            if (contract.getStatus() == ContractStatus.DRAFT) {
                addButton(actions, "Activate", "button-primary", () -> handleActivate(contract));
            }
            if (contract.getStatus() == ContractStatus.ACTIVE) {
                addButton(actions, "Close", "button-secondary", () -> handleClose(contract));
                addButton(actions, "Terminate", "button-danger", () -> handleTerminate(contract));
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
