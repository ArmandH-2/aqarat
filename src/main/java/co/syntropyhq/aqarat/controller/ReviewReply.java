package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.service.PropertyService;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.Dialogs;
import co.syntropyhq.aqarat.util.SessionManager;
import java.sql.SQLException;
import java.util.Optional;

/**
 * The owner answering the agent's question on a submission.
 *
 * <p>Two screens offer it — the row in My properties, and the card at the top of
 * Portfolio that says an agent has replied — and only one of them used to do
 * anything. The card's button called the method that selects the Properties tab,
 * which on the Properties tab is nothing at all: the most prominent thing on an
 * owner's portfolio was a button that did not respond to being pressed.
 *
 * <p>Kept here rather than duplicated so the two cannot drift into asking the
 * same question two different ways.
 */
final class ReviewReply {

    private ReviewReply() {
    }

    /**
     * Asks the owner for their answer and sends it.
     *
     * @return true when an answer was sent, which is the caller's cue to reload
     */
    static boolean ask(PropertyService propertyService, Property property) {
        Optional<String> input = Dialogs.note("Answer the reviewing agent")
            .about(property.getTitle())
            .explaining("Your answer goes back into the review queue with the submission, so "
                + "the agent sees it beside the details it is about.")
            .field("Your response")
            .placeholder("What the agent asked for, in your own words")
            .confirm("Send it back for review")
            .cancel("Not now")
            .show();
        if (input.isEmpty()) {
            return false;
        }
        try {
            propertyService.respondToReview(property.getId(), input.get(),
                SessionManager.getCurrentUser().getId());
        } catch (PropertyService.InvalidTransitionException e) {
            AlertUtil.showError("This submission can no longer be sent back for review.");
            return false;
        } catch (SQLException e) {
            AlertUtil.showError("Could not reach the database. Try again.");
            return false;
        }
        AlertUtil.showInfo("Sent back for review",
            "Your answer sits beside the submission in the agent's queue.");
        return true;
    }
}
