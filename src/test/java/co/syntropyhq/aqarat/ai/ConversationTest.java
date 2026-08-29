package co.syntropyhq.aqarat.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationTest {

    @Test
    void retainsSystemMessageAndCapsHistoryAt20() {
        Conversation conversation = new Conversation();
        conversation.append(ChatMessage.system("System instructions"));

        for (int i = 1; i <= 25; i++) {
            conversation.append(ChatMessage.user("User message " + i));
        }

        var messages = conversation.messages();
        assertEquals(21, messages.size(), "Should have 1 system message + 20 recent messages");
        assertEquals("system", messages.get(0).getRole());
        assertEquals("System instructions", messages.get(0).getContent());
        assertEquals("User message 6", messages.get(1).getContent());
        assertEquals("User message 25", messages.get(20).getContent());
    }

    // The controller appends the user's text before the service runs, so the
    // prompt has to be inserted at the front rather than appended - and only
    // once, however many turns the conversation lasts.
    @Test
    void systemPromptGoesToTheFrontAndIsAddedOnlyOnce() {
        Conversation conversation = new Conversation();
        conversation.append(ChatMessage.user("A flat in Beirut"));
        conversation.ensureSystem(ChatMessage.system("System instructions"));
        conversation.append(ChatMessage.user("cheaper"));
        conversation.ensureSystem(ChatMessage.system("System instructions"));

        var messages = conversation.messages();
        assertEquals(3, messages.size(), "the prompt must not be added twice");
        assertEquals("system", messages.get(0).getRole());
        assertEquals("A flat in Beirut", messages.get(1).getContent());
    }

    @Test
    void clearEmptiesAllMessages() {
        Conversation conversation = new Conversation();
        conversation.append(ChatMessage.user("Hello"));
        conversation.clear();
        assertTrue(conversation.messages().isEmpty());
    }
}
