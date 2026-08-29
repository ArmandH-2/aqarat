package co.syntropyhq.aqarat.ai;

public class AssistantUnavailableException extends Exception {

    public AssistantUnavailableException(String message) {
        super(message);
    }

    public AssistantUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
