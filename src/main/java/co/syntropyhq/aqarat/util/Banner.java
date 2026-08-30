package co.syntropyhq.aqarat.util;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * What a panel shows when it could not load what it exists to show.
 *
 * <p>A modal saying "could not reach the database" takes the message away the
 * moment it is acknowledged and leaves an empty panel that looks like the person
 * simply owns nothing. The banner stays in the space the list would have
 * filled, so the panel explains itself for as long as it is wrong, and it
 * carries the way out rather than making the person guess that leaving and
 * coming back is the retry.
 *
 * <p>It also says what is safe. Someone who cannot see their contracts assumes
 * the worst about their contracts, and a panel that only reads has lost nothing
 * worth worrying about.
 */
public final class Banner {

    private Banner() {
    }

    /**
     * A failure to load, with a way to try again.
     *
     * @param what  what could not be loaded, in the person's words
     * @param why   what went wrong and what is safe
     * @param retry run when the person presses Try again
     */
    public static VBox failure(String what, String why, Runnable retry) {
        FontIcon icon = new FontIcon(Feather.ALERT_TRIANGLE);
        icon.getStyleClass().add("banner-icon");

        Label title = new Label(what);
        title.getStyleClass().add("banner-title");
        title.setWrapText(true);

        Label detail = new Label(why);
        detail.getStyleClass().add("banner-detail");
        detail.setWrapText(true);

        VBox text = new VBox(4, title, detail);
        text.setMaxWidth(420);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox row = new HBox(12, icon, text);
        row.setAlignment(Pos.TOP_LEFT);

        if (retry != null) {
            Button again = new Button("Try again");
            again.getStyleClass().addAll("button", "button-compact", "button-danger");
            again.setOnAction(e -> retry.run());
            row.getChildren().add(again);
        }

        VBox banner = new VBox(row);
        banner.getStyleClass().add("banner-failure");
        banner.setAlignment(Pos.CENTER);
        banner.setMaxHeight(VBox.USE_PREF_SIZE);
        return banner;
    }
}
