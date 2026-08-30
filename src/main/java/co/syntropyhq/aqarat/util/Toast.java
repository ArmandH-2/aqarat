package co.syntropyhq.aqarat.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.PopupWindow;
import javafx.stage.Window;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Reports the outcome of an action without stopping the person doing it.
 *
 * <p>The application used to open a modal window to say "saved". Someone who has
 * just watched themselves press Save learns nothing from being asked to
 * acknowledge it, and the click costs them the place they were in. A toast rises
 * in the corner instead: it never takes focus, so typing continues underneath
 * it, and it is never the only report — the panel behind it has already redrawn
 * with the new row, so a toast that is missed loses nothing.
 *
 * <p>A failure is the exception. It stays until it is dismissed, because a
 * message that disappears on a timer is no way to tell someone their work did
 * not save.
 *
 * <p>Built on {@link Popup} rather than an overlay inside the shell so it works
 * on the sign-in window too, which has no shell around it, and so no panel has
 * to reserve space for something that is usually absent.
 */
public final class Toast {

    /** How the outcome should be read, which sets the colour and the dwell. */
    public enum Tone {
        /** It worked. */
        DONE(Feather.CHECK, "toast-done", Duration.seconds(4)),
        /** Something was removed, cancelled or sent back. */
        UNDONE(Feather.ROTATE_CCW, "toast-undone", Duration.seconds(7)),
        /** It did not work. Stays until dismissed. */
        FAILED(Feather.ALERT_TRIANGLE, "toast-failed", null);

        private final Feather icon;
        private final String styleClass;
        private final Duration dwell;

        Tone(Feather icon, String styleClass, Duration dwell) {
            this.icon = icon;
            this.styleClass = styleClass;
            this.dwell = dwell;
        }
    }

    /** Beyond three the corner stops being readable, so the oldest is pushed out. */
    private static final int MAX_VISIBLE = 3;
    private static final double WIDTH = 360;
    private static final double MARGIN = 18;

    private static Popup popup;
    private static VBox stack;
    private static final List<Card> live = new ArrayList<>();
    /* Windows already being followed. Without this a listener would be added on
       every toast, and each one would outlive the card that caused it. */
    private static final Set<Window> anchored = new HashSet<>();

    private Toast() {
    }

    public static void done(String message) {
        show(Tone.DONE, message, null, null, null);
    }

    public static void done(String message, String detail) {
        show(Tone.DONE, message, detail, null, null);
    }

    public static void done(String message, String detail, String actionLabel, Runnable action) {
        show(Tone.DONE, message, detail, actionLabel, action);
    }

    public static void undone(String message) {
        show(Tone.UNDONE, message, null, null, null);
    }

    public static void undone(String message, String detail) {
        show(Tone.UNDONE, message, detail, null, null);
    }

    public static void failed(String message) {
        show(Tone.FAILED, message, null, null, null);
    }

    public static void failed(String message, String detail) {
        show(Tone.FAILED, message, detail, null, null);
    }

    /**
     * Raises a toast over whichever of the application's windows has focus.
     *
     * <p>An identical message already on screen is not repeated: it is given a
     * count and its timer restarts, so a person who presses a button twice sees
     * one card saying so rather than two cards saying the same thing.
     */
    public static synchronized void show(Tone tone, String message, String detail,
            String actionLabel, Runnable action) {
        Window owner = focusedWindow();
        if (owner == null) {
            // Nothing on screen to hang it from — the outcome is still worth
            // recording, so it goes to the console rather than vanishing.
            System.err.println("Aqarat: " + tone + " — " + message);
            return;
        }

        Card existing = findByMessage(message);
        if (existing != null) {
            existing.repeat();
            return;
        }

        ensurePopup();
        Card card = new Card(tone, message, detail, actionLabel, action);
        live.add(card);
        stack.getChildren().add(card.node);
        while (live.size() > MAX_VISIBLE) {
            live.get(0).dismiss();
        }

        if (!popup.isShowing()) {
            popup.show(owner);
        }
        anchorTo(owner);
        card.enter();
    }

    /* ---------------------------------------------------------------- */

    private static void ensurePopup() {
        if (popup != null) {
            return;
        }
        stack = new VBox(10);
        stack.setAlignment(Pos.BOTTOM_RIGHT);
        stack.setPickOnBounds(false);
        stack.getStyleClass().add("toast-layer");
        // Room for the drop shadow, which would otherwise be clipped by the
        // popup's bounds.
        stack.setPadding(new Insets(10));

        popup = new Popup();
        // Nudged back on screen when the window it belongs to is dragged partly
        // off one: a message nobody can see is not a message.
        popup.setAutoFix(true);
        popup.setHideOnEscape(false);
        popup.setAnchorLocation(PopupWindow.AnchorLocation.CONTENT_BOTTOM_RIGHT);
        popup.getContent().add(stack);
        popup.getScene().getStylesheets()
            .add(Toast.class.getResource("/css/app.css").toExternalForm());
        // The popup's scene has a root of its own, and a scene root carries the
        // .root class — which in this stylesheet paints the canvas colour. Left
        // alone it draws a limestone rectangle behind every toast. Set inline so
        // no later stylesheet rule can put it back.
        popup.getScene().getRoot().setStyle("-fx-background-color: transparent;");
        // Its own window, so its own capture hook — F12 on the shell photographs
        // every open window, and a toast is one of them.
        SceneCapture.install(popup.getScene());
    }

