package co.syntropyhq.aqarat.util;

public enum Panel {

    BROWSE_LISTINGS("BrowseListings.fxml"),
    PROPERTY_DETAILS("PropertyDetails.fxml"),
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
