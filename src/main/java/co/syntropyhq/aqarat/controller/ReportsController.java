package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.dao.OverduePayment;
import co.syntropyhq.aqarat.dao.ReportDao;
import co.syntropyhq.aqarat.dao.RevenueByPeriod;
import co.syntropyhq.aqarat.dao.SystemSettingDao;
import co.syntropyhq.aqarat.dao.TimeOnMarketRow;
import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.Role;
import co.syntropyhq.aqarat.service.ReportService;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.AnimationUtil;
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.SessionManager;
import co.syntropyhq.aqarat.util.UIHelper;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

public class ReportsController {

    @FXML
    private VBox contentBox;
    @FXML
    private Label accessDeniedLabel;
    @FXML
    private Button revenueTabButton;
    @FXML
    private Button overdueTabButton;
    @FXML
    private Button marketTabButton;
    @FXML
    private VBox revenueBox;
    @FXML
    private VBox overdueBox;
    @FXML
    private VBox marketBox;

    @FXML
    private DatePicker revenueFromPicker;
    @FXML
    private DatePicker revenueToPicker;
    @FXML
    private TableView<RevenueByPeriod> revenueTable;
    @FXML
    private TableView<OverduePayment> overdueTable;
    @FXML
    private TableView<TimeOnMarketRow> marketTable;

    private final ReportService reportService = new ReportService(new ReportDao(), new SystemSettingDao());

    @FXML
    private void initialize() {
        AppUser user = SessionManager.getCurrentUser();
        if (user == null || user.getRole() != Role.ADMIN) {
            denyAccess();
            return;
        }
        buildRevenueColumns();
        buildOverdueColumns();
        buildMarketColumns();
        revenueFromPicker.setValue(LocalDate.now().minusMonths(12));
        revenueToPicker.setValue(LocalDate.now());
        emptyState(revenueTable, "No revenue data for this period.");
        emptyState(overdueTable, "No payments currently overdue.");
        emptyState(marketTable, "No closed properties with valuation comparisons found.");
        selectRevenueTab();
    }

    private void denyAccess() {
        contentBox.setVisible(false);
        contentBox.setManaged(false);
        accessDeniedLabel.setText("You do not have access to reports.");
        accessDeniedLabel.setVisible(true);
        accessDeniedLabel.setManaged(true);
    }

    @FXML
    private void handleRevenueTab() {
        selectRevenueTab();
    }

    @FXML
    private void handleOverdueTab() {
        selectOverdueTab();
    }

    @FXML
    private void handleMarketTab() {
        selectMarketTab();
    }

    private void selectRevenueTab() {
        setActiveTab(revenueTabButton, overdueTabButton, marketTabButton);
        setActiveBox(revenueBox, overdueBox, marketBox);
        loadRevenue();
    }

    private void selectOverdueTab() {
        setActiveTab(overdueTabButton, revenueTabButton, marketTabButton);
        setActiveBox(overdueBox, revenueBox, marketBox);
        loadOverdue();
    }

    private void selectMarketTab() {
        setActiveTab(marketTabButton, revenueTabButton, overdueTabButton);
        setActiveBox(marketBox, revenueBox, overdueBox);
        loadMarket();
    }

    private void setActiveTab(Button active, Button... rest) {
        active.getStyleClass().setAll("tab-pill-button", "active");
        for (Button button : rest) {
            button.getStyleClass().setAll("tab-pill-button");
        }
    }

    private void setActiveBox(VBox active, VBox... rest) {
        active.setVisible(true);
        active.setManaged(true);
        for (VBox box : rest) {
            box.setVisible(false);
            box.setManaged(false);
        }
    }

    @FXML
    private void handleRunRevenue() {
        LocalDate from = revenueFromPicker.getValue();
        LocalDate to = revenueToPicker.getValue();
        if (from == null || to == null || from.isAfter(to)) {
            AlertUtil.showError("Choose a from date on or before the to date.");
            return;
        }
        loadRevenue();
    }

