package co.syntropyhq.aqarat.util;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Reusable animation utilities for smooth JavaFX transitions,
 * panel entry reveals, hover physics, and numerical counters.
 */
public final class AnimationUtil {

    private AnimationUtil() {
    }

    /**
     * Smooth fade-in animation for a node.
     */
    public static void fadeIn(Node node, double durationMillis) {
        if (node == null) return;
        node.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(durationMillis), node);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.setInterpolator(Interpolator.EASE_OUT);
        ft.play();
    }

    /**
     * Smooth slide and fade-in animation for panel entry.
     */
    public static void slideAndFadeIn(Node node, double fromY, double durationMillis) {
        if (node == null) return;
        node.setOpacity(0);
        node.setTranslateY(fromY);

        FadeTransition ft = new FadeTransition(Duration.millis(durationMillis), node);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition tt = new TranslateTransition(Duration.millis(durationMillis), node);
        tt.setFromY(fromY);
        tt.setToY(0);
        tt.setInterpolator(Interpolator.SPLINE(0.25, 0.1, 0.25, 1.0));

        ParallelTransition pt = new ParallelTransition(ft, tt);
        pt.play();
    }

    /**
     * Staggered fade and slide-in for children of a container or list of nodes.
     */
    public static void staggerIn(Node[] nodes, double delayStepMillis) {
        if (nodes == null) return;
        for (int i = 0; i < nodes.length; i++) {
            Node node = nodes[i];
            if (node == null) continue;
            node.setOpacity(0);
            node.setTranslateY(12);

            FadeTransition ft = new FadeTransition(Duration.millis(300), node);
            ft.setFromValue(0);
            ft.setToValue(1);
            ft.setDelay(Duration.millis(i * delayStepMillis));
            ft.setInterpolator(Interpolator.EASE_OUT);

            TranslateTransition tt = new TranslateTransition(Duration.millis(300), node);
            tt.setFromY(12);
            tt.setToY(0);
            tt.setDelay(Duration.millis(i * delayStepMillis));
            tt.setInterpolator(Interpolator.SPLINE(0.25, 0.1, 0.25, 1.0));

            new ParallelTransition(ft, tt).play();
        }
    }

    /**
     * Adds a subtle tactile lift and shadow increase on hover.
     */
    public static void addHoverLift(Node node) {
        if (node == null) return;

        DropShadow defaultShadow = new DropShadow(8, 0, 2, Color.rgb(0, 0, 0, 0.04));
        DropShadow hoverShadow = new DropShadow(14, 0, 4, Color.rgb(0, 0, 0, 0.09));

        node.setEffect(defaultShadow);

        node.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(160), node);
            st.setToX(1.012);
            st.setToY(1.012);
            st.setInterpolator(Interpolator.EASE_OUT);
            st.play();
            node.setEffect(hoverShadow);
        });

        node.setOnMouseExited(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(160), node);
            st.setToX(1.0);
            st.setToY(1.0);
            st.setInterpolator(Interpolator.EASE_OUT);
            st.play();
            node.setEffect(defaultShadow);
        });
    }

}
