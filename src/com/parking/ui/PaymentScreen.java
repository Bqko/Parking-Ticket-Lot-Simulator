package com.parking.ui;

import com.parking.model.Ticket;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.util.Optional;

/**
 * Screen 2 — Payment & Exit.
 *
 * <p>Two-step flow:</p>
 * <ol>
 *   <li><b>Lookup</b> — enter a ticket ID to see the current fee.</li>
 *   <li><b>Pay</b>    — enter the amount tendered; displays change and receipt.</li>
 *   <li><b>Exit</b>   — one-click exit gate release after payment.</li>
 * </ol>
 */
public class PaymentScreen {

    private final ParkingApp app;

    // Widgets shared between steps
    private TextField  ticketIdField;
    private TextField  amountField;
    private Label      feeLabel;
    private Label      statusLabel;
    private VBox       receiptPanel;
    private Label      receiptText;
    private Button     payButton;
    private Button     exitButton;

    // State
    private Ticket currentTicket;

    public PaymentScreen(ParkingApp app) {
        this.app = app;
    }

    public void show(Stage stage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + ParkingApp.BG_DARK + ";");
        root.setTop(buildHeader(stage));
        root.setCenter(buildBody());
        stage.setScene(new Scene(root, 960, 640));
    }

    // ── Header ─────────────────────────────────────────────────────────────

    private HBox buildHeader(Stage stage) {
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20, 30, 20, 30));
        header.setStyle("-fx-background-color: " + ParkingApp.BG_NAVY + ";");

        Button back = ParkingApp.makeButton("← Back", ParkingApp.BG_CARD);
        back.setOnAction(e -> app.showHome());

        Label title = new Label("💳  Payment & Exit");
        title.setFont(Font.font("System", FontWeight.BOLD, 20));
        title.setTextFill(Color.web(ParkingApp.TEXT_WHITE));

        header.getChildren().addAll(back, title);
        return header;
    }

    // ── Body ───────────────────────────────────────────────────────────────

    private HBox buildBody() {
        HBox body = new HBox(30);
        body.setPadding(new Insets(40));
        body.setAlignment(Pos.TOP_CENTER);
        body.getChildren().addAll(buildFormPanel(), buildReceiptPanel());
        return body;
    }

    // ── Left: form panel ───────────────────────────────────────────────────

    private VBox buildFormPanel() {
        VBox panel = new VBox(18);
        panel.setPrefWidth(420);
        panel.setPadding(new Insets(30));
        panel.setStyle(
                "-fx-background-color: " + ParkingApp.BG_CARD + ";" +
                        "-fx-background-radius: 12;"
        );

        Label heading = ParkingApp.sectionTitle("Pay for Parking");

        // ── Step 1: Ticket ID lookup ──
        Label step1 = makeStepLabel("STEP 1 — ENTER TICKET ID");
        ticketIdField = styledField("e.g.  A3F92C1D4B");

        Button lookupBtn = ParkingApp.makeButton("Look Up Fee", ParkingApp.TEAL);
        lookupBtn.setPrefWidth(360);
        lookupBtn.setOnAction(e -> handleLookup());

        feeLabel = new Label("Fee: —");
        feeLabel.setFont(Font.font("System", FontWeight.BOLD, 22));
        feeLabel.setTextFill(Color.web(ParkingApp.YELLOW));

        // ── Step 2: Enter amount ──
        Label step2 = makeStepLabel("STEP 2 — ENTER AMOUNT PAID (TRY)");
        amountField = styledField("e.g.  50.00");
        amountField.setDisable(true);

        payButton = ParkingApp.makeButton("Process Payment  💳", ParkingApp.GREEN);
        payButton.setPrefWidth(360);
        payButton.setDisable(true);
        payButton.setOnAction(e -> handlePayment());

        // ── Step 3: Exit ──
        Label step3 = makeStepLabel("STEP 3 — OPEN EXIT GATE");
        exitButton = ParkingApp.makeButton("Release Exit Gate  🚦", ParkingApp.BLUE);
        exitButton.setPrefWidth(360);
        exitButton.setDisable(true);
        exitButton.setOnAction(e -> handleExit());

        // ── Status ──
        statusLabel = new Label("");
        statusLabel.setFont(Font.font("System", 13));
        statusLabel.setWrapText(true);

        panel.getChildren().addAll(
                heading, new Separator(),
                step1, ticketIdField, lookupBtn, feeLabel,
                new Separator(),
                step2, amountField, payButton,
                new Separator(),
                step3, exitButton,
                statusLabel
        );
        return panel;
    }

    // ── Right: receipt panel ───────────────────────────────────────────────

    private VBox buildReceiptPanel() {
        receiptPanel = new VBox(14);
        receiptPanel.setPrefWidth(380);
        receiptPanel.setPadding(new Insets(30));
        receiptPanel.setAlignment(Pos.TOP_CENTER);
        receiptPanel.setStyle(
                "-fx-background-color: " + ParkingApp.BG_CARD + ";" +
                        "-fx-background-radius: 12;" +
                        "-fx-opacity: 0.4;"
        );

        Label icon = new Label("🧾");
        icon.setFont(Font.font("System", 48));

        Label waiting = new Label("Receipt will appear\nhere after payment");
        waiting.setFont(Font.font("System", 14));
        waiting.setTextFill(Color.web(ParkingApp.TEXT_MUTED));
        waiting.setAlignment(Pos.CENTER);

        receiptText = new Label("");
        receiptText.setFont(Font.font("Monospaced", 12));
        receiptText.setTextFill(Color.web(ParkingApp.TEXT_WHITE));
        receiptText.setWrapText(true);

        receiptPanel.getChildren().addAll(icon, waiting, receiptText);
        return receiptPanel;
    }

    // ── Step handlers ──────────────────────────────────────────────────────

    private void handleLookup() {
        String id = ticketIdField.getText().trim().toUpperCase();
        if (id.isEmpty()) { setStatus("❌  Enter a ticket ID.", ParkingApp.RED); return; }

        Optional<Ticket> opt = ParkingApp.TICKET_MANAGER.findTicket(id);
        if (opt.isEmpty()) {
            setStatus("❌  Ticket not found: " + id, ParkingApp.RED);
            feeLabel.setText("Fee: —");
            return;
        }

        currentTicket = opt.get();

        switch (currentTicket.getStatus()) {
            case PAID   -> { setStatus("✅  Already paid. Proceed to exit.", ParkingApp.GREEN);
                exitButton.setDisable(false); return; }
            case EXITED -> { setStatus("ℹ️   This ticket is already closed.", ParkingApp.TEAL); return; }
            default     -> {}
        }

        double fee = ParkingApp.TICKET_MANAGER.previewFee(id);
        feeLabel.setText(String.format("Fee:  %.2f TRY", fee));
        amountField.setDisable(false);
        payButton.setDisable(false);
        setStatus("✅  Ticket found. Enter the amount paid.", ParkingApp.GREEN);
    }

    private void handlePayment() {
        if (currentTicket == null) { setStatus("❌  Look up a ticket first.", ParkingApp.RED); return; }

        double amount;
        try {
            amount = Double.parseDouble(amountField.getText().trim());
        } catch (NumberFormatException ex) {
            setStatus("❌  Invalid amount.", ParkingApp.RED);
            return;
        }

        try {
            double change = ParkingApp.TICKET_MANAGER.processPayment(
                    currentTicket.getTicketId(), amount);

            // Show receipt
            receiptPanel.setStyle(
                    "-fx-background-color: " + ParkingApp.BG_CARD + ";" +
                            "-fx-background-radius: 12;" +
                            "-fx-border-color: " + ParkingApp.GREEN + ";" +
                            "-fx-border-radius: 12;" +
                            "-fx-border-width: 2;"
            );
            receiptText.setText(currentTicket.toReceiptString());

            feeLabel.setText(String.format("Change: %.2f TRY ✅", change));
            feeLabel.setTextFill(Color.web(ParkingApp.GREEN));
            payButton.setDisable(true);
            amountField.setDisable(true);
            exitButton.setDisable(false);
            setStatus("✅  Payment accepted! Proceed to exit gate.", ParkingApp.GREEN);

        } catch (IllegalArgumentException e) {
            setStatus("❌  " + e.getMessage(), ParkingApp.RED);
        }
    }

    private void handleExit() {
        if (currentTicket == null) { setStatus("❌  No ticket selected.", ParkingApp.RED); return; }

        try {
            ParkingApp.TICKET_MANAGER.processExit(currentTicket.getTicketId());
            exitButton.setDisable(true);
            setStatus("🚗  Exit gate open! Spot " + currentTicket.getSpot().getSpotId()
                    + " is now free. Drive safely!", ParkingApp.GREEN);

            // Reset for next customer
            currentTicket = null;
            ticketIdField.clear();
            amountField.clear();
            amountField.setDisable(true);
            payButton.setDisable(true);
            feeLabel.setText("Fee: —");
            feeLabel.setTextFill(Color.web(ParkingApp.YELLOW));

        } catch (IllegalStateException e) {
            setStatus("❌  " + e.getMessage(), ParkingApp.RED);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void setStatus(String msg, String color) {
        statusLabel.setText(msg);
        statusLabel.setTextFill(Color.web(color));
    }

    private Label makeStepLabel(String text) {
        Label l = ParkingApp.makeFieldLabel(text);
        l.setTextFill(Color.web(ParkingApp.TEAL));
        return l;
    }

    private TextField styledField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setStyle(
                "-fx-background-color: " + ParkingApp.BG_DARK + ";" +
                        "-fx-text-fill: " + ParkingApp.TEXT_WHITE + ";" +
                        "-fx-prompt-text-fill: " + ParkingApp.TEXT_MUTED + ";" +
                        "-fx-background-radius: 8;" +
                        "-fx-padding: 10 14 10 14;" +
                        "-fx-font-size: 14px;"
        );
        tf.setPrefHeight(42);
        return tf;
    }
}