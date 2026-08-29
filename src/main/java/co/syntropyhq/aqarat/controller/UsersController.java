package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.dao.AuditDao;
import co.syntropyhq.aqarat.dao.UserDao;
import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.Role;
import co.syntropyhq.aqarat.model.UserStatus;
import co.syntropyhq.aqarat.service.AuditService;
import co.syntropyhq.aqarat.service.AuthService;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.AnimationUtil;
import co.syntropyhq.aqarat.util.FieldError;
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.SessionManager;
import co.syntropyhq.aqarat.util.UIHelper;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

public class UsersController {

    @FXML
    private VBox contentBox;
    @FXML
    private Label accessDeniedLabel;
    @FXML
    private TextField fullNameField;
    @FXML
    private Label fullNameError;
    @FXML
    private TextField emailField;
    @FXML
    private Label emailError;
    @FXML
    private TextField phoneField;
    @FXML
    private Label phoneError;
    @FXML
    private ComboBox<Role> roleCombo;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label passwordError;
    @FXML
    private ListView<AppUser> userList;

    private final AuthService authService =
        new AuthService(new UserDao(), new AuditService(new AuditDao()));

    @FXML
    private void initialize() {
        AppUser currentUser = SessionManager.getCurrentUser();
        if (currentUser == null || currentUser.getRole() != Role.ADMIN) {
            denyAccess();
            return;
        }
        setRoleConverter(roleCombo);
        roleCombo.setItems(FXCollections.observableArrayList(Role.values()));
        roleCombo.getSelectionModel().select(Role.AGENT);

        userList.setPlaceholder(UIHelper.createEmptyState("No User Accounts Found", "Provision a new account using the form above."));
        userList.setCellFactory(list -> new UserCard());
        loadUsers();
    }

    private void denyAccess() {
        contentBox.setVisible(false);
        contentBox.setManaged(false);
        accessDeniedLabel.setText("You do not have access to user accounts.");
        accessDeniedLabel.setVisible(true);
        accessDeniedLabel.setManaged(true);
    }

    private void loadUsers() {
        try {
            List<AppUser> users = authService.findAll();
            userList.setItems(FXCollections.observableArrayList(users));
        } catch (SQLException e) {
            AlertUtil.showError("Could not load accounts. Check that SQL Server is running.");
        }
    }

