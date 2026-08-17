package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.Role;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.Panel;
import co.syntropyhq.aqarat.util.Router;
import co.syntropyhq.aqarat.util.SessionManager;
import co.syntropyhq.aqarat.util.UIHelper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

    private static final NavEntry[] NAV_ENTRIES = {
        new NavEntry(Panel.BROWSE_LISTINGS, "Browse listings", "DISCOVER", Role.CUSTOMER),
        new NavEntry(Panel.MY_PROPERTIES, "My properties", "PORTFOLIO", Role.CUSTOMER),
        new NavEntry(Panel.SUBMIT_PROPERTY, "Submit a property", "PORTFOLIO", Role.CUSTOMER),
        new NavEntry(Panel.MY_CONTRACTS, "My contracts", "PORTFOLIO", Role.CUSTOMER),
        new NavEntry(Panel.MY_ACTIVITY, "My activity", "PORTFOLIO", Role.CUSTOMER),
        new NavEntry(Panel.AGENT_DASHBOARD, "Dashboard", "AGENT OPERATIONS", Role.AGENT, Role.ADMIN),
        new NavEntry(Panel.REVIEW_QUEUE, "Review queue", "AGENT OPERATIONS", Role.AGENT, Role.ADMIN),
        new NavEntry(Panel.LISTINGS, "Listings", "AGENT OPERATIONS", Role.AGENT, Role.ADMIN),
        new NavEntry(Panel.VIEWINGS, "Viewings", "AGENT OPERATIONS", Role.AGENT, Role.ADMIN),
        new NavEntry(Panel.CONTRACTS, "Contracts", "AGENT OPERATIONS", Role.AGENT, Role.ADMIN),
        new NavEntry(Panel.PAYMENTS, "Payments", "AGENT OPERATIONS", Role.AGENT, Role.ADMIN),
        new NavEntry(Panel.USERS, "Users", "ADMINISTRATION", Role.ADMIN),
        new NavEntry(Panel.REFERENCE, "Reference data", "ADMINISTRATION", Role.ADMIN),
        new NavEntry(Panel.AUDIT_LOG, "Audit log", "ADMINISTRATION", Role.ADMIN),
        new NavEntry(Panel.REPORTS, "Reports", "ADMINISTRATION", Role.ADMIN),
    };

    private static final NavEntry GUEST_ENTRY =
        new NavEntry(Panel.BROWSE_LISTINGS, "Browse listings", "DISCOVER");

    @FXML
    private VBox navItems;
    @FXML
    private StackPane contentPane;
    @FXML
    private StackPane avatarContainer;
    @FXML
    private Label userNameLabel;
    @FXML
    private Label userRoleLabel;
    @FXML
    private Button signOutButton;

    private final List<Label> navLabels = new ArrayList<>();

    @FXML
    private void initialize() {
        Router.setContentPane(contentPane);

        AppUser user = SessionManager.getCurrentUser();
        if (user == null) {
            userNameLabel.setText("Guest User");
            userRoleLabel.setText("Browsing Catalog");
            signOutButton.setText("Sign in");
            avatarContainer.getChildren().setAll(UIHelper.createAvatar("Guest", 16));
            addNavItem(GUEST_ENTRY);
            return;
        }

        userNameLabel.setText(user.getFullName());
        userRoleLabel.setText(Format.enumLabel(user.getRole()));
        avatarContainer.getChildren().setAll(UIHelper.createAvatar(user.getFullName(), 16));

        String currentCategory = null;
        for (NavEntry entry : NAV_ENTRIES) {
            if (entry.appliesTo(user.getRole())) {
                if (currentCategory == null || !currentCategory.equals(entry.category)) {
                    currentCategory = entry.category;
                    Label catLabel = new Label(currentCategory);
                    catLabel.getStyleClass().add("sidebar-category");
                    navItems.getChildren().add(catLabel);
                }
                addNavItem(entry);
            }
        }
    }

    private void addNavItem(NavEntry entry) {
        Label label = new Label(entry.text);
        label.getStyleClass().add("nav-item");
        label.setMaxWidth(Double.MAX_VALUE);

        boolean fxmlExists = getClass().getResource("/fxml/" + entry.panel.getFxml()) != null;
        label.setDisable(!fxmlExists);

        label.setOnMouseClicked(event -> {
            setActiveNav(label);
            Router.show(entry.panel);
        });

        navLabels.add(label);
        navItems.getChildren().add(label);
    }

    private void setActiveNav(Label activeLabel) {
        for (Label l : navLabels) {
            l.getStyleClass().remove("active");
        }
        if (!activeLabel.getStyleClass().contains("active")) {
            activeLabel.getStyleClass().add("active");
        }
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
        private final String category;
        private final Role[] roles;

        private NavEntry(Panel panel, String text, String category, Role... roles) {
            this.panel = panel;
            this.text = text;
            this.category = category;
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
