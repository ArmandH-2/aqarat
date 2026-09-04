package co.syntropyhq.aqarat;

import co.syntropyhq.aqarat.dao.AuditDao;
import co.syntropyhq.aqarat.dao.PropertyDao;
import co.syntropyhq.aqarat.dao.PropertyDocumentDao;
import co.syntropyhq.aqarat.dao.UserDao;
import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.DocumentType;
import co.syntropyhq.aqarat.model.NewDocument;
import co.syntropyhq.aqarat.model.PropertyDocument;
import co.syntropyhq.aqarat.service.AuditService;
import co.syntropyhq.aqarat.service.DocumentService;
import co.syntropyhq.aqarat.util.Panel;
import co.syntropyhq.aqarat.util.Router;
import co.syntropyhq.aqarat.util.SceneCapture;
import co.syntropyhq.aqarat.util.SessionManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

/**
 * Renders panels to PNG so a person can look at them.
 *
 * <p>Windows cannot photograph a JavaFX window from outside — the scene is
 * composited on the GPU and an external capture comes back blank, which is why
 * {@link SceneCapture} exists at all. This drives the real {@link Router} at
 * the real panels against the real database and asks each scene to draw
 * itself, which is the only way to see a screen without a person at the
 * keyboard.
 *
 * <p>Named so Surefire's default includes skip it, in the same way as
 * {@code PerformanceProbe}. It needs a seeded database and a desktop session:
 *
 * <pre>./mvnw test -Dtest=PanelShots -Daqarat.shots=C:/tmp/shots</pre>
 */
public class PanelShots {

    private static final String DIR_PROPERTY = "aqarat.shots";

    /** A property carrying a contract and a history, for the dossier. */
    private static final int RICH_PROPERTY = 8;

    /** A submission still in review, for the reviewer's screen. */
    private static final int PENDING_PROPERTY = 5;

    @Test
    void renderPanels() throws Exception {
        String directory = System.getProperty(DIR_PROPERTY);
        if (directory == null || directory.isBlank()) {
            System.out.println("PanelShots: set -D" + DIR_PROPERTY + " to render. Skipping.");
            return;
        }
        System.setProperty("aqarat.capture.dir", directory);

        UserDao userDao = new UserDao();
        AppUser agent = userDao.findByEmail("rami@aqarat.local");
        AppUser owner = userDao.findById(ownerOf(PENDING_PROPERTY));
        attachEvidence(agent);

        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        started.await(20, TimeUnit.SECONDS);

        shoot("dossier-top", agent, Panel.PROPERTY_DOSSIER, RICH_PROPERTY, 0, null);
        shoot("dossier-activity", agent, Panel.PROPERTY_DOSSIER, RICH_PROPERTY, 1, null);
        shoot("submit-evidence", owner, Panel.SUBMIT_PROPERTY, null, 0.62, null);
        shoot("my-properties", owner, Panel.MY_PROPERTIES, null, 0, null);
        // The signature moment: the agent presses publish with nothing
        // verified, and the refusal offers both roads out.
        shoot("publish-refused", agent, Panel.REVIEW_SUBMISSION, PENDING_PROPERTY, 0,
            "Approve and publish");

        Platform.exit();
    }

    /*
     * One document on the dossier's property, verified, so the evidence
     * section shows what it looks like when it has something in it. Idempotent:
     * a second run finds the document already there and leaves it alone.
     */
    private void attachEvidence(AppUser agent) throws Exception {
        DocumentService documents = new DocumentService(
            new PropertyDocumentDao(), new PropertyDao(), new AuditService(new AuditDao()));
        SessionManager.login(agent);
        List<PropertyDocument> existing = documents.findForProperty(RICH_PROPERTY, agent);
        if (existing.isEmpty()) {
            Path deed = Files.createTempFile("aqarat-deed", ".pdf");
            Files.write(deed, "%PDF-1.4 sample title deed".getBytes(StandardCharsets.US_ASCII));
            documents.upload(RICH_PROPERTY,
                List.of(new NewDocument(deed, DocumentType.TITLE_DEED)), agent);
            existing = documents.findForProperty(RICH_PROPERTY, agent);
        }
        if (!existing.get(0).isVerified()) {
            documents.verify(existing.get(0).getId(), agent);
        }
    }

