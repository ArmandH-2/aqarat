package co.syntropyhq.aqarat.util;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.Pane;

public final class Router {

    private static Pane contentPane;
    private static Entry current;
    private static final Deque<Entry> history = new ArrayDeque<>();

    private Router() {
    }

    public static void setContentPane(Pane pane) {
        contentPane = pane;
    }

    public static void show(Panel panel) {
        show(panel, null);
    }

    public static void show(Panel panel, int id) {
        show(panel, Integer.valueOf(id));
    }

    public static void back() {
        if (history.isEmpty()) {
            return;
        }
        current = history.pop();
        display(current);
    }

    private static void show(Panel panel, Integer id) {
        if (current != null) {
            history.push(current);
        }
        current = new Entry(panel, id);
        display(current);
    }

    private static void display(Entry entry) {
        String path = "/fxml/" + entry.panel.getFxml();
        FXMLLoader loader = new FXMLLoader(Router.class.getResource(path));
        Parent view;
        try {
            view = loader.load();
        } catch (IOException e) {
            throw new PanelLoadException(entry.panel, e);
        }
        if (entry.id != null && loader.getController() instanceof NeedsId needsId) {
            needsId.receiveId(entry.id.intValue());
        }
        if (contentPane != null) {
            contentPane.getChildren().setAll(view);
            // Smooth entrance animation for all panel transitions
            AnimationUtil.slideAndFadeIn(view, 8, 220);
        }
    }

    // Keeps a panel and the optional id it was opened with together, so back()
    // can reload exactly what was on screen before.
    private static final class Entry {
        private final Panel panel;
        private final Integer id;

        private Entry(Panel panel, Integer id) {
            this.panel = panel;
            this.id = id;
        }
    }

    private static final class PanelLoadException extends RuntimeException {
        private PanelLoadException(Panel panel, IOException cause) {
            super("Could not load panel " + panel.name(), cause);
        }
    }
}
