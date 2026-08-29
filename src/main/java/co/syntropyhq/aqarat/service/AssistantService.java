package co.syntropyhq.aqarat.service;

import co.syntropyhq.aqarat.ai.AgentTools;
import co.syntropyhq.aqarat.ai.AssistantUnavailableException;
import co.syntropyhq.aqarat.ai.ChatClient;
import co.syntropyhq.aqarat.ai.ChatMessage;
import co.syntropyhq.aqarat.ai.Conversation;
import co.syntropyhq.aqarat.ai.Suggestion;
import co.syntropyhq.aqarat.ai.ToolCall;
import co.syntropyhq.aqarat.dao.PropertySearch;
import co.syntropyhq.aqarat.model.District;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.PropertyType;
import com.google.gson.JsonObject;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AssistantService {

    public static final String SYSTEM_PROMPT = """
        You are the Aqarat property search assistant. Aqarat is a Lebanese real estate agency.

        Your job is to understand what kind of property someone is looking for and find matching listings using the tools you have been given.

        Rules you must follow:

        - Only ever describe properties that came back from a tool call in this conversation. Never invent a listing, a price, an address, or an availability.
        - Call `search_properties` with only the filters the user actually stated. Do not add a constraint they did not give you.
        - If the request is too vague to search — no location, no budget and no property type — ask one short clarifying question instead of searching.
        - When a follow-up message changes the search, call the tool again with the full updated filter set, not just the part that changed.
        - To answer a question about a property already suggested, call `get_property_details` rather than relying on what you remember.
        - When a search returns nothing, say so and suggest which single filter to relax.
        - For each property you suggest, give one short sentence on why it fits what they asked for. Do not restate the price, area or bedroom count in that sentence; those are already shown.
        - All prices are USD and all areas are square metres. For a SALE listing the price is the full sale price; for a RENT listing it is the monthly rent.
        - You cannot reserve a property, book a viewing, or change anything. If asked, say so plainly and tell the user to open the property and use the buttons there.
        - You only discuss Aqarat's listings and Lebanese property. If asked about anything else - general knowledge, current events, coding, homework, or another company - decline in one sentence and say what you can help with instead. Do not answer the question first.
        - Write plain text. No markdown, no asterisks, no headings. The application renders your reply as it is.
        - When you list properties, number them 1., 2., 3. in the same order the tool returned them, and give each one its own line.

        Keep replies short. Two or three sentences, then the properties.""";

    private static final String DEFAULT_REASON = "Matches your search criteria.";

    // "1. text", "2) text", with optional leading bullet or emphasis markers.
    private static final Pattern NUMBERED_ITEM =
        Pattern.compile("^[-*\\s]*\\d+[.)]\\s*(.+)$");

    private final PropertyService propertyService;
    private final ChatClient chatClient;

    public AssistantService(PropertyService propertyService) {
        this(propertyService, new ChatClient());
    }

    public AssistantService(PropertyService propertyService, ChatClient chatClient) {
        this.propertyService = propertyService;
        this.chatClient = chatClient;
    }

    public static class Response {
        private final String messageText;
        private final List<Suggestion> suggestions;
        private final PropertySearch updatedFilters;

        public Response(String messageText, List<Suggestion> suggestions, PropertySearch updatedFilters) {
            this.messageText = messageText;
            this.suggestions = suggestions != null ? suggestions : List.of();
            this.updatedFilters = updatedFilters;
        }

        public String getMessageText() {
            return messageText;
        }

        public List<Suggestion> getSuggestions() {
            return suggestions;
        }

        public PropertySearch getUpdatedFilters() {
            return updatedFilters;
        }
    }

    public Response respond(Conversation conversation, PropertySearch carriedFilters,
                            List<District> districts, List<PropertyType> propertyTypes)
            throws AssistantUnavailableException, SQLException {
        conversation.ensureSystem(ChatMessage.system(SYSTEM_PROMPT));

        AgentTools tools = new AgentTools(propertyService, districts, propertyTypes);
        if (carriedFilters != null) {
            tools.setLastAppliedFilters(carriedFilters);
        }

        int round = 0;
        ChatMessage responseMessage = null;

        while (round < 3) {
            responseMessage = chatClient.complete(conversation.messages(), true);
            List<ToolCall> toolCalls = responseMessage.getToolCalls();

            if (toolCalls == null || toolCalls.isEmpty()) {
                conversation.append(responseMessage);
                List<Suggestion> suggestions = extractSuggestions(tools.getLastRankedResults(), responseMessage.getContent());
                return new Response(stripEmphasis(responseMessage.getContent()), suggestions,
                    tools.getLastAppliedFilters());
            }

            conversation.append(responseMessage);
            round++;

            for (ToolCall toolCall : toolCalls) {
                String toolName = toolCall.getFunction() != null ? toolCall.getFunction().getName() : "";
                String arguments = toolCall.getFunction() != null ? toolCall.getFunction().getArguments() : "{}";
                String toolResultJson;
                try {
                    toolResultJson = tools.execute(toolName, arguments);
                } catch (RuntimeException e) {
                    // Malformed arguments are the model's problem to correct, so they
                    // go back as a tool result and it gets another round. A SQLException
                    // is not caught here: the database being down is the user's problem
                    // and belongs in an alert, not in the conversation.
                    toolResultJson = toolError(
                        "Could not read the tool arguments. Check them and try again.");
                }
                conversation.append(ChatMessage.tool(toolCall.getId(), toolResultJson));
            }
        }

        ChatMessage finalResponse = chatClient.complete(conversation.messages(), false);
        conversation.append(finalResponse);
        List<Suggestion> suggestions = extractSuggestions(tools.getLastRankedResults(), finalResponse.getContent());
        return new Response(stripEmphasis(finalResponse.getContent()), suggestions,
            tools.getLastAppliedFilters());
    }

    private String toolError(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        return error.toString();
    }

    private List<Suggestion> extractSuggestions(List<Property> rankedResults, String assistantText) {
        if (rankedResults == null || rankedResults.isEmpty()) {
            return List.of();
        }
        List<String> reasons = numberedLines(assistantText);

        List<Suggestion> suggestions = new ArrayList<>();
        for (int i = 0; i < rankedResults.size(); i++) {
            String reason = i < reasons.size() ? reasons.get(i) : DEFAULT_REASON;
            suggestions.add(new Suggestion(rankedResults.get(i), reason));
        }
        return suggestions;
    }

    // The reply is matched to properties by position rather than by title,
    // because titles repeat - two warehouses in Saida are both called
    // "Warehouse in Saida", and matching on the text gave them the same reason.
    // The prompt asks for a numbered list in tool order, so the nth item
    // describes the nth property.
    List<String> numberedLines(String assistantText) {
        List<String> reasons = new ArrayList<>();
        if (assistantText == null || assistantText.isBlank()) {
            return reasons;
        }
        for (String line : assistantText.split("\r?\n")) {
            Matcher matcher = NUMBERED_ITEM.matcher(line.trim());
            if (matcher.matches()) {
                String reason = stripEmphasis(matcher.group(1)).trim();
                if (!reason.isBlank()) {
                    reasons.add(reason);
                }
            }
        }
        return reasons;
    }

    // A model told to write plain text still slips in the occasional asterisk,
    // and a JavaFX Label renders those literally.
    String stripEmphasis(String text) {
        if (text == null) {
            return null;
        }
        return text.replace("**", "").replace("__", "").trim();
    }
}