    private int ownerOf(int propertyId) throws Exception {
        return new PropertyDao().findById(propertyId).getOwnerId();
    }

    private void shoot(String name, AppUser as, Panel panel, Integer id, double scrollTo,
            String pressButtonLabelled) throws Exception {
        CountDownLatch painted = new CountDownLatch(1);
        Platform.runLater(() -> {
            SessionManager.login(as);
            StackPane content = new StackPane();
            content.setPrefSize(1440, 980);
            Scene scene = new Scene(content, 1440, 980);
            scene.getStylesheets().add(
                PanelShots.class.getResource("/css/app.css").toExternalForm());
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.show();
            Router.setContentPane(content);
            if (id == null) {
                Router.show(panel);
            } else {
                Router.show(panel, id.intValue());
            }
            // The dossier loads on a background thread and the panels animate
            // in, so the shutter waits for both rather than photographing a
            // spinner.
            javafx.animation.PauseTransition settle =
                new javafx.animation.PauseTransition(javafx.util.Duration.seconds(3));
            settle.setOnFinished(event -> {
                scrollTo(scene, scrollTo);
                javafx.animation.PauseTransition afterScroll =
                    new javafx.animation.PauseTransition(javafx.util.Duration.millis(700));
                afterScroll.setOnFinished(done -> {
                    if (pressButtonLabelled == null) {
                        SceneCapture.capture(scene, name);
                        stage.close();
                        painted.countDown();
                        return;
                    }
                    // A modal dialog runs a nested event loop, so the shutter is
                    // armed before the button is pressed rather than after -
                    // the line after fire() does not run until the dialog closes.
                    armDialogShot(name, stage, painted);
                    press(scene, pressButtonLabelled);
                });
                afterScroll.play();
            });
            settle.play();
        });
        painted.await(60, TimeUnit.SECONDS);
    }

    /* Captures whatever window is on top once the dialog has painted, then
       dismisses it so the harness can carry on. */
    private void armDialogShot(String name, Stage host, CountDownLatch painted) {
        javafx.animation.PauseTransition shutter =
            new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
        shutter.setOnFinished(event -> {
            for (javafx.stage.Window window : javafx.stage.Window.getWindows()) {
                if (window != host && window.isShowing() && window.getScene() != null) {
                    SceneCapture.capture(window.getScene(), name);
                    ((Stage) window).close();
                }
            }
            host.close();
            painted.countDown();
        });
        shutter.play();
    }

    private void press(javafx.scene.Parent root, String label) {
        javafx.scene.control.Button button = findButton(root, label);
        if (button == null) {
            System.out.println("PanelShots: no button labelled '" + label + "'");
            return;
        }
        button.fire();
    }

    private void press(Scene scene, String label) {
        press(scene.getRoot(), label);
    }

    private javafx.scene.control.Button findButton(javafx.scene.Node node, String label) {
        if (node instanceof javafx.scene.control.Button button
                && label.equals(button.getText())) {
            return button;
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                javafx.scene.control.Button found = findButton(child, label);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // Panels are a ScrollPane at the root; a fraction of 0 leaves it at the top.
    private void scrollTo(Scene scene, double fraction) {
        if (fraction <= 0) {
            return;
        }
        javafx.scene.control.ScrollPane pane = findScrollPane(scene.getRoot());
        if (pane != null) {
            pane.setVvalue(fraction);
        }
    }

    private javafx.scene.control.ScrollPane findScrollPane(javafx.scene.Node node) {
        if (node instanceof javafx.scene.control.ScrollPane pane) {
            return pane;
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                javafx.scene.control.ScrollPane found = findScrollPane(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