    @FXML
    private void handleCreate() {
        clearFormErrors();
        String fullName = fullNameField.getText().trim();
        String email = emailField.getText().trim();
        String phone = phoneField.getText().trim();
        String password = passwordField.getText();
        if (!fieldsAreValid(fullName, email, phone, password)) {
            return;
        }
        AppUser created;
        try {
            created =
                authService.createAccount(email, password, fullName, phone, roleCombo.getValue());
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        if (created == null) {
            FieldError.show(emailField, emailError, "This email is already registered.");
            return;
        }
        AlertUtil.showInfo("Account successfully provisioned.");
        clearForm();
        loadUsers();
    }

    private boolean fieldsAreValid(String fullName, String email, String phone, String password) {
        boolean valid = true;
        if (fullName.isEmpty()) {
            FieldError.show(fullNameField, fullNameError, "Enter a full name.");
            valid = false;
        }
        if (email.isEmpty()) {
            FieldError.show(emailField, emailError, "Enter an email address.");
            valid = false;
        }
        if (phone.isEmpty()) {
            FieldError.show(phoneField, phoneError, "Enter a phone number.");
            valid = false;
        }
        if (password.isEmpty()) {
            FieldError.show(passwordField, passwordError, "Enter an initial password.");
            valid = false;
        }
        return valid;
    }

    private void clearFormErrors() {
        FieldError.clear(fullNameField, fullNameError);
        FieldError.clear(emailField, emailError);
        FieldError.clear(phoneField, phoneError);
        FieldError.clear(passwordField, passwordError);
    }

    private void clearForm() {
        fullNameField.clear();
        emailField.clear();
        phoneField.clear();
        passwordField.clear();
        roleCombo.getSelectionModel().select(Role.AGENT);
    }

    private void handleRoleChange(AppUser user, Role newRole) {
        if (newRole == null || newRole == user.getRole()) {
            return;
        }
        try {
            authService.changeRole(user.getId(), newRole);
        } catch (AuthService.LastAdminException e) {
            AlertUtil.showError(e.getMessage());
            loadUsers();
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("Account role updated.");
        loadUsers();
    }

    private void handleToggleStatus(AppUser user) {
        UserStatus target =
            user.getStatus() == UserStatus.ACTIVE ? UserStatus.INACTIVE : UserStatus.ACTIVE;
        String verb = target == UserStatus.INACTIVE ? "deactivate" : "reactivate";
        if (!AlertUtil.confirm("Do you want to " + verb + " " + user.getFullName() + "?")) {
            return;
        }
        try {
            authService.setStatus(user.getId(), target);
        } catch (AuthService.LastAdminException e) {
            AlertUtil.showError(e.getMessage());
            return;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        loadUsers();
    }

    private void handleResetPassword(AppUser user) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setHeaderText(null);
        dialog.setTitle("Reset User Password");
        dialog.setContentText("Enter new temporary password for " + user.getFullName() + ":");
        Optional<String> input = dialog.showAndWait();
        if (input.isEmpty()) {
            return;
        }
        String newPassword = input.get().trim();
        if (newPassword.isEmpty()) {
            AlertUtil.showError("Enter a valid password.");
            return;
        }
        try {
            authService.resetPassword(user.getId(), newPassword);
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return;
        }
        AlertUtil.showInfo("User password has been successfully reset.");
    }

    private String statusPillClass(UserStatus status) {
        return status == UserStatus.ACTIVE ? "pill-good" : "pill-neutral";
    }

    private void setRoleConverter(ComboBox<Role> combo) {
        combo.setConverter(new StringConverter<Role>() {
            @Override
            public String toString(Role role) {
                return role == null ? "" : Format.enumLabel(role);
            }

            @Override
            public Role fromString(String text) {
                return null;
            }
        });
    }

    private final class UserCard extends ListCell<AppUser> {

        @Override
        protected void updateItem(AppUser user, boolean empty) {
            super.updateItem(user, empty);
            setText(null);
            setGraphic(empty || user == null ? null : buildCard(user));
        }

        private VBox buildCard(AppUser user) {
            VBox card = new VBox(10);
            card.getStyleClass().addAll("card", "card-hoverable");
            card.setPadding(new Insets(16));

            HBox header = new HBox(12);
            header.setAlignment(Pos.CENTER_LEFT);

            javafx.scene.Node avatar = UIHelper.createAvatar(user.getFullName(), 36);

            VBox info = new VBox(2);
            Label name = new Label(user.getFullName());
            name.getStyleClass().add("section-title");

            Label meta = new Label(user.getEmail() + " · " + (user.getPhone() == null ? "No phone" : user.getPhone()));
            meta.getStyleClass().add("hint");
            info.getChildren().addAll(name, meta);
            HBox.setHgrow(info, Priority.ALWAYS);

            Label statusPill = UIHelper.createPill(Format.enumLabel(user.getStatus()), statusPillClass(user.getStatus()));
            header.getChildren().addAll(avatar, info, statusPill);

            HBox actions = buildActions(user);

            card.getChildren().addAll(header, actions);
            AnimationUtil.addHoverLift(card);
            return card;
        }

        private HBox buildActions(AppUser user) {
            HBox actions = new HBox(10);
            actions.setAlignment(Pos.CENTER_LEFT);
            actions.setPadding(new Insets(4, 0, 0, 48));

            Label roleLabel = new Label("Role:");
            roleLabel.getStyleClass().add("label-soft");

            ComboBox<Role> roleField =
                new ComboBox<>(FXCollections.observableArrayList(Role.values()));
            setRoleConverter(roleField);
            roleField.getSelectionModel().select(user.getRole());
            roleField.setOnAction(event -> handleRoleChange(user, roleField.getValue()));

            Button toggle =
                new Button(user.getStatus() == UserStatus.ACTIVE ? "Deactivate" : "Reactivate");
            toggle.getStyleClass().addAll("button",
                user.getStatus() == UserStatus.ACTIVE ? "button-danger" : "button-secondary");
            toggle.setOnAction(event -> handleToggleStatus(user));

            Button resetPassword = new Button("Reset password");
            resetPassword.getStyleClass().addAll("button", "button-secondary");
            resetPassword.setOnAction(event -> handleResetPassword(user));

            actions.getChildren().addAll(roleLabel, roleField, toggle, resetPassword);
            return actions;
        }
    }
}
