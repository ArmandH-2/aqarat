package co.syntropyhq.aqarat.util;

import co.syntropyhq.aqarat.model.DealType;
import co.syntropyhq.aqarat.model.District;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.PropertyStatus;
import co.syntropyhq.aqarat.model.PropertyType;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

/**
 * Reusable UI component builders for status pills, avatar circles,
 * spec chips, and empty state cards.
 */
public final class UIHelper {

    private static final int THUMB_WIDTH = 140;
    private static final int THUMB_HEIGHT = 100;

    /** Narrowest a listing card may be before the grid drops a column. */
    public static final double CARD_MIN_WIDTH = 268;

    /** Gutter between listing cards, horizontally and vertically. */
    public static final double CARD_GAP = 22;

    /** Photographs are 3:2, matching how the seed images are cropped. */
    private static final double CARD_PHOTO_RATIO = 2.0 / 3.0;

    private UIHelper() {
    }

    /**
     * A grid card led by its photograph.
     *
     * <p>The list card this replaces put a 140px thumbnail beside three lines of
     * text across the full window width, which fits three properties on a
     * 1080-tall screen and makes a catalogue of eleven hundred homes feel like a
     * table. Leading with the image is what lets a row of three sit side by side
     * and what makes browsing feel like shopping rather than querying.
     */
    public static Node createListingCard(Property property, District district, PropertyType type,
            String photoPath) {
        VBox card = new VBox();
        card.getStyleClass().add("listing-card");
        card.setCursor(Cursor.HAND);

        card.getChildren().addAll(
            buildCardPhoto(card, photoPath, property.getStatus()),
            buildCardBody(property, district, type));

        card.setOnMouseClicked(event -> Router.show(Panel.PROPERTY_DETAILS, property.getId()));
        AnimationUtil.addHoverLift(card);
        return card;
    }

    /*
     * The photograph tracks the card's width so the grid can widen its columns
     * to fill whatever window it is given. Everything here is driven from the
     * card's width — never the other way round — so no size feeds back upwards.
     */
    private static StackPane buildCardPhoto(VBox card, String photoPath, PropertyStatus status) {
        StackPane frame = new StackPane();
        frame.getStyleClass().add("listing-photo");
        frame.setAlignment(Pos.TOP_LEFT);
        frame.setMinWidth(0);
        frame.minHeightProperty().bind(card.widthProperty().multiply(CARD_PHOTO_RATIO));
        frame.prefHeightProperty().bind(card.widthProperty().multiply(CARD_PHOTO_RATIO));
        frame.maxHeightProperty().bind(card.widthProperty().multiply(CARD_PHOTO_RATIO));

        File file = photoPath == null ? null : new File("uploads/" + photoPath);
        if (file != null && file.exists()) {
            // Decoded once at a generous width; JavaFX scales it down to fit.
            ImageView photo = new ImageView(new Image(
                file.toURI().toString(), 600, 400, false, true, true));
            photo.setPreserveRatio(false);
            photo.fitWidthProperty().bind(frame.widthProperty());
            photo.fitHeightProperty().bind(frame.heightProperty());

            // Only the top corners are rounded: the photo meets the body flush.
            Rectangle clip = new Rectangle();
            clip.widthProperty().bind(frame.widthProperty());
            clip.heightProperty().bind(frame.heightProperty());
            clip.setArcWidth(24);
            clip.setArcHeight(24);
            photo.setClip(clip);
            frame.getChildren().add(photo);
        }

        if (status != null) {
            Label badge = new Label(Format.enumLabel(status));
            badge.getStyleClass().addAll("pill-on-photo", statusToneClass(status));
            StackPane.setMargin(badge, new Insets(12, 0, 0, 12));
            frame.getChildren().add(badge);
        }
        return frame;
    }

