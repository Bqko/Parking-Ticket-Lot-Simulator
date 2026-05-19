package com.parking.ui;

import com.parking.model.Ticket;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

public class PaymentScreen {

    private final ParkingApp app;

    private TextField ticketIdField;
    private TextField amountField;
    private Label     feeDisplay;
    private Label     durationLabel;  // shows "2h 15m parked"
    private Label     feeSub;         // "estimated fee — updates every second"
    private Label     statusLabel;
    private VBox      receiptCard;
    private Button    payBtn;
    private Button    exitBtn;
    private Ticket    currentTicket;
    private boolean   lostTicketMode = false; // true when processing a lost ticket

    // Live fee update ticker
    private Timeline  feeTicker;

    public PaymentScreen(ParkingApp app) { this.app = app; }

    Node build() {
        ScrollPane sp = new ScrollPane();
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: " + ParkingApp.BG_BASE + "; -fx-background-color: " + ParkingApp.BG_BASE + "; -fx-border-color: transparent;");

        VBox page = new VBox(24);
        page.setPadding(new Insets(40, 48, 40, 48));
        page.setStyle("-fx-background-color: " + ParkingApp.BG_BASE + ";");

        // Page header
        HBox header = new HBox(14);
        header.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label("💳");
        icon.setFont(Font.font("System", 28));
        VBox titles = new VBox(2);
        Label title = ParkingApp.pageTitle("Payment & Exit");
        Label sub   = new Label("Look up a ticket, pay the fee, and release the exit gate.");
        sub.setFont(Font.font("System", 13));
        sub.setTextFill(Color.web(ParkingApp.TEXT_M));
        titles.getChildren().addAll(title, sub);
        header.getChildren().addAll(icon, titles);

        // Step cards row — grow equally
        HBox stepsRow = new HBox(20);
        stepsRow.setFillHeight(true);
        var stepsCard    = buildStepsCard();
        var receiptCard2 = buildReceiptCard();
        HBox.setHgrow(stepsCard,    Priority.ALWAYS);
        HBox.setHgrow(receiptCard2, Priority.ALWAYS);
        stepsCard.setMaxWidth(Double.MAX_VALUE);
        receiptCard2.setMaxWidth(Double.MAX_VALUE);
        stepsRow.getChildren().addAll(stepsCard, receiptCard2);

        page.getChildren().addAll(header, stepsRow);
        sp.setContent(page);

        // Stop ticker if user navigates away
        sp.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) stopTicker();
        });

        javafx.application.Platform.runLater(() ->
                Animations.staggerCards(60, 90, stepsCard, receiptCard2));

        return sp;
    }

    // ── Steps card (left) ─────────────────────────────────────────────────

    private VBox buildStepsCard() {
        VBox card = ParkingApp.pageCard(null);
        card.setSpacing(24);

        // Step 1
        card.getChildren().add(buildStep("1", "Look Up Ticket", ParkingApp.ACCENT));
        ticketIdField = ParkingApp.styledField("Enter Ticket ID  e.g. A3F92C1D4B");
        Button lookupBtn = ParkingApp.primaryBtn("Look Up  →", ParkingApp.ACCENT);
        lookupBtn.setOnAction(e -> { Animations.buttonPulse(lookupBtn); handleLookup(); });

        // Fee display — large number + live sub-label
        feeDisplay = new Label("—");
        feeDisplay.setFont(Font.font("Georgia", FontWeight.BOLD, 44));
        feeDisplay.setTextFill(Color.web(ParkingApp.WARNING));

        feeSub = new Label("look up a ticket to see the fee");
        feeSub.setFont(Font.font("System", 12));
        feeSub.setTextFill(Color.web(ParkingApp.TEXT_M));

        durationLabel = new Label("");
        durationLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
        durationLabel.setTextFill(Color.web(ParkingApp.ACCENT));
        durationLabel.setVisible(false);

        VBox feeBox = new VBox(4, feeDisplay, feeSub, durationLabel);

        card.getChildren().addAll(
                ParkingApp.fieldGroup("TICKET ID", ticketIdField),
                lookupBtn, feeBox,
                makeDivider()
        );

        // Lost ticket shortcut
        HBox lostRow = new HBox(10);
        lostRow.setAlignment(Pos.CENTER_LEFT);
        Label lostIcon = new Label("🎫");
        lostIcon.setFont(Font.font("System", 14));
        Label lostLbl = new Label("Customer lost their ticket?");
        lostLbl.setFont(Font.font("System", 12));
        lostLbl.setTextFill(Color.web(ParkingApp.TEXT_M));
        Region lostSpacer = new Region();
        HBox.setHgrow(lostSpacer, Priority.ALWAYS);
        Button lostBtn = new Button("Lost Ticket");
        lostBtn.setFont(Font.font("System", FontWeight.BOLD, 11));
        lostBtn.setTextFill(Color.web(ParkingApp.DANGER));
        lostBtn.setStyle(
                "-fx-background-color: " + ParkingApp.DANGER + "18;" +
                        "-fx-border-color: " + ParkingApp.DANGER + "44;" +
                        "-fx-border-radius: 8;" +
                        "-fx-background-radius: 8;" +
                        "-fx-padding: 5 12 5 12;" +
                        "-fx-cursor: hand;"
        );
        lostBtn.setOnAction(e -> { Animations.buttonPulse(lostBtn); handleLostTicket(); });
        lostRow.getChildren().addAll(lostIcon, lostLbl, lostSpacer, lostBtn);
        card.getChildren().addAll(lostRow, makeDivider());

        // Step 2
        card.getChildren().add(buildStep("2", "Process Payment", ParkingApp.SUCCESS));
        amountField = ParkingApp.styledField("Amount paid (USD)  e.g. 50.00");
        amountField.setDisable(true);
        payBtn = ParkingApp.primaryBtn("Confirm Payment  💳", ParkingApp.SUCCESS);
        payBtn.setDisable(true);
        payBtn.setOnAction(e -> { Animations.buttonPulse(payBtn); handlePayment(); });

        card.getChildren().addAll(
                ParkingApp.fieldGroup("AMOUNT PAID (USD)", amountField),
                payBtn,
                makeDivider()
        );

        // Step 3
        card.getChildren().add(buildStep("3", "Open Exit Gate", ParkingApp.INFO));
        exitBtn = ParkingApp.primaryBtn("Release Gate  🚦", ParkingApp.INFO);
        exitBtn.setDisable(true);
        exitBtn.setOnAction(e -> { Animations.buttonPulse(exitBtn); handleExit(); });
        card.getChildren().add(exitBtn);

        // Status
        statusLabel = new Label("");
        statusLabel.setFont(Font.font("System", 12));
        statusLabel.setWrapText(true);
        statusLabel.setVisible(false);
        statusLabel.setPadding(new Insets(10, 14, 10, 14));
        card.getChildren().add(statusLabel);

        return card;
    }

    // ── Receipt card (right) ──────────────────────────────────────────────

    private VBox buildReceiptCard() {
        receiptCard = ParkingApp.pageCard(null);
        receiptCard.setVisible(false);
        return receiptCard;
    }

    // ── Live fee ticker ───────────────────────────────────────────────────

    /**
     * Starts a Timeline that recalculates and displays the fee every second.
     * This ensures the customer always sees the REAL current fee, not a stale estimate.
     */
    private void startTicker() {
        stopTicker(); // safety — never run two at once
        feeTicker = new Timeline(new KeyFrame(Duration.seconds(1), e -> refreshLiveFee()));
        feeTicker.setCycleCount(Timeline.INDEFINITE);
        feeTicker.play();
    }

    private void stopTicker() {
        if (feeTicker != null) {
            feeTicker.stop();
            feeTicker = null;
        }
    }

    private void refreshLiveFee() {
        if (currentTicket == null) return;

        // Real-time fee from entry until RIGHT NOW
        double fee = ParkingApp.TICKET_MANAGER.previewFee(currentTicket.getTicketId());
        feeDisplay.setText(String.format("%.2f USD", fee));

        // How long the vehicle has been parked
        long totalMinutes = ChronoUnit.MINUTES.between(
                currentTicket.getIssuedAt(), LocalDateTime.now());
        long hours   = totalMinutes / 60;
        long minutes = totalMinutes % 60;

        String duration = hours > 0
                ? String.format("%dh %02dm parked", hours, minutes)
                : String.format("%dm parked", minutes);

        durationLabel.setText("⏱  " + duration);
        durationLabel.setVisible(true);
    }

    // ── Handlers ──────────────────────────────────────────────────────────

    private void handleLookup() {
        String id = ticketIdField.getText().trim().toUpperCase();
        if (id.isBlank()) { showStatus("Enter a ticket ID first.", ParkingApp.DANGER); return; }

        Optional<Ticket> opt = ParkingApp.TICKET_MANAGER.findTicket(id);
        if (opt.isEmpty()) { showStatus("✗  Ticket not found: " + id, ParkingApp.DANGER); return; }

        currentTicket = opt.get();
        switch (currentTicket.getStatus()) {
            case PAID   -> {
                showStatus("✓  Already paid. Proceed to exit.", ParkingApp.SUCCESS);
                exitBtn.setDisable(false);
                return;
            }
            case EXITED -> { showStatus("ℹ  This ticket is already closed.", ParkingApp.INFO); return; }
            default     -> {}
        }

        // Show vehicle info in sub-label
        feeSub.setText("updating every second  ·  " +
                currentTicket.getVehicle().getType().getDisplayName() + "  ·  " +
                currentTicket.getVehicle().getLicensePlate());

        amountField.setDisable(false);
        payBtn.setDisable(false);
        showStatus("✓  Ticket found. Fee updates live below.", ParkingApp.SUCCESS);

        // Immediately show fee then start live ticker
        refreshLiveFee();
        startTicker();
    }

    private void handlePayment() {
        if (currentTicket == null) { showStatus("Look up a ticket first.", ParkingApp.DANGER); return; }
        double amount;
        try { amount = Double.parseDouble(amountField.getText().trim()); }
        catch (NumberFormatException ex) { showStatus("✗  Invalid amount.", ParkingApp.DANGER); return; }

        try {
            if (lostTicketMode) {
                // Lost ticket — charge flat fee
                double flatFee = ParkingApp.TICKET_MANAGER.getFeeCalculator().getLostTicketFee();
                if (amount < flatFee) {
                    showStatus(String.format("✗  Insufficient. Flat fee is $%.2f", flatFee), ParkingApp.DANGER);
                    return;
                }
                double change = ParkingApp.TICKET_MANAGER.processLostTicket(currentTicket.getTicketId(), amount);
                feeDisplay.setText(String.format("$%.2f", flatFee));
                feeDisplay.setTextFill(Color.web(ParkingApp.SUCCESS));
                feeSub.setText("lost ticket fee charged");
                payBtn.setDisable(true);
                amountField.setDisable(true);
                exitBtn.setDisable(false);
                showStatus(String.format("✓  Charged $%.2f. Change: $%.2f. Proceed to exit.", flatFee, change), ParkingApp.SUCCESS);

            } else {
                // Normal payment — stop ticker, lock in fee at this exact moment
                stopTicker();
                double change = ParkingApp.TICKET_MANAGER.processPayment(currentTicket.getTicketId(), amount);
                populateReceipt(currentTicket, change);
                Animations.cardPop(receiptCard);
                feeDisplay.setText(String.format("%.2f USD", currentTicket.getFeeCharged()));
                feeDisplay.setTextFill(Color.web(ParkingApp.SUCCESS));
                feeSub.setText("fee locked in at payment time");
                durationLabel.setVisible(false);
                payBtn.setDisable(true);
                amountField.setDisable(true);
                exitBtn.setDisable(false);
                showStatus("✓  Payment accepted! Proceed to exit.", ParkingApp.SUCCESS);
            }

        } catch (IllegalArgumentException e) {
            showStatus("✗  " + e.getMessage(), ParkingApp.DANGER);
        }
    }

    private void handleExit() {
        if (currentTicket == null) return;
        try {
            // For lost tickets, ticket is already marked LOST so we just release the spot
            if (!lostTicketMode) {
                ParkingApp.TICKET_MANAGER.processExit(currentTicket.getTicketId());
            } else {
                // Release spot manually for lost ticket flow
                com.parking.model.ParkingLot.getInstance().releaseSpot(currentTicket.getSpot());
            }
            exitBtn.setDisable(true);
            showStatus("🚗  Gate open! Spot " + currentTicket.getSpot().getSpotId() + " is now free.", ParkingApp.SUCCESS);
            resetAll();
        } catch (IllegalStateException e) {
            showStatus("✗  " + e.getMessage(), ParkingApp.DANGER);
        }
    }

    private void handleLostTicket() {
        String id = ticketIdField.getText().trim().toUpperCase();
        if (id.isBlank()) {
            showStatus("✗  Enter the ticket ID first (if known), or ask the customer for their plate.", ParkingApp.DANGER);
            return;
        }

        Optional<Ticket> opt = ParkingApp.TICKET_MANAGER.findTicket(id);
        if (opt.isEmpty()) {
            showStatus("✗  Ticket not found: " + id, ParkingApp.DANGER);
            return;
        }

        currentTicket  = opt.get();
        lostTicketMode = true;
        stopTicker();

        double flatFee = ParkingApp.TICKET_MANAGER.getFeeCalculator().getLostTicketFee();

        // Show flat fee and warning
        feeDisplay.setText(String.format("%.2f USD", flatFee));
        feeDisplay.setTextFill(Color.web(ParkingApp.DANGER));
        feeSub.setText("🎫  Lost ticket flat fee applies");
        durationLabel.setVisible(false);

        // Unlock payment fields
        amountField.setDisable(false);
        payBtn.setDisable(false);
        payBtn.setText("Charge Lost Ticket Fee  🎫");

        // Show warning card in receipt area
        populateLostTicketWarning(currentTicket, flatFee);
        Animations.cardPop(receiptCard);

        showStatus("⚠  Lost ticket — flat fee of $" + String.format("%.2f", flatFee) + " will be charged.", ParkingApp.WARNING);
    }

    private void populateLostTicketWarning(Ticket t, double flatFee) {
        receiptCard.getChildren().clear();

        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label rtitle = new Label("Lost Ticket");
        rtitle.setFont(Font.font("Georgia", FontWeight.BOLD, 17));
        rtitle.setTextFill(Color.web(ParkingApp.TEXT_H));
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Label badge = ParkingApp.statusBadge("FLAT FEE", ParkingApp.DANGER);
        titleRow.getChildren().addAll(rtitle, sp, badge);

        Separator sep = new Separator();

        // Warning box
        VBox warnBox = new VBox(6);
        warnBox.setPadding(new Insets(14, 16, 14, 16));
        warnBox.setStyle(
                "-fx-background-color: " + ParkingApp.DANGER + "18;" +
                        "-fx-background-radius: 10;" +
                        "-fx-border-color: " + ParkingApp.DANGER + "44;" +
                        "-fx-border-radius: 10;" +
                        "-fx-border-width: 1;"
        );
        Label warnTitle = new Label("LOST TICKET FEE");
        warnTitle.setFont(Font.font("System", FontWeight.BOLD, 10));
        warnTitle.setTextFill(Color.web(ParkingApp.DANGER));
        Label warnAmt = new Label(String.format("$%.2f", flatFee));
        warnAmt.setFont(Font.font("Georgia", FontWeight.BOLD, 36));
        warnAmt.setTextFill(Color.web(ParkingApp.TEXT_H));
        Label warnNote = new Label("A flat fee is charged when the original ticket cannot be presented. This replaces the time-based fee.");
        warnNote.setFont(Font.font("System", 11));
        warnNote.setTextFill(Color.web(ParkingApp.TEXT_M));
        warnNote.setWrapText(true);
        warnBox.getChildren().addAll(warnTitle, warnAmt, warnNote);

        // Vehicle details
        VBox details = new VBox(0);
        details.setStyle("-fx-background-color: " + ParkingApp.BG_RAISED + "; -fx-background-radius: 10;");
        details.getChildren().addAll(
                receiptRow("Ticket ID",  t.getTicketId(),                          false),
                receiptRow("Plate",      t.getVehicle().getLicensePlate(),          true),
                receiptRow("Type",       t.getVehicle().getType().getDisplayName(), false),
                receiptRow("Spot",       t.getSpot().getSpotId(),                   true),
                receiptRow("Entry",      t.getIssuedAt().toLocalTime().toString(),  false)
        );

        receiptCard.getChildren().addAll(titleRow, sep, warnBox, details);
        receiptCard.setVisible(true);
    }

    // ── Shared reset ──────────────────────────────────────────────────────

    private void resetAll() {
        currentTicket  = null;
        lostTicketMode = false;
        stopTicker();
        ticketIdField.clear();
        amountField.clear();
        amountField.setDisable(true);
        payBtn.setDisable(true);
        payBtn.setText("Confirm Payment  💳");
        exitBtn.setDisable(true);
        feeDisplay.setText("—");
        feeDisplay.setTextFill(Color.web(ParkingApp.WARNING));
        feeSub.setText("look up a ticket to see the fee");
        durationLabel.setVisible(false);
        receiptCard.setVisible(false);
    }

    // ── Receipt builder ───────────────────────────────────────────────────

    private void populateReceipt(Ticket t, double change) {
        receiptCard.getChildren().clear();

        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label rtitle = new Label("Receipt");
        rtitle.setFont(Font.font("Georgia", FontWeight.BOLD, 17));
        rtitle.setTextFill(Color.web(ParkingApp.TEXT_H));
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Label badge = ParkingApp.statusBadge("PAID", ParkingApp.SUCCESS);
        titleRow.getChildren().addAll(rtitle, sp, badge);

        Separator sep = new Separator();

        // Amount highlight
        VBox amtBox = new VBox(4);
        amtBox.setPadding(new Insets(14, 16, 14, 16));
        amtBox.setStyle(
                "-fx-background-color: " + ParkingApp.SUCCESS + "18;" +
                        "-fx-background-radius: 10;" +
                        "-fx-border-color: " + ParkingApp.SUCCESS + "44;" +
                        "-fx-border-radius: 10;" +
                        "-fx-border-width: 1;"
        );
        Label amtLbl = new Label("TOTAL PAID");
        amtLbl.setFont(Font.font("System", FontWeight.BOLD, 10));
        amtLbl.setTextFill(Color.web(ParkingApp.SUCCESS));
        Label amtVal = new Label(String.format("%.2f USD", t.getAmountPaid()));
        amtVal.setFont(Font.font("Georgia", FontWeight.BOLD, 28));
        amtVal.setTextFill(Color.web(ParkingApp.TEXT_H));
        Label changeLbl = new Label(String.format("Change: %.2f USD", change));
        changeLbl.setFont(Font.font("System", 12));
        changeLbl.setTextFill(Color.web(ParkingApp.TEXT_M));
        amtBox.getChildren().addAll(amtLbl, amtVal, changeLbl);

        // Duration on receipt
        long totalMinutes = ChronoUnit.MINUTES.between(t.getIssuedAt(), LocalDateTime.now());
        long hours   = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        String durationStr = hours > 0
                ? String.format("%dh %02dm", hours, minutes)
                : String.format("%dm", minutes);

        VBox details = new VBox(0);
        details.setStyle("-fx-background-color: " + ParkingApp.BG_RAISED + "; -fx-background-radius: 10;");
        details.getChildren().addAll(
                receiptRow("Ticket ID",    t.getTicketId(),                          false),
                receiptRow("Plate",        t.getVehicle().getLicensePlate(),          true),
                receiptRow("Type",         t.getVehicle().getType().getDisplayName(), false),
                receiptRow("Spot",         t.getSpot().getSpotId(),                   true),
                receiptRow("Entry",        t.getIssuedAt().toLocalTime().toString(),  false),
                receiptRow("Duration",     durationStr,                               true),
                receiptRow("Fee (USD)",    String.format("%.2f", t.getFeeCharged()),  false)
        );

        receiptCard.getChildren().addAll(titleRow, sep, amtBox, details);
        receiptCard.setVisible(true);
    }

    private HBox receiptRow(String key, String value, boolean shaded) {
        HBox row = new HBox();
        row.setPadding(new Insets(10, 16, 10, 16));
        row.setAlignment(Pos.CENTER_LEFT);
        if (shaded) row.setStyle("-fx-background-color: " + ParkingApp.BG_SURFACE + "55;");
        Label k = new Label(key);
        k.setFont(Font.font("System", 13));
        k.setTextFill(Color.web(ParkingApp.TEXT_M));
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Label v = new Label(value);
        v.setFont(Font.font("System", FontWeight.BOLD, 13));
        v.setTextFill(Color.web(ParkingApp.TEXT_H));
        row.getChildren().addAll(k, spacer, v);
        return row;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private HBox buildStep(String num, String label, String color) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        javafx.scene.layout.StackPane numBadge = new javafx.scene.layout.StackPane();
        javafx.scene.shape.Circle c = new javafx.scene.shape.Circle(13);
        c.setFill(Color.web(color + "22"));
        c.setStroke(Color.web(color + "66")); c.setStrokeWidth(1.2);
        Label numLbl = new Label(num);
        numLbl.setFont(Font.font("System", FontWeight.BOLD, 12));
        numLbl.setTextFill(Color.web(color));
        numBadge.getChildren().addAll(c, numLbl);

        Label stepLbl = new Label(label);
        stepLbl.setFont(Font.font("System", FontWeight.BOLD, 14));
        stepLbl.setTextFill(Color.web(ParkingApp.TEXT_H));

        row.getChildren().addAll(numBadge, stepLbl);
        return row;
    }

    private Separator makeDivider() {
        Separator s = new Separator();
        s.setStyle("-fx-background-color: " + ParkingApp.BORDER + ";");
        return s;
    }

    private void showStatus(String msg, String color) {
        statusLabel.setText(msg);
        statusLabel.setTextFill(Color.web(color));
        statusLabel.setStyle(
                "-fx-background-color: " + color + "18;" +
                        "-fx-background-radius: 8;" +
                        "-fx-border-color: " + color + "44;" +
                        "-fx-border-radius: 8;" +
                        "-fx-border-width: 1;"
        );
        Animations.statusSlideIn(statusLabel);
        if (color.equals(ParkingApp.DANGER)) Animations.shake(statusLabel);
    }
}