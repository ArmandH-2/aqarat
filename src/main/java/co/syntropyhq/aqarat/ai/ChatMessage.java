package co.syntropyhq.aqarat.ai;

import com.google.gson.annotations.SerializedName;
import java.util.List;

// Role, content, and the optional toolCallId and toolCalls a tool-calling
// exchange needs. A plain holder, no behaviour, matching the model package style.
public class ChatMessage {

    private String role;
    private String content;

    @SerializedName("tool_call_id")
    private String toolCallId;

    @SerializedName("tool_calls")
    private List<ToolCall> toolCalls;

    public ChatMessage() {
    }

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public ChatMessage(String role, String content, String toolCallId, List<ToolCall> toolCalls) {
        this.role = role;
        this.content = content;
        this.toolCallId = toolCallId;
        this.toolCalls = toolCalls;
    }

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }

    public static ChatMessage assistantToolCalls(List<ToolCall> toolCalls) {
        ChatMessage msg = new ChatMessage("assistant", null);
        msg.setToolCalls(toolCalls);
        return msg;
    }

    public static ChatMessage tool(String toolCallId, String content) {
        ChatMessage msg = new ChatMessage("tool", content);
        msg.setToolCallId(toolCallId);
        return msg;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }
}