    /*
     * The popup is its own window, so it does not travel with the one it belongs
     * to unless it is told to. Following the owner's position and size keeps it
     * pinned to the same corner while the window is dragged or resized.
     */
    private static void anchorTo(Window owner) {
        reposition(owner);
        if (anchored.contains(owner)) {
            return;
        }
        anchored.add(owner);
        owner.xProperty().addListener((obs, old, now) -> reposition(owner));
        owner.yProperty().addListener((obs, old, now) -> reposition(owner));
        owner.widthProperty().addListener((obs, old, now) -> reposition(owner));
        owner.heightProperty().addListener((obs, old, now) -> reposition(owner));
    }

    private static void reposition(Window owner) {
        if (popup == null || !popup.isShowing()) {
            return;
        }
        popup.setAnchorX(owner.getX() + owner.getWidth() - MARGIN);
        popup.setAnchorY(owner.getY() + owner.getHeight() - MARGIN);
    }

    private static Window focusedWindow() {
        Window fallback = null;
        for (Window window : Window.getWindows()) {
            if (window instanceof Popup) {
                continue;
            }
            if (window.isFocused()) {
                return window;
            }
            if (fallback == null && window.isShowing()) {
                fallback = window;
            }
        }
        return fallback;
    }

    private static Card findByMessage(String message) {
        for (Card card : live) {
            if (card.message.equals(message)) {
                return card;
            }
        }
        return null;
    }

    private static synchronized void remove(Card card) {
        live.remove(card);
        stack.getChildren().remove(card.node);
        if (live.isEmpty() && popup != null && popup.isShowing()) {
            popup.hide();
        }
    }

    /* ---------------------------------------------------------------- */

    /** One card in the corner: a badge, the outcome, and the way to close it. */
    private static final class Card {

        private final String message;
        private final HBox node;
        private final Label title;
        private final PauseTransition timer;
        private int occurrences = 1;

        private Card(Tone tone, String message, String detail, String actionLabel, Runnable action) {
            this.message = message;

            FontIcon icon = new FontIcon(tone.icon);
            icon.getStyleClass().add("toast-icon");
            StackPane badge = new StackPane(icon);
            badge.getStyleClass().add("toast-badge");

            title = new Label(message);
            title.getStyleClass().add("toast-title");
            title.setWrapText(true);

            VBox text = new VBox(2, title);
            if (detail != null && !detail.isBlank()) {
                Label detailLabel = new Label(detail);
                detailLabel.getStyleClass().add("toast-detail");
                detailLabel.setWrapText(true);
                text.getChildren().add(detailLabel);
            }
            HBox.setHgrow(text, Priority.ALWAYS);

            node = new HBox(12, badge, text);
            node.getStyleClass().addAll("toast", tone.styleClass);
            node.setAlignment(Pos.TOP_LEFT);
            node.setMaxWidth(WIDTH);
            node.setPrefWidth(WIDTH);

            if (actionLabel != null && action != null) {
                node.getChildren().add(actionButton(actionLabel, action));
            }
            node.getChildren().add(closeButton());

            timer = tone.dwell == null ? null : new PauseTransition(tone.dwell);
            if (timer != null) {
                timer.setOnFinished(e -> dismiss());
                // Reading a toast should not be a race: hovering it holds it.
                node.setOnMouseEntered(e -> timer.pause());
                node.setOnMouseExited(e -> timer.play());
            }
        }

        private Node actionButton(String label, Runnable action) {
            Label link = new Label(label);
            link.getStyleClass().add("toast-action");
            link.setOnMouseClicked(e -> {
                dismiss();
                action.run();
            });
            return link;
        }

        private Node closeButton() {
            FontIcon cross = new FontIcon(Feather.X);
            cross.getStyleClass().add("toast-close");
            Region hit = new StackPane(cross);
            hit.getStyleClass().add("toast-close-hit");
            hit.setOnMouseClicked(e -> dismiss());
            return hit;
        }

        private void enter() {
            node.setOpacity(0);
            FadeTransition fade = new FadeTransition(Duration.millis(180), node);
            fade.setFromValue(0);
            fade.setToValue(1);
            TranslateTransition rise = new TranslateTransition(Duration.millis(180), node);
            rise.setFromY(14);
            rise.setToY(0);
            rise.setInterpolator(Interpolator.SPLINE(0.25, 0.1, 0.25, 1.0));
            fade.play();
            rise.play();
            if (timer != null) {
                timer.playFromStart();
            }
        }

        private void repeat() {
            occurrences++;
            title.setText(message + "  ×" + occurrences);
            if (timer != null) {
                timer.playFromStart();
            }
        }

        private void dismiss() {
            if (timer != null) {
                timer.stop();
            }
            FadeTransition fade = new FadeTransition(Duration.millis(140), node);
            fade.setFromValue(node.getOpacity());
            fade.setToValue(0);
            SequentialTransition out = new SequentialTransition(fade);
            out.setOnFinished(e -> remove(this));
            out.play();
        }
    }
}
