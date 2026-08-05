package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.dao.UserDao;
import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.service.AuthService;
import co.syntropyhq.aqarat.util.AlertUtil;
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

public class RegisterController {

    @FXML
    private TextField fullNameField;
    @FXML
    private TextField emailField;
    @FXML
    private TextField phoneField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private Label fullNameError;
    @FXML
    private Label emailError;
    @FXML
    private Label phoneError;
    @FXML
    private Label passwordError;
    @FXML
    private Label confirmPasswordError;

    private final AuthService authService = new AuthService(new UserDao());

    @FXML
    private void handleRegister() {
        clearErrors();
        String fullName = fullNameField.getText().trim();
        String email = emailField.getText().trim();
        String phone = phoneField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        if (!fieldsAreValid(fullName, email, phone, password, confirmPassword)) {
            return;
        }

        AppUser user;
        try {
            user = authService.register(email, password, fullName, phone);
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Check that SQL Server is running.");
            return;
        }

        if (user == null) {
            showError(emailError, "This email is already registered.");
            return;
        }

        AlertUtil.showInfo("Account created. Sign in to continue.");
        openLoginWindow();
    }

    @FXML
    private void handleLoginLink() {
        openLoginWindow();
    }

    private boolean fieldsAreValid(String fullName, String email, String phone,
            String password, String confirmPassword) {
        boolean valid = true;
        if (fullName.isEmpty()) {
            showError(fullNameError, "Enter your full name.");
            valid = false;
        }
        if (email.isEmpty()) {
            showError(emailError, "Enter your email.");
            valid = false;
        }
        if (phone.isEmpty()) {
            showError(phoneError, "Enter your phone number.");
            valid = false;
        }
        if (password.isEmpty()) {
            showError(passwordError, "Enter a password.");
            valid = false;
        } else if (!password.equals(confirmPassword)) {
            showError(confirmPasswordError, "Passwords do not match.");
            valid = false;
        }
        return valid;
    }

    private void openLoginWindow() {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/Login.fxml"));
            Stage stage = new Stage();
            Scene scene = new Scene(root, 1280, 800);
            scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
            stage.setTitle("Aqarat");
            stage.setMinWidth(1100);
            stage.setMinHeight(700);
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            AlertUtil.showError("Could not open the window.");
            return;
        }
        ((Stage) fullNameField.getScene().getWindow()).close();
    }

    private void showError(Label label, String message) {
        label.setText(message);
        label.setVisible(true);
        label.setManaged(true);
    }

    private void clearErrors() {
        hideError(fullNameError);
        hideError(emailError);
        hideError(phoneError);
        hideError(passwordError);
        hideError(confirmPasswordError);
    }

    private void hideError(Label label) {
        label.setVisible(false);
        label.setManaged(false);
    }
}
