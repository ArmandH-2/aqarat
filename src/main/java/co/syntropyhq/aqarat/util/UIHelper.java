package co.syntropyhq.aqarat.util;

import co.syntropyhq.aqarat.model.PropertyStatus;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

/**
 * Reusable UI component builders for status pills, avatar circles,
 * spec chips, and empty state cards.
 */
public final class UIHelper {

    private UIHelper() {
    }

    /**
     * Builds a styled status pill with appropriate color class.
     */
    public static Label createStatusPill(PropertyStatus status) {
        if (status == null) {
            Label pill = new Label("Unknown");
            pill.getStyleClass().addAll("pill", "pill-neutral");
            return pill;
        }

        String labelText = Format.enumLabel(status);
        String styleClass;
        switch (status) {
            case AVAILABLE:
                styleClass = "pill-good";
                break;
            case PENDING_REVIEW:
            case NEEDS_INFO:
            case WITHDRAWAL_REQUESTED:
                styleClass = "pill-warn";
                break;
            case REJECTED:
                styleClass = "pill-bad";
                break;
            case RESERVED:
            case UNDER_CONTRACT:
            case DRAFT:
                styleClass = "pill-info";
                break;
            case CLOSED:
            case WITHDRAWN:
            default:
                styleClass = "pill-neutral";
                break;
        }

        Label pill = new Label(labelText);
        pill.getStyleClass().addAll("pill", styleClass);
        return pill;
    }

    /**
     * Builds a generic styled pill with text and variant class.
     */
    public static Label createPill(String text, String variantClass) {
        Label pill = new Label(text);
        pill.getStyleClass().addAll("pill", variantClass);
        return pill;
    }

    /**
     * Builds a circular avatar with user initials.
     */
    public static Node createAvatar(String fullName, double radius) {
        String initials = "?";
        if (fullName != null && !fullName.trim().isEmpty()) {
            String[] parts = fullName.trim().split("\\s+");
            if (parts.length >= 2) {
                initials = ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
            } else if (parts.length == 1 && parts[0].length() > 0) {
                initials = ("" + parts[0].charAt(0)).toUpperCase();
            }
        }

        Circle circle = new Circle(radius);
        circle.getStyleClass().add("avatar-circle");

        Label label = new Label(initials);
        label.getStyleClass().add("avatar-text");
        label.setStyle("-fx-font-size: " + (int)(radius * 0.85) + "px; -fx-font-weight: 700; -fx-text-fill: -c-primary;");

        StackPane avatar = new StackPane(circle, label);
        avatar.setAlignment(Pos.CENTER);
        return avatar;
    }

    /**
     * Builds a spec chip node (e.g. "3 bed", "2 bath", "150 m²").
     */
    public static HBox createSpecChip(String text) {
        HBox chip = new HBox(4);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("spec-chip");

        Label label = new Label(text);
        label.getStyleClass().add("hint");
        label.setStyle("-fx-font-weight: 500; -fx-text-fill: -c-text-secondary;");

        chip.getChildren().add(label);
        return chip;
    }

    /**
     * Builds a rich empty-state card with title and subtitle.
     */
    public static VBox createEmptyState(String title, String subtitle) {
        VBox box = new VBox(6);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("empty-state-box");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-title");
        titleLabel.setStyle("-fx-text-fill: -c-text-muted;");

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("hint");
        subtitleLabel.setWrapText(true);

        box.getChildren().addAll(titleLabel, subtitleLabel);
        return box;
    }
}
