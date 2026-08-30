package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.dao.AuditDao;
import co.syntropyhq.aqarat.dao.DistrictDao;
import co.syntropyhq.aqarat.dao.PropertyDao;
import co.syntropyhq.aqarat.dao.PropertyMessageDao;
import co.syntropyhq.aqarat.dao.PropertyPhotoDao;
import co.syntropyhq.aqarat.dao.PropertySearch;
import co.syntropyhq.aqarat.dao.PropertyTypeDao;
import co.syntropyhq.aqarat.dao.UserDao;
import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.PropertyStatus;
import co.syntropyhq.aqarat.service.AuditService;
import co.syntropyhq.aqarat.service.AuthService;
import co.syntropyhq.aqarat.service.PropertyService;
import co.syntropyhq.aqarat.service.ReferenceService;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.AppIcons;
import co.syntropyhq.aqarat.util.FieldError;
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.SceneCapture;
import co.syntropyhq.aqarat.util.SessionManager;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class LoginController {

    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label emailError;
    @FXML
    private Label passwordError;
    @FXML
    private Label formError;
    @FXML
    private Label listingCountLabel;
    @FXML
    private Label districtCountLabel;

    private final AuthService authService = new AuthService(new UserDao());
    private final PropertyService propertyService = new PropertyService(
        new PropertyDao(), new PropertyPhotoDao(), new PropertyMessageDao(),
        new AuditService(new AuditDao()));
    private final ReferenceService referenceService =
        new ReferenceService(new DistrictDao(), new PropertyTypeDao());

    @FXML
    private void initialize() {
        loadCatalogueSize();
    }

    /* Decorative counts. They run off the UI thread so a slow or absent
       database never delays the sign-in form, and a failure leaves the
       placeholder dashes rather than interrupting someone trying to log in. */
    private void loadCatalogueSize() {
        Task<int[]> task = new Task<>() {
            @Override
            protected int[] call() throws SQLException {
                int listings = propertyService.count(
                    List.of(PropertyStatus.AVAILABLE), new PropertySearch());
                int districts = referenceService.findAllDistricts().size();
                return new int[] {listings, districts};
            }
        };
        task.setOnSucceeded(event -> {
            int[] counts = task.getValue();
            listingCountLabel.setText(Format.count(counts[0]));
            districtCountLabel.setText(Format.count(counts[1]));
        });
        Thread worker = new Thread(task, "login-catalogue-size");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void handleSignIn() {
        clearErrors();
        String email = emailField.getText().trim();
        String password = passwordField.getText();

        boolean valid = true;
        if (email.isEmpty()) {
            FieldError.show(emailField, emailError, "Enter your email.");
            valid = false;
        }
        if (password.isEmpty()) {
            FieldError.show(passwordField, passwordError, "Enter your password.");
            valid = false;
        }
        if (!valid) {
            return;
        }

        AppUser user;
        try {
            user = authService.login(email, password);
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Check that SQL Server is running.");
            return;
        }

        if (user == null) {
            // formError belongs to the whole form, not one field - there is no
            // control to redden, so it is shown directly rather than through FieldError.
            formError.setText("Incorrect email or password.");
            formError.setVisible(true);
            formError.setManaged(true);
            return;
        }

        SessionManager.login(user);
        if (openWindow("/fxml/MainShell.fxml", "Aqarat")) {
            closeThisWindow();
        }
    }

    // A guest browses published listings without an account, so the shell
    // opens with nobody signed in (DESIGN.md section 4).
    @FXML
    private void handleBrowseAsGuest() {
        SessionManager.logout();
        if (openWindow("/fxml/MainShell.fxml", "Aqarat")) {
            closeThisWindow();
        }
    }

    @FXML
    private void handleRegisterLink() {
        if (openWindow("/fxml/Register.fxml", "Aqarat - Register")) {
            closeThisWindow();
        }
    }

    // Returns false when the next window did not open, so this one stays on
    // screen rather than leaving the user with no window at all.
    private boolean openWindow(String fxmlPath, String title) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));
            Stage stage = new Stage();
            Scene scene = new Scene(root, 1280, 800);
            scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
            stage.setTitle(title);
            stage.setMinWidth(1100);
            stage.setMinHeight(700);
            stage.setScene(scene);
            AppIcons.apply(stage);
            SceneCapture.install(scene);
            stage.show();
            return true;
        } catch (IOException e) {
            AlertUtil.showError("Aqarat could not open",
                "The application started but could not build its main window. This is "
                    + "usually the database being unreachable - check that SQL Server is "
                    + "running, then sign in again.");
            return false;
        }
    }

    private void closeThisWindow() {
        ((Stage) emailField.getScene().getWindow()).close();
    }

    private void clearErrors() {
        FieldError.clear(emailField, emailError);
        FieldError.clear(passwordField, passwordError);
        formError.setVisible(false);
        formError.setManaged(false);
    }
}
