package co.syntropyhq.aqarat.util;

public enum Panel {

    /* Discover replaces the separate Browse Listings and Assistant panels:
       they were two entrances to the same catalogue. */
    DISCOVER("Discover.fxml"),
    PROPERTY_DETAILS("PropertyDetails.fxml"),
    /* Portfolio replaces My properties, My contracts and My activity. Those
       three split one person's own business along database table boundaries. */
    PORTFOLIO("Portfolio.fxml"),
    MY_PROPERTIES("MyProperties.fxml"),
    SUBMIT_PROPERTY("SubmitProperty.fxml"),
    MY_CONTRACTS("MyContracts.fxml"),
    MY_ACTIVITY("MyActivity.fxml"),
    AGENT_DASHBOARD("AgentDashboard.fxml"),
    REVIEW_QUEUE("ReviewQueue.fxml"),
    REVIEW_SUBMISSION("ReviewSubmission.fxml"),
    LISTINGS("Listings.fxml"),
    VIEWINGS("Viewings.fxml"),
    CONTRACTS("Contracts.fxml"),
    PAYMENTS("Payments.fxml"),
    USERS("Users.fxml"),
    REFERENCE("Reference.fxml"),
    AUDIT_LOG("AuditLog.fxml"),
    REPORTS("Reports.fxml");

    private final String fxml;

    Panel(String fxml) {
        this.fxml = fxml;
    }

    public String getFxml() {
        return fxml;
    }
}
