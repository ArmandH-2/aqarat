package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.dao.UserDao;
import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.service.AuthService;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.SessionManager;
import java.io.IOException;
import java.sql.SQLException;
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

    private final AuthService authService = new AuthService(new UserDao());

    @FXML
    private void handleSignIn() {
        clearErrors();
        String email = emailField.getText().trim();
        String password = passwordField.getText();

        boolean valid = true;
        if (email.isEmpty()) {
            showError(emailError, "Enter your email.");
            valid = false;
        }
        if (password.isEmpty()) {
            showError(passwordError, "Enter your password.");
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
            showError(formError, "Incorrect email or password.");
            return;
        }

        SessionManager.login(user);
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
            stage.show();
            return true;
        } catch (IOException e) {
            AlertUtil.showError("Could not open the window.");
            return false;
        }
    }

    private void closeThisWindow() {
        ((Stage) emailField.getScene().getWindow()).close();
    }

    private void showError(Label label, String message) {
        label.setText(message);
        label.setVisible(true);
        label.setManaged(true);
    }

    private void clearErrors() {
        hideError(emailError);
        hideError(passwordError);
        hideError(formError);
    }

    private void hideError(Label label) {
        label.setVisible(false);
        label.setManaged(false);
    }
}
