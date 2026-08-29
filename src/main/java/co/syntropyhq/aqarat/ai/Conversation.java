package co.syntropyhq.aqarat.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Conversation {

    private static final int MAX_HISTORY = 20;
    private final List<ChatMessage> history = new ArrayList<>();

    public synchronized void append(ChatMessage message) {
        if (message != null) {
            history.add(message);
            trim();
        }
    }

    // The system prompt has to sit at index 0 whatever order the caller works
    // in, because trim() protects the first message and only the first. The
    // controller appends the user's text before the service is called, so
    // appending the prompt instead of inserting it would put it second and add
    // a fresh copy on every turn.
    public synchronized void ensureSystem(ChatMessage systemMessage) {
        if (history.isEmpty() || !"system".equalsIgnoreCase(history.get(0).getRole())) {
            history.add(0, systemMessage);
        }
    }

    public synchronized List<ChatMessage> messages() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    public synchronized void clear() {
        history.clear();
    }

    public synchronized int size() {
        return history.size();
    }

    private void trim() {
        if (history.isEmpty()) {
            return;
        }
        boolean hasSystem = "system".equalsIgnoreCase(history.get(0).getRole());
        int startIndex = hasSystem ? 1 : 0;
        int nonSystemCount = history.size() - startIndex;
        if (nonSystemCount > MAX_HISTORY) {
            int toRemove = nonSystemCount - MAX_HISTORY;
            for (int i = 0; i < toRemove; i++) {
                history.remove(startIndex);
            }
        }
    }
}
