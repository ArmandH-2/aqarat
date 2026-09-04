package co.syntropyhq.aqarat.controller;

import co.syntropyhq.aqarat.dao.AuditDao;
import co.syntropyhq.aqarat.dao.ContractDao;
import co.syntropyhq.aqarat.dao.DistrictDao;
import co.syntropyhq.aqarat.dao.PropertyDao;
import co.syntropyhq.aqarat.dao.PropertyDocumentDao;
import co.syntropyhq.aqarat.dao.PropertyPhotoDao;
import co.syntropyhq.aqarat.dao.PropertyTypeDao;
import co.syntropyhq.aqarat.dao.ReservationDao;
import co.syntropyhq.aqarat.dao.UserDao;
import co.syntropyhq.aqarat.dao.ValuationDao;
import co.syntropyhq.aqarat.dao.ViewingDao;
import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.AuditLog;
import co.syntropyhq.aqarat.model.Contract;
import co.syntropyhq.aqarat.model.DealType;
import co.syntropyhq.aqarat.model.District;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.PropertyDocument;
import co.syntropyhq.aqarat.model.PropertyDossier;
import co.syntropyhq.aqarat.model.PropertyType;
import co.syntropyhq.aqarat.model.Reservation;
import co.syntropyhq.aqarat.model.Valuation;
import co.syntropyhq.aqarat.model.Viewing;
import co.syntropyhq.aqarat.service.AuditService;
import co.syntropyhq.aqarat.service.DocumentService;
import co.syntropyhq.aqarat.service.DossierService;
import co.syntropyhq.aqarat.service.ReferenceService;
import co.syntropyhq.aqarat.util.AlertUtil;
import co.syntropyhq.aqarat.util.Format;
import co.syntropyhq.aqarat.util.NeedsId;
import co.syntropyhq.aqarat.util.Router;
import co.syntropyhq.aqarat.util.SessionManager;
import co.syntropyhq.aqarat.util.Toast;
import co.syntropyhq.aqarat.util.UIHelper;
import co.syntropyhq.aqarat.util.Uploads;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The property file: one property, everything recorded about it, for staff.
 *
 * <p>The panel loads on a background thread. It fires around a dozen queries
 * where a listing screen fires two, and a screen that freezes while it thinks
 * is the one thing this application has been careful not to have.
 *
 * <p>CLAUDE.md's ordering rule applies here: {@code initialize} runs before
 * {@code receiveId}, so nothing is loaded there.
 */
public class PropertyDossierController implements NeedsId {

    @FXML private VBox loadingBox;
    @FXML private VBox contentBox;
    @FXML private Label referenceLabel;
    @FXML private Label districtLabel;
    @FXML private Label titleLabel;
    @FXML private HBox statusSlot;
    @FXML private FlowPane identityStrip;
    @FXML private VBox lifecycleSlot;
    @FXML private FlowPane counterStrip;
    @FXML private Label evidenceHint;
    @FXML private VBox documentList;
    @FXML private VBox valuationList;
    @FXML private VBox viewingList;
    @FXML private VBox reservationList;
    @FXML private VBox contractList;
    @FXML private Label timelineHint;
    @FXML private VBox timelineList;

    private final DossierService dossierService = new DossierService(
        new PropertyDao(), new PropertyDocumentDao(), new PropertyPhotoDao(), new ValuationDao(),
        new ViewingDao(), new ReservationDao(), new ContractDao(), new AuditDao(), new UserDao());
    private final DocumentService documentService = new DocumentService(
        new PropertyDocumentDao(), new PropertyDao(), new AuditService(new AuditDao()));
    private final ReferenceService referenceService =
        new ReferenceService(new DistrictDao(), new PropertyTypeDao());

    private int propertyId;

    @Override
    public void receiveId(int id) {
        this.propertyId = id;
        load();
    }

    @FXML
    private void handleBack() {
        Router.back();
    }

    private void load() {
        showLoading(true);
        Task<Loaded> task = new Task<>() {
            @Override
            protected Loaded call() throws SQLException, DossierService.NotPermittedException {
                AppUser viewer = SessionManager.getCurrentUser();
                PropertyDossier dossier = dossierService.load(propertyId, viewer);
                Property property = dossier.getProperty();
                return new Loaded(dossier,
                    referenceService.findDistrict(property.getDistrictId()),
                    referenceService.findPropertyType(property.getPropertyTypeId()));
            }
        };
        task.setOnSucceeded(event -> {
            render(task.getValue());
            showLoading(false);
        });
        task.setOnFailed(event -> {
            showLoading(false);
            Throwable cause = task.getException();
            String message = cause instanceof DossierService.NotPermittedException
                ? cause.getMessage()
                : "The property file could not be opened.";
            AlertUtil.showError(message);
            Router.back();
        });
        Thread worker = new Thread(task, "property-dossier");
        worker.setDaemon(true);
        worker.start();
    }