    private static VBox buildCardBody(Property property, District district, PropertyType type) {
        VBox body = new VBox(9);
        body.getStyleClass().add("listing-body");

        // Both deal types show a bare amount; the unit beside it carries the
        // difference, so the two prices stay the same visual weight in a grid.
        Label price = new Label(Format.salePrice(property.getAskingPrice()));
        price.getStyleClass().add("price-display");

        Label priceUnit = new Label(property.getDealType() == DealType.RENT
            ? "per month"
            : perSquareMetre(property));
        priceUnit.getStyleClass().add("hint");

        HBox priceRow = new HBox(8, price, priceUnit);
        priceRow.setAlignment(Pos.BASELINE_LEFT);

        Label title = new Label(property.getTitle());
        title.getStyleClass().add("body-medium");
        title.setWrapText(false);

        String districtName = district == null ? "—" : district.getName();
        String typeName = type == null ? "" : " · " + type.getName();
        Label where = new Label(districtName + " · " + Format.enumLabel(property.getDealType()) + typeName);
        where.getStyleClass().add("hint");

        VBox naming = new VBox(2, title, where);

        // Bedrooms and bathrooms are primitives: a warehouse or a plot of land
        // legitimately has zero of both, and a chip reading "0 bed" is noise.
        HBox specs = new HBox(7);
        if (property.getBedrooms() > 0) {
            specs.getChildren().add(createSpecChip(property.getBedrooms() + " bed"));
        }
        if (property.getBathrooms() > 0) {
            specs.getChildren().add(createSpecChip(property.getBathrooms() + " bath"));
        }
        if (property.getAreaSqm() != null) {
            specs.getChildren().add(createSpecChip(Format.area(property.getAreaSqm())));
        }

        body.getChildren().addAll(priceRow, naming, specs);
        return body;
    }

    private static String perSquareMetre(Property property) {
        BigDecimal area = property.getAreaSqm();
        BigDecimal price = property.getAskingPrice();
        if (area == null || price == null || area.signum() <= 0) {
            return "";
        }
        return Format.pricePerSqm(price.divide(area, 0, RoundingMode.HALF_UP));
    }

    private static String statusToneClass(PropertyStatus status) {
        return switch (status) {
            case AVAILABLE -> "tone-good";
            case RESERVED, UNDER_CONTRACT -> "tone-info";
            case PENDING_REVIEW, NEEDS_INFO -> "tone-warn";
            case REJECTED -> "tone-bad";
            default -> "tone-neutral";
        };
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

    /**
     * Builds the result card used by both the listings browser and the search
     * assistant: thumbnail, title with a deal badge, location, spec chips and a
     * price callout. Clicking it opens the property.
     *
     * <p>The district and type are passed in rather than looked up, and the photo
     * arrives as a path rather than an id, so this stays a pure view builder with
     * no reach into a service. {@code reason} is the assistant's one-line
     * explanation of why the property fits; pass null to leave it off.
     */
    public static Node createPropertyCard(Property property, District district, PropertyType type,
            String photoPath, String reason) {
        HBox card = new HBox(16);
        card.getStyleClass().addAll("card", "card-hoverable");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(14));
        card.setCursor(Cursor.HAND);

        card.getChildren().addAll(
            buildThumbnail(photoPath),
            buildDetails(property, district, type, reason),
            buildPriceBox(property));

        card.setOnMouseClicked(event -> Router.show(Panel.PROPERTY_DETAILS, property.getId()));
        AnimationUtil.addHoverLift(card);
        return card;
    }

