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
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.SessionManager;
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

// Admin-only (DESIGN.md section 5). Three tabs, one table each - the same
// tab-toggle shape ReviewQueueController and PaymentsController already use,
// just with three buttons instead of two.
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
        emptyState(revenueTable, "No contracts activated in this period.");
        emptyState(overdueTable, "No payments are overdue.");
        // The seeded data values properties up to the point they are published
        // and no further, so nothing that has closed carries an estimate to
        // compare its asking price against. This report fills in as properties
        // are valued and then closed through the application.
        emptyState(marketTable,
            "No closed property has been valued yet, so there is nothing to compare.");
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
        active.getStyleClass().setAll("button", "button-primary");
        for (Button button : rest) {
            button.getStyleClass().setAll("button", "button-secondary");
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
        addColumn(revenueTable, "Period", 120, RevenueByPeriod::getPeriod);
        addColumn(revenueTable, "Contracts", 100, row -> String.valueOf(row.getContractCount()));
        addNumericColumn(revenueTable, "Total value", 140,
            row -> Format.paymentAmount(row.getTotalValue()));
        addNumericColumn(revenueTable, "Commission", 140,
            row -> Format.paymentAmount(row.getTotalCommission()));
    }

    private void buildOverdueColumns() {
        addColumn(overdueTable, "Contract", 90, row -> "#" + row.getContractId());
        addColumn(overdueTable, "Property", 220, OverduePayment::getPropertyTitle);
        addColumn(overdueTable, "Client", 160, OverduePayment::getClientName);
        addColumn(overdueTable, "Installment", 100, row -> String.valueOf(row.getInstallmentNo()));
        addColumn(overdueTable, "Due date", 110, row -> Format.date(row.getDueDate()));
        addNumericColumn(overdueTable, "Due", 110, row -> Format.paymentAmount(row.getAmountDue()));
        addNumericColumn(overdueTable, "Paid", 110, row -> Format.paymentAmount(row.getAmountPaid()));
        addNumericColumn(overdueTable, "Days overdue", 110, row -> String.valueOf(row.getDaysOverdue()));
    }

    private void buildMarketColumns() {
        addColumn(marketTable, "Property", 240, TimeOnMarketRow::getTitle);
        addColumn(marketTable, "Published", 130, row -> Format.dateTime(row.getPublishedAt()));
        addColumn(marketTable, "Closed", 130, row -> Format.dateTime(row.getClosedAt()));
        addNumericColumn(marketTable, "Days on market", 120, row -> String.valueOf(row.getDaysOnMarket()));
        addNumericColumn(marketTable, "Asking price", 130, row -> Format.salePrice(row.getAskingPrice()));
        addNumericColumn(marketTable, "Estimate", 130, row -> Format.salePrice(row.getEstimatedValue()));
        // NumberFormat keeps the minus sign for a listing under the estimate,
        // which is the point of the report - premium is a signed distance.
        addNumericColumn(marketTable, "Premium", 100,
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

    // Money and count columns are right-aligned with the "numeric" class
    // from docs/UI-STYLE.md, applied through the cell rather than inline.
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
        Label empty = new Label(message);
        empty.getStyleClass().add("empty-state");
        table.setPlaceholder(empty);
    }
}