    private void showLoading(boolean loading) {
        loadingBox.setVisible(loading);
        loadingBox.setManaged(loading);
        contentBox.setVisible(!loading);
        contentBox.setManaged(!loading);
    }

    private void render(Loaded loaded) {
        PropertyDossier dossier = loaded.dossier;
        Property property = dossier.getProperty();

        referenceLabel.setText("PROPERTY #" + property.getId());
        districtLabel.setText(loaded.district == null ? ""
            : "· " + loaded.district.getName().toUpperCase() + ", "
                + loaded.district.getGovernorate().toUpperCase());
        titleLabel.setText(property.getTitle());
        statusSlot.getChildren().setAll(UIHelper.createStatusPill(property.getStatus()));
        lifecycleSlot.getChildren().setAll(UIHelper.createLifecycleBar(property.getStatus()));

        renderIdentity(dossier, loaded.propertyType);
        renderCounters(dossier);
        renderDocuments(dossier);
        renderValuations(dossier);
        renderViewings(dossier);
        renderReservations(dossier);
        renderContracts(dossier);
        renderTimeline(dossier);
    }

    private void renderIdentity(PropertyDossier dossier, PropertyType propertyType) {
        Property property = dossier.getProperty();
        identityStrip.getChildren().setAll(
            fact("OWNER", dossier.nameOf(property.getOwnerId())),
            fact("ASSIGNED AGENT", dossier.nameOf(property.getAgentId())),
            fact("TYPE", propertyType == null ? "—" : propertyType.getName()),
            fact("DEAL", property.getDealType() == DealType.SALE ? "Sale" : "Rent"),
            fact("ASKING", property.getDealType() == DealType.SALE
                ? Format.salePrice(property.getAskingPrice())
                : Format.monthlyRent(property.getAskingPrice())),
            fact("SUBMITTED", Format.dateTime(property.getSubmittedAt())),
            fact("PUBLISHED", Format.dateTime(property.getPublishedAt())));
    }

    private void renderCounters(PropertyDossier dossier) {
        int documents = dossier.getDocuments().size();
        long verified = dossier.getDocuments().stream().filter(PropertyDocument::isVerified).count();
        // Evidence is the one figure here that is good or bad rather than
        // merely a count, so it is the one that carries a tone.
        String evidenceTone = verified > 0 ? "-c-good" : "-c-warn";
        counterStrip.getChildren().setAll(
            counter(dossier.getPhotoCount(), "Photos", null, null),
            counter(documents, "Documents",
                verified > 0 ? verified + " verified" : "None verified yet", evidenceTone),
            counter(dossier.getViewings().size(), "Viewings", null, null),
            counter(dossier.getReservations().size(), "Reservations", null, null),
            counter(dossier.getContracts().size(), "Contracts", null, null),
            counter(dossier.getValuations().size(), "Valuations", null, null));
    }

    private void renderDocuments(PropertyDossier dossier) {
        List<PropertyDocument> documents = dossier.getDocuments();
        long verified = documents.stream().filter(PropertyDocument::isVerified).count();
        evidenceHint.setText(verified > 0
            ? "Ownership verified"
            : "Nothing verified yet — this listing cannot be published");
        if (documents.isEmpty()) {
            documentList.getChildren().setAll(UIHelper.createEmptyState(
                "No documents uploaded",
                "The owner attaches proof of ownership when they submit, or afterwards "
                    + "from their own properties screen."));
            return;
        }
        documentList.getChildren().clear();
        for (PropertyDocument document : documents) {
            documentList.getChildren().add(documentRow(dossier, document));
        }
    }

    private Node documentRow(PropertyDossier dossier, PropertyDocument document) {
        // Who checked it and when belongs on the detail line with the rest of
        // the provenance. A pill is a status badge - at 10.5px it cannot carry
        // a sentence, and a whole date inside one dwarfs the row it labels.
        String provenance = document.getOriginalName() + " · uploaded by "
            + dossier.nameOf(document.getUploadedBy()) + " on "
            + Format.dateTime(document.getUploadedAt());
        if (document.isVerified()) {
            provenance = provenance + " · verified by " + document.getVerifierName()
                + " on " + Format.dateTime(document.getVerifiedAt());
        }
        VBox text = new VBox(3,
            strong(Format.enumLabel(document.getDocType())),
            quiet(provenance));
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox row = new HBox(12, text);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("card-subtle");

        if (document.isVerified()) {
            row.getChildren().add(UIHelper.createPill("Verified", "pill-good"));
        } else {
            Button verify = new Button("Verify");
            verify.getStyleClass().addAll("button", "button-secondary", "button-compact");
            verify.setOnAction(event -> verify(document));
            row.getChildren().addAll(UIHelper.createPill("Not verified", "pill-warn"), verify);
        }

        Button open = new Button("Open");
        open.getStyleClass().addAll("button", "button-ghost", "button-compact");
        open.setOnAction(event -> open(document));
        row.getChildren().add(open);
        return row;
    }

