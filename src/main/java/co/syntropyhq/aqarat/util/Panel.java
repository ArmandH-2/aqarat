package co.syntropyhq.aqarat.util;

public enum Panel {

    /* Discover replaces the separate Browse Listings and Assistant panels:
       they were two entrances to the same catalogue. */
    DISCOVER("Discover.fxml"),
    PROPERTY_DETAILS("PropertyDetails.fxml"),
    /* The staff-only file on one property: evidence, activity and history. */
    PROPERTY_DOSSIER("PropertyDossier.fxml"),
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

    /**
     * The Ikonli Feather literal for this panel's navigation icon.
     *
     * <p>Kept beside the FXML name so a new panel cannot be added without
     * deciding how it is recognised in the sidebar. Returned as a literal
     * rather than an {@code Ikon} so this enum stays free of a UI dependency.
     */
    public String getIconLiteral() {
        return switch (this) {
            case DISCOVER -> "fth-search";
            case PROPERTY_DETAILS -> "fth-home";
            case PROPERTY_DOSSIER -> "fth-folder";
            case PORTFOLIO, MY_PROPERTIES -> "fth-briefcase";
            case SUBMIT_PROPERTY -> "fth-plus-circle";
            case MY_CONTRACTS -> "fth-file-text";
            case MY_ACTIVITY -> "fth-clock";
            case AGENT_DASHBOARD -> "fth-grid";
            case REVIEW_QUEUE -> "fth-inbox";
            case REVIEW_SUBMISSION -> "fth-check-square";
            case LISTINGS -> "fth-layers";
            case VIEWINGS -> "fth-calendar";
            case CONTRACTS -> "fth-file-text";
            case PAYMENTS -> "fth-dollar-sign";
            case USERS -> "fth-users";
            case REFERENCE -> "fth-database";
            case AUDIT_LOG -> "fth-shield";
            case REPORTS -> "fth-bar-chart-2";
        };
    }
}
