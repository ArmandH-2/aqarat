package co.syntropyhq.aqarat;

import co.syntropyhq.aqarat.dao.UserDao;
import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.District;
import co.syntropyhq.aqarat.util.CaptureGeometry;
import co.syntropyhq.aqarat.util.Panel;
import co.syntropyhq.aqarat.util.Router;
import co.syntropyhq.aqarat.util.SessionManager;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.junit.jupiter.api.Test;

/**
 * Drives the running application through the shots the launch film needs, so a
 * screen recorder has something to record.
 *
 * <p>The alternative was a person at the keyboard, or a robot moving the real
 * mouse. Both were rejected for the same reason: a take has to be repeatable.
 * A film cuts between takes, and re-recording one shot at four in the morning
 * because a hand slipped means re-recording every shot it cuts against. Driving
 * the controls directly means take nine is identical to take one.
 *
 * <p>What it does not do is fake anything. It logs in as a real user, loads the
 * real shell, asks {@link Router} for the real panel, and sets real controls,
 * which fire the same listeners a keystroke fires. Every pixel recorded is the
 * application doing its own work. There is no cursor in these takes because
 * there is no cursor in the story — the film is about what the software shows,
 * not about someone operating it.
 *
 * <p>Named so Surefire's default includes skip it, like {@link PanelShots}.
 * Needs a seeded database, the demonstration accounts from
 * {@code db/demo-accounts.sql}, and a desktop session:
 *
 * <pre>./mvnw test -Dtest=FilmTakes -Daqarat.take=estimate -Daqarat.capture.size=1920x1080</pre>
 *
 * <p>Start the recorder once the window appears. Every take opens with
 * {@link #LEAD_IN} of stillness so the first seconds can be trimmed away
 * without losing anything.
 */
public class FilmTakes {

    /** Held still at the start of every take, to be trimmed off in the edit. */
    private static final Duration LEAD_IN = Duration.seconds(6);

    /** Held at the end, so a cut never lands on the window closing. */
    private static final Duration TAIL = Duration.seconds(3);

    /** Long enough for a panel's background query to come back and paint. */
    private static final Duration SETTLE = Duration.seconds(2.5);

    private static final String CUSTOMER = "customer@syntropyhq.co";
    private static final String ADMIN = "admin@syntropyhq.co";

    private Scene scene;

