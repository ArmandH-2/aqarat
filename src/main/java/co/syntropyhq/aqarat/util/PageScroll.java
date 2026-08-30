package co.syntropyhq.aqarat.util;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.input.ScrollEvent;

/**
 * Makes the wheel mean the same thing everywhere on a panel.
 *
 * <p>A {@code ListView} inside a page keeps the wheel to itself. That left the
 * application with two different behaviours in one panel: over the list the list
 * scrolled and the title, subtitle and tabs stayed put; an inch to the left, over
 * the page background, the same gesture scrolled the page. Which one happened
 * depended on where the pointer happened to be resting, and on eleven of the
 * fourteen panels the header could not be scrolled away at all — two hundred
 * pixels of a laptop screen spent on a title the reader had already read.
 *
 * <p>The rule installed here is <strong>the page moves first</strong>. While the
 * page has anywhere left to go, the wheel scrolls the page; the header leaves,
 * the list grows into the space, and only then does the list take over. On the
 * way up the page reclaims the wheel, so the header comes back before the reader
 * has to think about it.
 *
 * <p>Chosen over the obvious alternative — sizing lists to their contents so the
 * page scrolls naturally — because that renders every row. Listings and Discover
 * each hold over a thousand.
 */
public final class PageScroll {

    private PageScroll() {
    }

    /**
     * Gives the page first claim on the wheel everywhere inside {@code root}.
     *
     * <p>Safe to call on any panel: one without a page-level scroll pane, or
     * without any nested scroller, is left exactly as it was.
     */
    public static void install(Parent root) {
        ScrollPane page = pageScrollPane(root);
        // Searched from the content rather than from the scroll pane. A control
        // builds its skin lazily, on its first layout, so before the panel is on
        // screen a ScrollPane reports no children at all and a lookup through it
        // finds nothing — which is how this first shipped doing nothing.
        if (page == null || !(page.getContent() instanceof Parent content)) {
            return;
        }
        // One selector at a time as well: lookupAll takes a single simple
        // selector and silently matches nothing when given a comma-separated
        // group.
        for (String selector : new String[] {".list-view", ".table-view", ".scroll-pane"}) {
            for (Node inner : content.lookupAll(selector)) {
                chain(inner, page);
            }
        }
        for (Node list : content.lookupAll(PAGE_LIST)) {
            fillViewport(list, page);
        }
    }

    /** Marks the list that is the panel's content, rather than a control in it. */
    private static final String PAGE_LIST = ".page-list";

    /*
     * Gives the panel's main list the whole height of the window.
     *
     * These lists were pinned at a fixed 450-500px, which did two things at
     * once: it left a band of empty canvas under the list on any window taller
     * than that, and it made the page fit its own viewport, so there was no page
     * movement available and the header above the list could never be scrolled
     * away. Sized to the viewport, the page ends up exactly one header taller
     * than the screen - enough to scroll the header off, and nothing more.
     */
    private static void fillViewport(Node list, ScrollPane page) {
        if (!(list instanceof Region region)) {
            return;
        }
        page.viewportBoundsProperty().addListener((observable, was, now) -> {
            if (now != null && now.getHeight() > 0) {
                region.setPrefHeight(now.getHeight());
                region.setMinHeight(now.getHeight());
            }
        });
        Bounds viewport = page.getViewportBounds();
        if (viewport != null && viewport.getHeight() > 0) {
            region.setPrefHeight(viewport.getHeight());
            region.setMinHeight(viewport.getHeight());
        }
    }

    /*
     * The page's own scroller: the root when the panel is one, otherwise the
     * outermost one inside it. Not every panel has one - the sign-in window does
     * not - and those are left alone.
     */
    private static ScrollPane pageScrollPane(Parent root) {
        if (root instanceof ScrollPane scrollPane) {
            return scrollPane;
        }
        for (Node node : root.getChildrenUnmodifiable()) {
            if (node instanceof ScrollPane scrollPane) {
                return scrollPane;
            }
            if (node instanceof Parent parent) {
                ScrollPane nested = pageScrollPane(parent);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    /*
     * A filter rather than a handler: it has to run before the list's own skin,
     * which is what consumes the event today.
     */
    private static void chain(Node inner, ScrollPane page) {
        inner.addEventFilter(ScrollEvent.SCROLL, event -> {
            double delta = event.getDeltaY();
            if (delta == 0) {
                return;
            }
            double overflow = overflow(page);
            if (overflow <= 0) {
                // Everything fits; there is no page movement to prefer.
                return;
            }
            boolean down = delta < 0;
            double position = page.getVvalue();
            boolean pageHasRoom = down
                ? position < page.getVmax() - EPSILON
                : position > page.getVmin() + EPSILON;
            if (!pageHasRoom) {
                // The page is against its end, so the list is what the reader
                // means. Left unconsumed, which hands it straight back.
                return;
            }
            page.setVvalue(clamp(position - delta / overflow, page));
            event.consume();
        });
    }

    private static final double EPSILON = 0.0001;

    /** How far the page can travel, in pixels; zero or less when it all fits. */
    private static double overflow(ScrollPane page) {
        Node content = page.getContent();
        Bounds viewport = page.getViewportBounds();
        if (content == null || viewport == null) {
            return 0;
        }
        return content.getLayoutBounds().getHeight() - viewport.getHeight();
    }

    private static double clamp(double value, ScrollPane page) {
        return Math.max(page.getVmin(), Math.min(page.getVmax(), value));
    }
}