    private void loadRevenue() {
        try {
            revenueTable.setItems(FXCollections.observableArrayList(
                reportService.revenueByPeriod(revenueFromPicker.getValue(), revenueToPicker.getValue())));
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
        }
    }

    private void loadOverdue() {
        try {
            overdueTable.setItems(FXCollections.observableArrayList(reportService.overduePayments()));
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
        }
    }

    private void loadMarket() {
        try {
            marketTable.setItems(FXCollections.observableArrayList(reportService.timeOnMarket()));
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
        }
    }

    private void buildRevenueColumns() {
        addColumn(revenueTable, "Billing Period", 130, RevenueByPeriod::getPeriod);
        addColumn(revenueTable, "Activated Contracts", 150, row -> String.valueOf(row.getContractCount()));
        addNumericColumn(revenueTable, "Total Contract Value", 180,
            row -> Format.paymentAmount(row.getTotalValue()));
        addNumericColumn(revenueTable, "Agency Commission", 180,
            row -> Format.paymentAmount(row.getTotalCommission()));
    }

    private void buildOverdueColumns() {
        addColumn(overdueTable, "Contract Ref", 110, row -> "#" + row.getContractId());
        addColumn(overdueTable, "Property Title", 220, OverduePayment::getPropertyTitle);
        addColumn(overdueTable, "Client Name", 160, OverduePayment::getClientName);
        addColumn(overdueTable, "Installment #", 110, row -> String.valueOf(row.getInstallmentNo()));
        addColumn(overdueTable, "Due Date", 120, row -> Format.date(row.getDueDate()));
        addNumericColumn(overdueTable, "Due Amount", 120, row -> Format.paymentAmount(row.getAmountDue()));
        addNumericColumn(overdueTable, "Paid Amount", 120, row -> Format.paymentAmount(row.getAmountPaid()));
        addNumericColumn(overdueTable, "Days Overdue", 110, row -> String.valueOf(row.getDaysOverdue()));
    }

    private void buildMarketColumns() {
        addColumn(marketTable, "Property Title", 240, TimeOnMarketRow::getTitle);
        addColumn(marketTable, "Published Date", 140, row -> Format.dateTime(row.getPublishedAt()));
        addColumn(marketTable, "Closed Date", 140, row -> Format.dateTime(row.getClosedAt()));
        addNumericColumn(marketTable, "Days on Market", 130, row -> String.valueOf(row.getDaysOnMarket()));
        addNumericColumn(marketTable, "Asking Price", 140, row -> Format.salePrice(row.getAskingPrice()));
        addNumericColumn(marketTable, "Algorithmic Estimate", 150, row -> Format.salePrice(row.getEstimatedValue()));
        addNumericColumn(marketTable, "Valuation Premium", 140,
            row -> Format.percentage(row.getPremiumPercent()));
    }

    private <T> void addColumn(TableView<T> table, String header, double width,
            Function<T, String> textFor) {
        TableColumn<T, String> column = new TableColumn<>(header);
        column.setPrefWidth(width);
        column.setCellValueFactory(data ->
            new SimpleStringProperty(textFor.apply(data.getValue())));
        table.getColumns().add(column);
    }

    private <T> void addNumericColumn(TableView<T> table, String header, double width,
            Function<T, String> textFor) {
        TableColumn<T, String> column = new TableColumn<>(header);
        column.setPrefWidth(width);
        column.setCellValueFactory(data ->
            new SimpleStringProperty(textFor.apply(data.getValue())));
        column.setCellFactory(col -> new TableCell<T, String>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                getStyleClass().add("numeric");
            }
        });
        table.getColumns().add(column);
    }

    private <T> void emptyState(TableView<T> table, String message) {
        table.setPlaceholder(UIHelper.createEmptyState(message, "Analytics update automatically as property transactions occur."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }
}