    @Test
    void recordTake() throws Exception {
        String take = System.getProperty("aqarat.take");
        if (take == null || take.isBlank()) {
            System.out.println("FilmTakes: set -Daqarat.take to record. Skipping.");
            return;
        }

        UserDao users = new UserDao();
        AppUser customer = users.findByEmail(CUSTOMER);
        AppUser admin = users.findByEmail(ADMIN);
        if (customer == null || admin == null) {
            throw new IllegalStateException(
                "FilmTakes needs the demonstration accounts. Run db/demo-accounts.sql first.");
        }

        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        started.await(20, TimeUnit.SECONDS);

        CountDownLatch finished = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                openShell(take.equals("audit") ? admin : customer);
                switch (take) {
                    case "browse" -> browse(finished);
                    case "nav" -> nav(finished);
                    case "estimate" -> estimate(finished);
                    case "audit" -> audit(finished);
                    default -> {
                        System.out.println("FilmTakes: no take named '" + take + "'.");
                        finished.countDown();
                    }
                }
            } catch (Exception e) {
                System.err.println("FilmTakes: take '" + take + "' failed — " + e);
                finished.countDown();
            }
        });

        finished.await(5, TimeUnit.MINUTES);
        Platform.exit();
    }

    /* The real shell, with the real sidebar for this user's role. The title is
       the one gdigrab looks for, and the size is the one the film is cut at. */
    private void openShell(AppUser as) throws Exception {
        loadFonts();
        SessionManager.login(as);
        Parent root = FXMLLoader.load(
            FilmTakes.class.getResource("/fxml/MainShell.fxml"));
        scene = new Scene(root, CaptureGeometry.sceneWidth(), CaptureGeometry.sceneHeight());
        scene.getStylesheets().add(
            FilmTakes.class.getResource("/css/app.css").toExternalForm());
        Stage stage = new Stage();
        stage.setTitle("Aqarat");
        stage.setScene(scene);
        stage.show();
    }

    /* The application bundles its faces and loads them in App.start, which this
       harness does not run. Without this the recording is in a fallback face
       and cuts against motion graphics that are not. */
    private void loadFonts() {
        for (String path : new String[] {
            "/fonts/InstrumentSerif-Regular.ttf", "/fonts/Inter-Regular.ttf",
            "/fonts/Inter-Medium.ttf", "/fonts/Inter-SemiBold.ttf", "/fonts/Inter-Bold.ttf"}) {
            try (var stream = FilmTakes.class.getResourceAsStream(path)) {
                if (stream != null) {
                    Font.loadFont(stream, 12);
                }
            } catch (Exception e) {
                System.err.println("FilmTakes: could not load " + path + " — " + e.getMessage());
            }
        }
    }

    // ---------------------------------------------------------------- takes

    /** The problem beat: the catalogue, scrolling at reading speed. */
    private void browse(CountDownLatch done) {
        after(LEAD_IN, () -> {
            Router.show(Panel.DISCOVER);
            after(SETTLE, () -> scrollTo(0.55, Duration.seconds(16),
                () -> after(TAIL, done::countDown)));
        });
    }

    /**
     * The naming beat: one role moving through the panels it is allowed.
     *
     * <p>Two panels, not three. The contracts panel was the obvious third and it
     * is empty for this account, which is truthful and reads on camera as an
     * unfinished product rather than as a customer who has not signed anything.
     * A film should not have to explain an empty state in four seconds.
     */
    private void nav(CountDownLatch done) {
        after(LEAD_IN, () -> {
            Router.show(Panel.DISCOVER);
            after(Duration.seconds(4), () -> {
                Router.show(Panel.PORTFOLIO);
                after(Duration.seconds(8), () -> after(TAIL, done::countDown));
            });
        });
    }

    /**
     * The demonstration: a specification is described, and an estimate arrives
     * before anyone has named a price.
     *
     * <p>The asking price field is never touched. That is the whole point of the
     * shot — the plausibility pill is {@code setVisible(false)} until a price
     * exists, so this take records an estimate with no verdict attached to it,
     * and the verdict is kept back for the live demonstration.
     */
    private void estimate(CountDownLatch done) {
        after(LEAD_IN, () -> {
            Router.show(Panel.SUBMIT_PROPERTY);
            whenPopulated("#districtCombo", () -> {
                pickDistrict();
                after(Duration.seconds(2.5), () -> {
                    pickFirst("#propertyTypeCombo");
                    after(Duration.seconds(2.5), () -> type("#areaField", AREA_SQM, () ->
                        // The estimate is debounced, then queried on a worker
                        // thread, then faded in over 200 ms. Wait for the fade
                        // rather than for a fixed guess.
                        whenVisible("#estimateDetail", () ->
                            // And then nothing moves.
                            //
                            // The first cut of this shot scrolled the form to
                            // bring the rest of the comparables into view, and
                            // scrolled the estimate panel off the top of the
                            // frame while the narration was describing it. It
                            // also brought the asking price field into shot,
                            // which is the one control this beat exists to
                            // leave alone.
                            //
                            // At rest the panel already shows the figure, the
                            // range, how it was reached, and the first of the
                            // comparables. The reveal is a punch-in done in the
                            // edit, where it cannot drift.
                            after(Duration.seconds(48), () -> after(TAIL, done::countDown)))));
                });
            });
        });
    }

    /** The boundary beat: the ledger, which is the proof of the claim. */
    private void audit(CountDownLatch done) {
        after(LEAD_IN, () -> {
            Router.show(Panel.AUDIT_LOG);
            after(SETTLE, () -> scrollTo(0.45, Duration.seconds(15),
                () -> after(TAIL, done::countDown)));
        });
    }

    // ------------------------------------------------------------- controls

    /*
     * Ras Beirut, and 215 m2, are chosen against the data rather than for the
     * sound of them.
     *
     * Comparables are closed sales in the same district and type within 30% of
     * the subject's area, and the estimator wants five of them. Ras Beirut
     * apartments closed at 114, 147, 206, 226, 234, 249 and 278 m2, so 215
     * brackets [150.5, 279.5] and catches five with margin at both ends.
     * Achrafieh at 185 m2 found exactly one, which put the project's known
     * weakest case on camera and opened a range of plus or minus fifty
     * per cent.
     *
     * Nothing here changes what the estimator does. It chooses which property
     * is described, the way any demonstration chooses its example.
     */
    private static final String DISTRICT = "Ras Beirut";
    private static final String AREA_SQM = "215";

    private void pickDistrict() {
        ComboBox<District> combo = lookup("#districtCombo");
        for (District district : combo.getItems()) {
            // District has no toString override; the combo displays it through a
            // StringConverter. Matching on the object's own name is the only
            // comparison that means anything here.
            if (DISTRICT.equals(district.getName())) {
                combo.setValue(district);
                return;
            }
        }
        throw new IllegalStateException(
            "FilmTakes: no district named '" + DISTRICT + "' in the reference data.");
    }

    private void pickFirst(String id) {
        ComboBox<Object> combo = lookup(id);
        if (!combo.getItems().isEmpty()) {
            combo.setValue(combo.getItems().get(0));
        }
    }

    /* One character at a time, at a speed a person types. Setting the whole
       string at once makes the estimate appear to answer a field that filled
       itself, which reads as a recording of a script rather than of a form. */
    private void type(String id, String text, Runnable then) {
        TextField field = lookup(id);
        Timeline typing = new Timeline();
        for (int i = 0; i < text.length(); i++) {
            String sofar = text.substring(0, i + 1);
            typing.getKeyFrames().add(new KeyFrame(
                Duration.millis(140.0 * (i + 1)), event -> field.setText(sofar)));
        }
        typing.setOnFinished(event -> then.run());
        typing.play();
    }

    /* Eased at both ends. A linear scroll is the other thing that says nobody
       was holding the wheel. */
    private void scrollTo(double vvalue, Duration over, Runnable then) {
        ScrollPane pane = scrollPane();
        if (pane == null) {
            then.run();
            return;
        }
        Timeline scroll = new Timeline(new KeyFrame(over,
            new KeyValue(pane.vvalueProperty(), vvalue, Interpolator.EASE_BOTH)));
        scroll.setOnFinished(event -> then.run());
        scroll.play();
    }

    // -------------------------------------------------------------- waiting

    private void after(Duration delay, Runnable then) {
        PauseTransition pause = new PauseTransition(delay);
        pause.setOnFinished(event -> then.run());
        pause.play();
    }

    /** Polls until a combo has its reference data, which arrives from the database. */
    private void whenPopulated(String id, Runnable then) {
        poll(() -> {
            Node node = scene.lookup(id);
            return node instanceof ComboBox<?> combo && !combo.getItems().isEmpty();
        }, then);
    }

    /** Polls until a node is both present and showing, fade included. */
    private void whenVisible(String id, Runnable then) {
        poll(() -> {
            Node node = scene.lookup(id);
            return node != null && node.isVisible() && node.getOpacity() > 0.99;
        }, then);
    }

    /* Twenty seconds of quarter-second checks. If the condition never holds the
       take carries on regardless, because a recording of the wrong thing is
       easier to diagnose than a harness that hung. */
    private void poll(BooleanSupplier condition, Runnable then) {
        Timeline timer = new Timeline();
        timer.getKeyFrames().add(new KeyFrame(Duration.millis(250), event -> {
            if (condition.getAsBoolean()) {
                timer.stop();
                then.run();
            }
        }));
        timer.setCycleCount(80);
        timer.setOnFinished(event -> then.run());
        timer.play();
    }

    // --------------------------------------------------------------- lookup

    @SuppressWarnings("unchecked")
    private <T> T lookup(String id) {
        Node node = scene.lookup(id);
        if (node == null) {
            throw new IllegalStateException("FilmTakes: no node " + id + " in this panel.");
        }
        return (T) node;
    }

    /* The panel is a ScrollPane at its root, but so is part of the shell around
       it, and walking from the scene root finds the shell's first. The search
       starts inside the content pane so it can only find the panel's own. */
    private ScrollPane scrollPane() {
        Node content = scene.lookup("#contentPane");
        return findScrollPane(content == null ? scene.getRoot() : content);
    }

    private ScrollPane findScrollPane(Node node) {
        if (node instanceof ScrollPane pane) {
            return pane;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                ScrollPane found = findScrollPane(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
