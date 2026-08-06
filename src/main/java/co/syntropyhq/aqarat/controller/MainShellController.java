package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.Role;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.Panel;
import co.syntropyhq.aqarat.util.Router;
import co.syntropyhq.aqarat.util.SessionManager;
import java.io.IOException;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class MainShellController {

    // Every sidebar item and which roles see it. Order here is the order on screen.
    private static final NavEntry[] NAV_ENTRIES = {
        new NavEntry(Panel.BROWSE_LISTINGS, "Browse listings", Role.CUSTOMER),
        new NavEntry(Panel.MY_PROPERTIES, "My properties", Role.CUSTOMER),
        new NavEntry(Panel.SUBMIT_PROPERTY, "Submit a property", Role.CUSTOMER),
        new NavEntry(Panel.MY_CONTRACTS, "My contracts", Role.CUSTOMER),
        new NavEntry(Panel.MY_ACTIVITY, "My activity", Role.CUSTOMER),
        new NavEntry(Panel.AGENT_DASHBOARD, "Dashboard", Role.AGENT, Role.ADMIN),
        new NavEntry(Panel.REVIEW_QUEUE, "Review queue", Role.AGENT, Role.ADMIN),
        new NavEntry(Panel.LISTINGS, "Listings", Role.AGENT, Role.ADMIN),
        new NavEntry(Panel.VIEWINGS, "Viewings", Role.AGENT, Role.ADMIN),
        new NavEntry(Panel.CONTRACTS, "Contracts", Role.AGENT, Role.ADMIN),
        new NavEntry(Panel.PAYMENTS, "Payments", Role.AGENT, Role.ADMIN),
        new NavEntry(Panel.USERS, "Users", Role.ADMIN),
        new NavEntry(Panel.REFERENCE, "Reference data", Role.ADMIN),
        new NavEntry(Panel.AUDIT_LOG, "Audit log", Role.ADMIN),
        new NavEntry(Panel.REPORTS, "Reports", Role.ADMIN),
    };

    private static final NavEntry GUEST_ENTRY =
        new NavEntry(Panel.BROWSE_LISTINGS, "Browse listings");

    @FXML
    private VBox navItems;
    @FXML
    private StackPane contentPane;
    @FXML
    private Label userNameLabel;
    @FXML
    private Label userRoleLabel;
    @FXML
    private Button signOutButton;

    @FXML
    private void initialize() {
        Router.setContentPane(contentPane);

        // A guest reaches the shell without signing in, so there is no user to
        // name and no role to match nav items against - they see the published
        // listings and nothing else (DESIGN.md section 4).
        AppUser user = SessionManager.getCurrentUser();
        if (user == null) {
            userNameLabel.setText("Guest");
            userRoleLabel.setText("Not signed in");
            signOutButton.setText("Sign in");
            navItems.getChildren().add(buildNavLabel(GUEST_ENTRY));
            return;
        }

        userNameLabel.setText(user.getFullName());
        userRoleLabel.setText(Format.enumLabel(user.getRole()));

        for (NavEntry entry : NAV_ENTRIES) {
            if (entry.appliesTo(user.getRole())) {
                navItems.getChildren().add(buildNavLabel(entry));
            }
        }
    }

    private Label buildNavLabel(NavEntry entry) {
        Label label = new Label(entry.text);
        label.getStyleClass().add("nav-item");
        label.setMaxWidth(Double.MAX_VALUE);

        // A missing FXML file disables the item rather than crashing on click.
        boolean fxmlExists = getClass().getResource("/fxml/" + entry.panel.getFxml()) != null;
        label.setDisable(!fxmlExists);
        label.setOnMouseClicked(event -> Router.show(entry.panel));
        return label;
    }

    @FXML
    private void handleSignOut() {
        SessionManager.logout();
        openLoginWindow();
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
            AlertUtil.showError("Could not open the sign-in window.");
            return;
        }
        ((Stage) userNameLabel.getScene().getWindow()).close();
    }

    private static final class NavEntry {
        private final Panel panel;
        private final String text;
        private final Role[] roles;

        // A guest matches no role, so its one entry is built with none.
        private NavEntry(Panel panel, String text, Role... roles) {
            this.panel = panel;
            this.text = text;
            this.roles = roles;
        }

        private boolean appliesTo(Role role) {
            for (Role candidate : roles) {
                if (candidate == role) {
                    return true;
                }
            }
            return false;
        }
    }
}