    private void verify(PropertyDocument document) {
        try {
            documentService.verify(document.getId(), SessionManager.getCurrentUser());
            Toast.done("Document verified",
                Format.enumLabel(document.getDocType()) + " on property #" + propertyId);
            load();
        } catch (DocumentService.NotPermittedException
                | DocumentService.AlreadyVerifiedException e) {
            AlertUtil.showUndone(e.getMessage());
            load();
        } catch (SQLException e) {
            AlertUtil.showError("The verification could not be saved.", e.getMessage());
        }
    }

    /**
     * Hands the file to whatever the operating system opens it with.
     *
     * <p>Off the UI thread: on Windows the first call to {@code Desktop.open}
     * waits for the handler to start, which is long enough to freeze a window
     * visibly.
     */
    private void open(PropertyDocument document) {
        Path path = Uploads.resolve(document.getFilePath());
        File file = path.toFile();
        if (!file.exists()) {
            AlertUtil.showUndone("That file is no longer in the uploads folder.",
                document.getFilePath());
            return;
        }
        if (!Desktop.isDesktopSupported()) {
            AlertUtil.showUndone("This system cannot open files from the application.",
                file.getAbsolutePath());
            return;
        }
        Thread opener = new Thread(() -> {
            try {
                Desktop.getDesktop().open(file);
            } catch (IOException | UnsupportedOperationException e) {
                Platform.runLater(() -> AlertUtil.showUndone(
                    "Nothing on this computer is set up to open that file.",
                    file.getAbsolutePath()));
            }
        }, "open-document");
        opener.setDaemon(true);
        opener.start();
    }

    private void renderValuations(PropertyDossier dossier) {
        if (dossier.getValuations().isEmpty()) {
            valuationList.getChildren().setAll(UIHelper.createEmptyState(
                "No valuation recorded",
                "An estimate is produced when the property is submitted for review."));
            return;
        }
        valuationList.getChildren().clear();
        for (Valuation valuation : dossier.getValuations()) {
            valuationList.getChildren().add(record(
                Format.salePrice(valuation.getEstimatedValue()),
                Format.salePrice(valuation.getLowerBound()) + " to "
                    + Format.salePrice(valuation.getUpperBound()) + " · "
                    + Format.pricePerSqm(valuation.getPricePerSqm()) + " · "
                    + Format.dateTime(valuation.getCreatedAt()),
                Format.enumLabel(valuation.getFlag()),
                switch (valuation.getFlag()) {
                    case OK -> "pill-good";
                    case ABOVE_MARKET -> "pill-warn";
                    case IMPLAUSIBLE -> "pill-bad";
                }));
        }
    }

    private void renderViewings(PropertyDossier dossier) {
        if (dossier.getViewings().isEmpty()) {
            viewingList.getChildren().setAll(UIHelper.createEmptyState(
                "Nobody has asked to visit yet",
                "Viewing requests from clients appear here as they arrive."));
            return;
        }
        viewingList.getChildren().clear();
        for (Viewing viewing : dossier.getViewings()) {
            String detail = "Agent " + dossier.nameOf(viewing.getAgentId())
                + " · requested " + Format.dateTime(viewing.getCreatedAt());
            if (viewing.getOutcomeNote() != null && !viewing.getOutcomeNote().isBlank()) {
                detail = detail + " · " + viewing.getOutcomeNote();
            }
            viewingList.getChildren().add(record(
                dossier.nameOf(viewing.getClientId()) + " · "
                    + Format.dateTime(viewing.getScheduledAt()),
                detail,
                Format.enumLabel(viewing.getStatus()),
                switch (viewing.getStatus()) {
                    case COMPLETED -> "pill-good";
                    case CONFIRMED -> "pill-info";
                    case REQUESTED -> "pill-neutral";
                    case CANCELLED, NO_SHOW -> "pill-bad";
                }));
        }
    }

    private void renderReservations(PropertyDossier dossier) {
        if (dossier.getReservations().isEmpty()) {
            reservationList.getChildren().setAll(UIHelper.createEmptyState(
                "No deposit has been taken",
                "A reservation holds the property for one client until it expires."));
            return;
        }
        reservationList.getChildren().clear();
        for (Reservation reservation : dossier.getReservations()) {
            reservationList.getChildren().add(record(
                Format.paymentAmount(reservation.getDepositAmount()) + " · "
                    + dossier.nameOf(reservation.getClientId()),
                "Taken " + Format.dateTime(reservation.getReservedAt())
                    + " · expires " + Format.dateTime(reservation.getExpiresAt()),
                Format.enumLabel(reservation.getStatus()),
                switch (reservation.getStatus()) {
                    case ACTIVE -> "pill-info";
                    case CONVERTED -> "pill-good";
                    case LAPSED, CANCELLED -> "pill-neutral";
                }));
        }
    }

