package co.syntropyhq.aqarat.ai;

import co.syntropyhq.aqarat.util.Config;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;

public class ChatClient {

    private final HttpClient httpClient;
    private final Gson gson;

    public ChatClient() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.gson = new GsonBuilder().disableHtmlEscaping().create();
    }

    public ChatClient(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.gson = new GsonBuilder().disableHtmlEscaping().create();
    }

    public static boolean isEnabled() {
        String enabled = Config.get("ai.enabled", "false");
        if (!"true".equalsIgnoreCase(enabled.trim())) {
            return false;
        }
        String apiKey = Config.get("ai.apiKey");
        return apiKey != null && !apiKey.isBlank() && !"CHANGE_ME".equals(apiKey.trim());
    }

    public ChatMessage complete(List<ChatMessage> messages, boolean offerTools)
            throws AssistantUnavailableException {
        if (!isEnabled()) {
            throw new AssistantUnavailableException("AI assistant is disabled or not configured.");
        }

        String baseUrl = Config.get("ai.baseUrl", "https://api.openai.com/v1").trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String apiKey = Config.get("ai.apiKey", "").trim();
        String model = Config.get("ai.model", "gpt-4o-mini").trim();
        int timeoutMs = 30000;
        try {
            timeoutMs = Integer.parseInt(Config.get("ai.timeoutMs", "30000").trim());
        } catch (NumberFormatException ignored) {
        }

        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("model", model);

        JsonArray messagesArray = new JsonArray();
        for (ChatMessage msg : messages) {
            JsonObject msgObj = new JsonObject();
            msgObj.addProperty("role", msg.getRole());
            if (msg.getContent() != null) {
                msgObj.addProperty("content", msg.getContent());
            } else {
                msgObj.add("content", null);
            }
            if (msg.getToolCallId() != null) {
                msgObj.addProperty("tool_call_id", msg.getToolCallId());
            }
            if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                msgObj.add("tool_calls", gson.toJsonTree(msg.getToolCalls()));
            }
            messagesArray.add(msgObj);
        }
        requestJson.add("messages", messagesArray);

        if (offerTools) {
            JsonArray toolsArray = new JsonArray();
            for (JsonObject schema : AgentTools.getToolSchemas()) {
                toolsArray.add(schema);
            }
            requestJson.add("tools", toolsArray);
        }

        String requestBody = gson.toJson(requestJson);
        String endpoint = baseUrl + "/chat/completions";

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofMillis(timeoutMs))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        } catch (IllegalArgumentException e) {
            throw new AssistantUnavailableException("Invalid API configuration URL: " + endpoint, e);
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new AssistantUnavailableException("AI assistant request timed out.", e);
        } catch (IOException e) {
            throw new AssistantUnavailableException("Network error connecting to AI assistant.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssistantUnavailableException("AI assistant request was interrupted.", e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new AssistantUnavailableException(
                "AI service returned HTTP status " + response.statusCode() + ".");
        }

        return parseResponse(response.body());
    }

    /**
     * Books the completion's token usage.
     *
     * <p>Every OpenAI-compatible provider returns this block, but it is optional
     * and a local runtime may omit it, so a missing block is skipped rather than
     * treated as a failure — the answer is worth having, not worth failing a
     * search over.
     */
    private void recordUsage(JsonObject root) {
        JsonObject usage = root.getAsJsonObject("usage");
        if (usage == null) {
            return;
        }
        long prompt = usage.has("prompt_tokens") ? usage.get("prompt_tokens").getAsLong() : 0;
        long completion = usage.has("completion_tokens")
            ? usage.get("completion_tokens").getAsLong() : 0;
        TokenLedger.record(prompt, completion);
        System.out.println("Aqarat assistant: " + prompt + " prompt + " + completion
            + " completion tokens. " + TokenLedger.summary());
    }

    private ChatMessage parseResponse(String responseBody) throws AssistantUnavailableException {
        try {
            JsonElement parsed = JsonParser.parseString(responseBody);
            if (!parsed.isJsonObject()) {
                throw new AssistantUnavailableException("Response is not a valid JSON object.");
            }
            JsonObject root = parsed.getAsJsonObject();
            recordUsage(root);
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new AssistantUnavailableException("Chat completion response returned no choices.");
            }

            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject messageObj = choice.getAsJsonObject("message");
            if (messageObj == null) {
                throw new AssistantUnavailableException("Missing message object in choice.");
            }

            ChatMessage message = gson.fromJson(messageObj, ChatMessage.class);
            if (message == null) {
                throw new AssistantUnavailableException("Could not deserialize assistant message.");
            }
            return message;
        } catch (AssistantUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new AssistantUnavailableException("Could not parse AI response body.", e);
        }
    }
}
