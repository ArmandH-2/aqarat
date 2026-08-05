package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.dao.AuditDao;
import co.syntropyhq.aqarat.dao.AuditSearch;
import co.syntropyhq.aqarat.dao.UserDao;
import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.AuditLog;
import co.syntropyhq.aqarat.model.Role;
import co.syntropyhq.aqarat.service.AuditService;
import co.syntropyhq.aqarat.service.AuthService;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.FieldError;
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.SessionManager;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

// Admin-only (DESIGN.md section 5): filter, page and export the trail every
// write in the system leaves behind (DESIGN.md section 1).
public class AuditLogController {

    private static final int PAGE_SIZE = 20;

    @FXML
    private VBox contentBox;
    @FXML
    private Label accessDeniedLabel;
    @FXML
    private TextField entityTypeField;
    @FXML
    private TextField actionField;
    @FXML
    private TextField userIdField;
    @FXML
    private Label userIdError;
    @FXML
    private DatePicker fromDatePicker;
    @FXML
    private DatePicker toDatePicker;
    @FXML
    private TableView<AuditLog> auditTable;
    @FXML
    private Button previousButton;
    @FXML
    private Button nextButton;

    private final AuditService auditService = new AuditService(new AuditDao());
    private final AuthService authService = new AuthService(new UserDao());
    private final Map<Integer, String> userNames = new HashMap<>();

    private int currentOffset = 0;

    @FXML
    private void initialize() {
        AppUser user = SessionManager.getCurrentUser();
        if (user == null || user.getRole() != Role.ADMIN) {
            denyAccess();
            return;
        }
        buildColumns();
        Label empty = new Label("No audit entries match these filters.");
        empty.getStyleClass().add("empty-state");
        auditTable.setPlaceholder(empty);
        runSearch(0);
    }

    private void denyAccess() {
        contentBox.setVisible(false);
        contentBox.setManaged(false);
        accessDeniedLabel.setText("You do not have access to the audit log.");
        accessDeniedLabel.setVisible(true);
        accessDeniedLabel.setManaged(true);
    }

    @FXML
    private void handleApplyFilters() {
        runSearch(0);
    }

    @FXML
    private void handlePrevious() {
        runSearch(Math.max(0, currentOffset - PAGE_SIZE));
    }

    @FXML
    private void handleNext() {
        runSearch(currentOffset + PAGE_SIZE);
    }

    private void runSearch(int offset) {
        AuditSearch filters = readFilters();
        if (filters == null) {
            return;
        }
        List<AuditLog> results;
        try {
            results = auditService.search(filters, offset, PAGE_SIZE);
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        currentOffset = offset;
        auditTable.setItems(FXCollections.observableArrayList(results));
        previousButton.setDisable(currentOffset == 0);
        nextButton.setDisable(results.size() < PAGE_SIZE);
    }

    // Blank fields mean "no filter" throughout - the same optional-filter
    // idiom PropertySearch uses, just read from text fields instead of
    // combos. A bad user id is the one field that can actually be wrong
    // (not just empty), so it gets the field-level error UI-STYLE.md
    // requires instead of a dialog.
    private AuditSearch readFilters() {
        FieldError.clear(userIdField, userIdError);
        AuditSearch filters = new AuditSearch();
        filters.setEntityType(blankToNull(entityTypeField.getText()));
        filters.setAction(blankToNull(actionField.getText()));
        String userIdText = userIdField.getText();
        if (userIdText != null && !userIdText.isBlank()) {
            try {
                filters.setUserId(Integer.valueOf(userIdText.trim()));
            } catch (NumberFormatException e) {
                FieldError.show(userIdField, userIdError, "Enter a whole number.");
                return null;
            }
        }
        filters.setCreatedFrom(startOfDayUtc(fromDatePicker.getValue()));
        filters.setCreatedTo(endOfDayUtc(toDatePicker.getValue()));
        return filters;
    }

    private String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private LocalDateTime startOfDayUtc(LocalDate date) {
        return date == null ? null : Format.toUtc(date.atStartOfDay());
    }

    private LocalDateTime endOfDayUtc(LocalDate date) {
        return date == null ? null : Format.toUtc(date.atTime(23, 59, 59));
    }

    // Exports every row matching the current filters, not just the page on
    // screen - an admin filtering to one month and exporting should get the
    // whole month, not twenty rows of it.
    @FXML
    private void handleExport() {
        AuditSearch filters = readFilters();
        if (filters == null) {
            return;
        }
        List<AuditLog> rows;
        try {
            int total = auditService.count(filters);
            rows = auditService.search(filters, 0, total);
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        File target = chooseExportFile();
        if (target == null) {
            return;
        }
        writeCsv(target, rows);
    }

    private File chooseExportFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export audit log");
        chooser.setInitialFileName("audit-log.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        Window window = auditTable.getScene().getWindow();
        return chooser.showSaveDialog(window);
    }

    private void writeCsv(File target, List<AuditLog> rows) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(target))) {
            writer.println("When,Who,Entity type,Entity id,Action,Old value,New value");
            for (AuditLog row : rows) {
                writer.println(csvRow(row));
            }
        } catch (IOException e) {
            AlertUtil.showError("Could not write the export file. Check the location and try again.");
            return;
        }
        AlertUtil.showInfo("Exported " + rows.size() + " audit entries to " + target.getName() + ".");
    }

    private String csvRow(AuditLog row) {
        return String.join(",",
            csvField(Format.dateTime(row.getCreatedAt())),
            csvField(userName(row.getUserId())),
            csvField(row.getEntityType()),
            csvField(row.getEntityId() == null ? "" : row.getEntityId().toString()),
            csvField(row.getAction()),
            csvField(row.getOldValue()),
            csvField(row.getNewValue()));
    }

    // RFC 4180: a field containing a comma, a quote or a line break is
    // wrapped in quotes, and any quote inside it is doubled. Without this an
    // old/new value that happens to contain a comma would split into the
    // wrong number of columns and corrupt the row after it.
    private String csvField(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuoting =
            value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        if (!needsQuoting) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String userName(Integer userId) {
        if (userId == null) {
            return "System";
        }
        return userNames.computeIfAbsent(userId, id -> {
            try {
                AppUser found = authService.findById(id);
                return found == null ? "Unknown user" : found.getFullName();
            } catch (SQLException e) {
                return "Unknown user";
            }
        });
    }

    private void buildColumns() {
        addColumn(auditTable, "When", 150, row -> Format.dateTime(row.getCreatedAt()));
        addColumn(auditTable, "Who", 140, row -> userName(row.getUserId()));
        addColumn(auditTable, "Entity", 160,
            row -> row.getEntityType() + (row.getEntityId() == null ? "" : " #" + row.getEntityId()));
        addColumn(auditTable, "Action", 140, AuditLog::getAction);
        addColumn(auditTable, "Old value", 200, row -> valueOrDash(row.getOldValue()));
        addColumn(auditTable, "New value", 200, row -> valueOrDash(row.getNewValue()));
    }

    private String valueOrDash(String value) {
        return value == null ? "-" : value;
    }

    private void addColumn(TableView<AuditLog> table, String header, double width,
            Function<AuditLog, String> textFor) {
        TableColumn<AuditLog, String> column = new TableColumn<>(header);
        column.setPrefWidth(width);
        column.setCellValueFactory(data -> new SimpleStringProperty(textFor.apply(data.getValue())));
        table.getColumns().add(column);
    }
}
