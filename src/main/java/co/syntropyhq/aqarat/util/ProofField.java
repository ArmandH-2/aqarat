package co.syntropyhq.aqarat.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import javafx.scene.Cursor;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.FileChooser;

/**
 * The proof row on a payment dialog: a field that is filled by choosing a
 * file rather than by typing.
 *
 * <p>It is a {@link TextField} on purpose. {@code Dialogs.Form} lays out one
 * control per row and knows how to keep its submit button disabled while a
 * required {@code TextInputControl} is empty, so being one means the proof
 * row is styled like every other row and its required-ness works without
 * teaching the dialog anything new. What the person sees is the name of the
 * file they picked; what the caller reads is {@link #chosenFile()}.
 *
 * <p>The file is validated the moment it is picked and copied only when the
 * payment is actually saved, so backing out of the dialog leaves nothing
 * behind in the uploads folder.
 */
public final class ProofField extends TextField {

    private Path chosen;

    public ProofField() {
        setEditable(false);
        setPromptText("Click to attach a receipt or transfer slip");
        setCursor(Cursor.HAND);
        setOnMouseClicked(event -> choose());
        setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                choose();
            }
        });
    }

    /** The file picked, or null if none was. */
    public Path chosenFile() {
        return chosen;
    }

    private void choose() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose the receipt or transfer slip");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
            "PDF or image (*.pdf, *.jpg, *.jpeg, *.png)", "*.pdf", "*.jpg", "*.jpeg", "*.png"));
        File picked = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
        if (picked == null) {
            // Backing out of the chooser is not a decision to remove whatever
            // was already attached.
            return;
        }
        Path path = picked.toPath();
        try {
            PaymentProofStore.validate(path);
        } catch (IOException e) {
            // DocumentStore's messages are already written for the person who
            // picked the file, so they are shown as they are.
            AlertUtil.showUndone("That file cannot be attached.", e.getMessage());
            return;
        }
        chosen = path;
        setText(path.getFileName().toString());
    }
}