    private static StackPane buildThumbnail(String photoPath) {
        StackPane container = new StackPane();
        container.setPrefSize(THUMB_WIDTH, THUMB_HEIGHT);
        container.setMinSize(THUMB_WIDTH, THUMB_HEIGHT);
        container.setMaxSize(THUMB_WIDTH, THUMB_HEIGHT);
        container.setStyle("-fx-background-color: -c-surface-subtle; -fx-background-radius: 8px;"
            + " -fx-border-color: -c-border-subtle; -fx-border-radius: 8px;");

        File file = photoPath == null ? null : new File("uploads/" + photoPath);
        if (file != null && file.exists()) {
            ImageView thumbnail = new ImageView(
                new Image(file.toURI().toString(), THUMB_WIDTH, THUMB_HEIGHT, false, true));
            thumbnail.setFitWidth(THUMB_WIDTH);
            thumbnail.setFitHeight(THUMB_HEIGHT);
            thumbnail.setPreserveRatio(false);

            Rectangle clip = new Rectangle(THUMB_WIDTH, THUMB_HEIGHT);
            clip.setArcWidth(16);
            clip.setArcHeight(16);
            thumbnail.setClip(clip);
            container.getChildren().add(thumbnail);
        } else {
            Label placeholder = new Label("🏠");
            placeholder.setStyle("-fx-font-size: 28px; -fx-opacity: 0.6;");
            container.getChildren().add(placeholder);
        }
        return container;
    }

    private static VBox buildDetails(Property property, District district, PropertyType type,
            String reason) {
        VBox details = new VBox(6);
        HBox.setHgrow(details, Priority.ALWAYS);

        Label title = new Label(property.getTitle());
        title.getStyleClass().add("section-title");
        HBox.setHgrow(title, Priority.ALWAYS);

        Label dealBadge = new Label(Format.enumLabel(property.getDealType()));
        dealBadge.getStyleClass().addAll("pill",
            property.getDealType() == DealType.SALE ? "pill-good" : "pill-info");

        HBox header = new HBox(8, title, dealBadge);
        header.setAlignment(Pos.CENTER_LEFT);

        Label subtitle = new Label((district != null ? district.getName() : "Lebanon")
            + " • " + (type != null ? type.getName() : "Property"));
        subtitle.getStyleClass().add("label-soft");

        HBox specChips = new HBox(8);
        specChips.setAlignment(Pos.CENTER_LEFT);
        specChips.getChildren().addAll(
            createSpecChip(property.getBedrooms() + " Beds"),
            createSpecChip(property.getBathrooms() + " Baths"),
            createSpecChip(Format.area(property.getAreaSqm())));
        if (property.isHasParking()) {
            specChips.getChildren().add(createSpecChip("Parking"));
        }

        details.getChildren().addAll(header, subtitle, specChips);

        if (reason != null && !reason.isBlank()) {
            Label reasonLabel = new Label(reason);
            reasonLabel.getStyleClass().add("hint");
            reasonLabel.setStyle("-fx-text-fill: -c-primary-light; -fx-font-style: italic;");
            reasonLabel.setWrapText(true);
            details.getChildren().add(reasonLabel);
        }
        return details;
    }

    private static VBox buildPriceBox(Property property) {
        VBox priceBox = new VBox(2);
        priceBox.setAlignment(Pos.CENTER_RIGHT);
        priceBox.setPrefWidth(160);

        Label price = new Label(property.getDealType() == DealType.SALE
            ? Format.salePrice(property.getAskingPrice())
            : Format.monthlyRent(property.getAskingPrice()));
        price.getStyleClass().add("section-title");
        price.setStyle("-fx-font-size: 17px; -fx-font-weight: 700; -fx-text-fill: -c-primary;");

        BigDecimal pricePerSqm = BigDecimal.ZERO;
        if (property.getAreaSqm() != null
                && property.getAreaSqm().compareTo(BigDecimal.ZERO) > 0
                && property.getAskingPrice() != null) {
            pricePerSqm = property.getAskingPrice()
                .divide(property.getAreaSqm(), 0, RoundingMode.HALF_UP);
        }
        Label pricePerSqmLabel = new Label("$" + pricePerSqm + "/m²");
        pricePerSqmLabel.getStyleClass().add("hint");

        priceBox.getChildren().addAll(price, pricePerSqmLabel);
        return priceBox;
    }
}
