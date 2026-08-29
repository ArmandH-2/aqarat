package co.syntropyhq.aqarat.ai;

import com.google.gson.annotations.SerializedName;

public class ToolCall {

    private String id;
    private String type = "function";

    @SerializedName("function")
    private FunctionCall function;

    public ToolCall() {
    }

    public ToolCall(String id, String type, FunctionCall function) {
        this.id = id;
        this.type = type;
        this.function = function;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public FunctionCall getFunction() {
        return function;
    }

    public void setFunction(FunctionCall function) {
        this.function = function;
    }

    public static class FunctionCall {
        private String name;
        private String arguments;

        public FunctionCall() {
        }

        public FunctionCall(String name, String arguments) {
            this.name = name;
            this.arguments = arguments;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getArguments() {
            return arguments;
        }

        public void setArguments(String arguments) {
            this.arguments = arguments;
        }
    }
}