    private void renderContracts(PropertyDossier dossier) {
        if (dossier.getContracts().isEmpty()) {
            contractList.getChildren().setAll(UIHelper.createEmptyState(
                "No contract on this property",
                "A sale or lease drafted against it will appear here."));
            return;
        }
        contractList.getChildren().clear();
        for (Contract contract : dossier.getContracts()) {
            String detail = "Client " + dossier.nameOf(contract.getClientId())
                + " · agent " + dossier.nameOf(contract.getAgentId())
                + " · from " + Format.date(contract.getStartDate());
            if (contract.getCommissionAmount() != null) {
                detail = detail + " · commission "
                    + Format.paymentAmount(contract.getCommissionAmount());
            }
            contractList.getChildren().add(record(
                Format.enumLabel(contract.getContractType()) + " · "
                    + Format.salePrice(contract.getTotalAmount()),
                detail,
                Format.enumLabel(contract.getStatus()),
                switch (contract.getStatus()) {
                    case ACTIVE -> "pill-info";
                    case COMPLETED -> "pill-good";
                    case DRAFT -> "pill-neutral";
                    case TERMINATED, EXPIRED -> "pill-bad";
                }));
        }
    }

    private void renderTimeline(PropertyDossier dossier) {
        List<AuditLog> entries = dossier.getTimeline();
        timelineHint.setText(entries.size() + (entries.size() == 1 ? " entry" : " entries"));
        if (entries.isEmpty()) {
            timelineList.getChildren().setAll(UIHelper.createEmptyState(
                "Nothing recorded yet",
                "Every change to this property and to anything attached to it lands here."));
            return;
        }
        timelineList.getChildren().clear();
        for (AuditLog entry : entries) {
            timelineList.getChildren().add(timelineRow(dossier, entry));
        }
    }

    private Node timelineRow(PropertyDossier dossier, AuditLog entry) {
        String change = entry.getOldValue() == null
            ? Format.valueLabel(entry.getNewValue())
            : Format.valueLabel(entry.getOldValue()) + " → " + Format.valueLabel(entry.getNewValue());

        Label when = quiet(Format.dateTime(entry.getCreatedAt()));
        when.setMinWidth(140);

        VBox text = new VBox(3,
            strong(Format.constantLabel(entry.getAction())
                + " · " + Format.constantLabel(entry.getEntityType())
                + " #" + entry.getEntityId()),
            quiet(dossier.nameOf(entry.getUserId()) + (change.isBlank() ? "" : " · " + change)));
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox row = new HBox(14, when, text);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("dash-row");
        return row;
    }

    /* ---------------- small builders ---------------- */

    private Node fact(String label, String value) {
        Label name = new Label(label);
        name.getStyleClass().add("spec-strip-label");
        Label figure = new Label(value == null || value.isBlank() ? "—" : value);
        figure.getStyleClass().add("spec-strip-value");
        figure.setWrapText(true);
        return new VBox(4, name, figure);
    }

    // The house stat tile, as AgentDashboard and MyProperties build it: the
    // figure first at display size, its name under it. Inverting that made the
    // dossier's tiles read as captions with a number attached.
    private Node counter(int value, String label, String note, String tone) {
        Label figure = metric(String.valueOf(value));
        if (tone != null) {
            figure.setStyle("-fx-text-fill: " + tone + ";");
        }
        VBox tile = new VBox(4, figure, quiet(label));
        if (note != null) {
            tile.getChildren().add(quiet(note));
        }
        tile.getStyleClass().add("stat-tile");
        // One width for all six. A FlowPane sizes each child to its content,
        // which left the row looking ragged for no reason a reader could see.
        tile.setMinWidth(172);
        tile.setPrefWidth(172);
        return tile;
    }

    private Node record(String headline, String detail, String pillText, String pillTone) {
        VBox text = new VBox(3, strong(headline), quiet(detail));
        HBox.setHgrow(text, Priority.ALWAYS);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(12, text, spacer, UIHelper.createPill(pillText, pillTone));
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("card-subtle");
        return row;
    }

    private Label strong(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("body-medium");
        label.setWrapText(true);
        return label;
    }

    private Label quiet(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("hint");
        label.setWrapText(true);
        return label;
    }

    private Label metric(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("metric-value");
        return label;
    }

    private record Loaded(PropertyDossier dossier, District district, PropertyType propertyType) {
    }
}
