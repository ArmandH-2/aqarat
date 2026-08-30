package co.syntropyhq.aqarat.util;

/**
 * Where the application used to open a window to say something.
 *
 * <p>It opened 155 of them. Most were not decisions: they announced that
 * something had worked, or that a field was empty, and each one dimmed the
 * screen and demanded a click before the person could carry on. That is the
 * opposite of a considered product, and it is a problem of count rather than of
 * appearance — restyling 155 modal windows still leaves 155 doors.
 *
 * <p>So the calls stayed and the surface changed. An outcome is now reported by
 * a {@link Toast} in the corner, which takes no focus and leaves on its own. The
 * two things still worth stopping someone for — a decision and a form — moved to
 * {@link Dialogs}. A panel that could not load what it exists to show says so
 * with a {@link Banner} in the space the list would have filled, and a
 * {@link Receipt} is a document rather than a message.
 *
 * <p>This class is kept as the plain way to report an outcome from a controller;
 * where the message deserves a banner or a receipt, the controller reaches for
 * those directly.
 */
public final class AlertUtil {

    private AlertUtil() {
    }

    /** Something worked. Green, four seconds, no click. */
    public static void showInfo(String message) {
        Toast.done(message);
    }

    /** Something worked, with a second line of consequence. */
    public static void showInfo(String message, String detail) {
        Toast.done(message, detail);
    }

    /**
     * Something did not work.
     *
     * <p>Red, and it stays until it is dismissed. A message telling someone
     * their work did not save must not disappear on a timer.
     */
    public static void showError(String message) {
        Toast.failed(message);
    }

    public static void showError(String message, String detail) {
        Toast.failed(message, detail);
    }

    /** Something was cancelled, withdrawn or sent back. Brass, seven seconds. */
    public static void showUndone(String message) {
        Toast.undone(message);
    }

    public static void showUndone(String message, String detail) {
        Toast.undone(message, detail);
    }
}
