package co.syntropyhq.aqarat.ai;

import co.syntropyhq.aqarat.model.Property;

public class Suggestion {

    private final Property property;
    private final String reason;

    public Suggestion(Property property, String reason) {
        this.property = property;
        this.reason = reason;
    }

    public Property getProperty() {
        return property;
    }

    public String getReason() {
        return reason;
    }
}
