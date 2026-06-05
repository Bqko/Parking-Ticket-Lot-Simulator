package com.parking.ui;

import com.parking.db.CustomerRepository;
import com.parking.model.ParkingLot;
import com.parking.model.ParkingSpot;
import com.parking.enums.SpotType;
import com.parking.service.FeeCalculator;
import com.parking.service.SessionStats;
import com.parking.model.Ticket;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.*;

import java.util.List;

/**
 * Admin panel — pricing configuration, spot management, live stats, and export.
 *
 * <p>FeeCalculator is always fetched fresh from TICKET_MANAGER to avoid
 * holding a stale reference captured before DB restore completed.</p>
 */
public class AdminScreen {

    private final ParkingApp app;

    // Input fields — pricing
    private TextField rateField;
    private TextField capField;
    private TextField graceField;
    private TextField discountField;
    private TextField lostField;

    // Current-value display labels
    private Label currentRate;
    private Label currentCap;
    private Label currentGrace;
    private Label currentDiscount;
    private Label currentLost;

    // Live example fee label (updates as you type)
    private Label exampleFeeLabel;

    // Spot manager
    private TextField  newSpotIdField;
    private TextField  newSpotFloorField;
    private ComboBox<String> newSpotTypeBox;
    private ListView<String> spotListView;
    private Label      spotStatusLabel;

    // Stats
    private Label statRevenue;
    private Label statTickets;
    private Label statAvgFee;
    private Label statBusiestHour;

    // Status banner — pricing card
    private Label statusLabel;

    public AdminScreen(ParkingApp app) {
        this.app = app;
    }

    /** Always get calc fresh — avoids stale reference captured before DB restore. */
    private FeeCalculator calc() {
        return ParkingApp.TICKET_MANAGER.getFeeCalculator();
    }

    Node build() {
        ScrollPane sp = new ScrollPane();
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: "       + ParkingApp.BG_BASE +
                "; -fx-background-color: " + ParkingApp.BG_BASE +
                "; -fx-border-color: transparent;");

        VBox page = new VBox(24);
        page.setPadding(new Insets(40, 48, 40, 48));
        page.setStyle("-fx-background-color: " + ParkingApp.BG_BASE + ";");

        // ── Page header ──
        HBox header = new HBox(14);
        header.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label("⚙️");
        icon.setFont(Font.font("System", 28));
        VBox titles = new VBox(2);
        Label title = ParkingApp.pageTitle("Admin Panel");
        Label sub   = new Label("Configure pricing, manage spots, and view session analytics.");
        sub.setFont(Font.font("System", 13));
        sub.setTextFill(Color.web(ParkingApp.TEXT_M));
        titles.getChildren().addAll(title, sub);
        header.getChildren().addAll(icon, titles);

        // ── Status banner (hidden by default) ──
        statusLabel = new Label("");
        statusLabel.setFont(Font.font("System", 13));
        statusLabel.setWrapText(true);
        statusLabel.setVisible(false);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        statusLabel.setPadding(new Insets(12, 16, 12, 16));

        // ── Row 1: pricing form + current settings ──
        HBox row1 = new HBox(20);
        VBox pricingCard = buildPricingCard();
        VBox summaryCard = buildSummaryCard();
        HBox.setHgrow(pricingCard, Priority.ALWAYS);
        row1.getChildren().addAll(pricingCard, summaryCard);

        // ── Row 2: spot manager + session stats ──
        HBox row2 = new HBox(20);
        VBox spotCard  = buildSpotManagerCard();
        VBox statsCard = buildStatsCard();
        HBox.setHgrow(spotCard, Priority.ALWAYS);
        row2.getChildren().addAll(spotCard, statsCard);

        page.getChildren().addAll(header, statusLabel, row1, row2);
        sp.setContent(page);

        javafx.application.Platform.runLater(() ->
                Animations.staggerCards(60, 80, pricingCard, summaryCard, spotCard, statsCard));

        return sp;
    }

    // ════════════════════════════════════════════════════════════════════════
    // A — PRICING CARD (with live preview)
    // ════════════════════════════════════════════════════════════════════════

    private VBox buildPricingCard() {
        VBox card = ParkingApp.pageCard("Pricing Configuration");
        card.setMinWidth(400);
        card.setMaxWidth(500);
        card.setSpacing(18);

        // Base hourly rate
        rateField = ParkingApp.styledField("e.g. 20.0");
        rateField.setText(fmt(calc().getBaseRatePerHour()));
        rateField.textProperty().addListener((obs, o, n) -> updateLivePreview());
        card.getChildren().add(buildSettingRow(
                "💵", "Base Hourly Rate",
                "Charged per hour for a standard car.",
                rateField, ParkingApp.ACCENT));

        card.getChildren().add(makeDivider());

        // Daily maximum cap
        capField = ParkingApp.styledField("e.g. 200.0");
        capField.setText(fmt(calc().getDailyMaxRate()));
        capField.textProperty().addListener((obs, o, n) -> updateLivePreview());
        card.getChildren().add(buildSettingRow(
                "📆", "Daily Maximum Cap",
                "Maximum charged per 24-hour period.",
                capField, ParkingApp.INFO));

        card.getChildren().add(makeDivider());

        // Grace period
        graceField = ParkingApp.styledField("e.g. 15");
        graceField.setText(String.valueOf(calc().getGracePeriodMinutes()));
        card.getChildren().add(buildSettingRow(
                "⏱", "Grace Period (minutes)",
                "Stays shorter than this are free.",
                graceField, ParkingApp.SUCCESS));

        card.getChildren().add(makeDivider());

        // Discount
        discountField = ParkingApp.styledField("e.g. 0.0");
        discountField.setText(fmt(calc().getDiscountPercent()));
        discountField.textProperty().addListener((obs, o, n) -> updateLivePreview());
        card.getChildren().add(buildSettingRow(
                "🏷", "Global Discount (%)",
                "Applied to every ticket. 0 = no discount.",
                discountField, ParkingApp.WARNING));

        card.getChildren().add(makeDivider());

        // Lost ticket fee
        lostField = ParkingApp.styledField("e.g. 150.0");
        lostField.setText(fmt(calc().getLostTicketFee()));
        card.getChildren().add(buildSettingRow(
                "🎫", "Lost Ticket Flat Fee",
                "Flat fee when a customer loses their ticket.",
                lostField, ParkingApp.DANGER));

        card.getChildren().add(makeDivider());

        // Buttons
        Button saveBtn  = ParkingApp.primaryBtn("Save Changes  ✓", ParkingApp.ACCENT);
        Button resetBtn = ParkingApp.ghostBtn("Reset to Defaults");
        saveBtn.setOnAction(e  -> { Animations.buttonPulse(saveBtn);  handleSave(); });
        resetBtn.setOnAction(e -> { Animations.buttonPulse(resetBtn); handleReset(); });

        HBox btnRow = new HBox(10);
        HBox.setHgrow(saveBtn, Priority.ALWAYS);
        btnRow.getChildren().addAll(saveBtn, resetBtn);
        card.getChildren().add(btnRow);

        return card;
    }

    private VBox buildSettingRow(String icon, String label, String hint,
                                 TextField field, String color) {
        VBox row = new VBox(6);

        HBox labelRow = new HBox(8);
        labelRow.setAlignment(Pos.CENTER_LEFT);

        StackPane iconCircle = new StackPane();
        Circle c = new Circle(14);
        c.setFill(Color.web(color + "22"));
        Label iconLbl = new Label(icon);
        iconLbl.setFont(Font.font("System", 13));
        iconCircle.getChildren().addAll(c, iconLbl);

        Label nameLbl = new Label(label);
        nameLbl.setFont(Font.font("System", FontWeight.BOLD, 13));
        nameLbl.setTextFill(Color.web(ParkingApp.TEXT_H));

        labelRow.getChildren().addAll(iconCircle, nameLbl);

        Label hintLbl = new Label(hint);
        hintLbl.setFont(Font.font("System", 11));
        hintLbl.setTextFill(Color.web(ParkingApp.TEXT_M));
        hintLbl.setWrapText(true);

        row.getChildren().addAll(labelRow, hintLbl, field);
        return row;
    }

    // ════════════════════════════════════════════════════════════════════════
    // B — SUMMARY CARD (live preview updates as you type)
    // ════════════════════════════════════════════════════════════════════════

    private VBox buildSummaryCard() {
        VBox card = ParkingApp.pageCard("Current Settings");
        card.setMinWidth(260);
        card.setMaxWidth(300);

        currentRate     = summaryValue(fmt(calc().getBaseRatePerHour()) + "/hr",  ParkingApp.ACCENT);
        currentCap      = summaryValue(fmt(calc().getDailyMaxRate())    + "/day",  ParkingApp.INFO);
        currentGrace    = summaryValue(calc().getGracePeriodMinutes()   + " min",  ParkingApp.SUCCESS);
        currentDiscount = summaryValue(fmt(calc().getDiscountPercent()) + "%",     ParkingApp.WARNING);
        currentLost     = summaryValue(fmt(calc().getLostTicketFee())   + " flat", ParkingApp.DANGER);

        VBox rows = new VBox(0);
        rows.setStyle(
                "-fx-background-color: " + ParkingApp.BG_RAISED + ";" +
                        "-fx-background-radius: 10;");
        rows.getChildren().addAll(
                summaryRow("Base Rate",    currentRate,     false),
                summaryRow("Daily Cap",    currentCap,      true),
                summaryRow("Grace Period", currentGrace,    false),
                summaryRow("Discount",     currentDiscount, true),
                summaryRow("Lost Ticket",  currentLost,     false)
        );

        // ── Live preview box ──
        VBox previewBox = new VBox(6);
        previewBox.setPadding(new Insets(16));
        previewBox.setStyle(
                "-fx-background-color: " + ParkingApp.ACCENT + "12;" +
                        "-fx-background-radius: 10;" +
                        "-fx-border-color: "     + ParkingApp.ACCENT + "33;" +
                        "-fx-border-radius: 10;" +
                        "-fx-border-width: 1;");

        Label previewTitle = new Label("LIVE PREVIEW");
        previewTitle.setFont(Font.font("System", FontWeight.BOLD, 10));
        previewTitle.setTextFill(Color.web(ParkingApp.ACCENT));

        Label previewDesc = new Label("Car parked 3 hours at current rates:");
        previewDesc.setFont(Font.font("System", 12));
        previewDesc.setTextFill(Color.web(ParkingApp.TEXT_M));
        previewDesc.setWrapText(true);

        exampleFeeLabel = new Label(calcExampleFee());
        exampleFeeLabel.setFont(Font.font("Georgia", FontWeight.BOLD, 28));
        exampleFeeLabel.setTextFill(Color.web(ParkingApp.TEXT_H));

        previewBox.getChildren().addAll(previewTitle, previewDesc, exampleFeeLabel);

        card.getChildren().addAll(rows, previewBox);
        return card;
    }

    /**
     * Recalculates the example fee from whatever is currently typed in the fields.
     * Called on every keystroke in rate, cap, or discount fields.
     */
    private void updateLivePreview() {
        if (exampleFeeLabel == null) return;
        exampleFeeLabel.setText(calcExampleFee());
    }

    private String calcExampleFee() {
        try {
            double rate     = parseDoubleRaw(rateField);
            double cap      = parseDoubleRaw(capField);
            double discount = parseDoubleRaw(discountField);
            double fee      = 3.0 * rate; // 3 hours, car multiplier = 1.0
            if (discount > 0 && discount <= 100) fee *= (1.0 - discount / 100.0);
            fee = Math.min(fee, cap);
            return String.format("%.2f", fee);
        } catch (Exception e) {
            return "—";
        }
    }

    private double parseDoubleRaw(TextField f) {
        return Double.parseDouble(f.getText().trim());
    }

    // ════════════════════════════════════════════════════════════════════════
    // A — SPOT MANAGER CARD
    // ════════════════════════════════════════════════════════════════════════

    private VBox buildSpotManagerCard() {
        VBox card = ParkingApp.pageCard("Parking Spot Manager");
        card.setSpacing(16);

        // ── Add new spot row ──
        Label addTitle = new Label("ADD NEW SPOT");
        addTitle.setFont(Font.font("System", FontWeight.BOLD, 11));
        addTitle.setTextFill(Color.web(ParkingApp.TEXT_M));

        newSpotIdField    = ParkingApp.styledField("Spot ID  e.g. F3-S01");
        newSpotFloorField = ParkingApp.styledField("Floor  e.g. 3");

        newSpotTypeBox = new ComboBox<>();
        newSpotTypeBox.getItems().addAll("STANDARD", "COMPACT", "MOTORCYCLE", "LARGE");
        newSpotTypeBox.setValue("STANDARD");
        newSpotTypeBox.setPrefHeight(42);
        newSpotTypeBox.setMaxWidth(Double.MAX_VALUE);
        newSpotTypeBox.setStyle(
                "-fx-background-color: " + ParkingApp.BG_BASE + ";" +
                        "-fx-border-color: "     + ParkingApp.BORDER_LIT + ";" +
                        "-fx-border-radius: 10;" +
                        "-fx-background-radius: 10;" +
                        "-fx-border-width: 1;");

        HBox addRow = new HBox(10);
        HBox.setHgrow(newSpotIdField,    Priority.ALWAYS);
        HBox.setHgrow(newSpotFloorField, Priority.SOMETIMES);
        HBox.setHgrow(newSpotTypeBox,    Priority.SOMETIMES);
        newSpotFloorField.setMaxWidth(80);
        newSpotTypeBox.setPrefWidth(130);
        addRow.getChildren().addAll(newSpotIdField, newSpotFloorField, newSpotTypeBox);

        Button addBtn = ParkingApp.primaryBtn("Add Spot  +", ParkingApp.SUCCESS);
        addBtn.setOnAction(e -> { Animations.buttonPulse(addBtn); handleAddSpot(); });

        // ── Status label for spot operations ──
        spotStatusLabel = new Label("");
        spotStatusLabel.setFont(Font.font("System", 12));
        spotStatusLabel.setWrapText(true);
        spotStatusLabel.setVisible(false);
        spotStatusLabel.setPadding(new Insets(8, 12, 8, 12));

        // ── Spot list ──
        Label listTitle = new Label("CURRENT SPOTS  —  click a free spot to remove");
        listTitle.setFont(Font.font("System", FontWeight.BOLD, 11));
        listTitle.setTextFill(Color.web(ParkingApp.TEXT_M));

        spotListView = new ListView<>();
        spotListView.setPrefHeight(200);
        spotListView.setStyle(
                "-fx-background-color: " + ParkingApp.BG_RAISED + ";" +
                        "-fx-background-radius: 10;" +
                        "-fx-border-color: "     + ParkingApp.BORDER + ";" +
                        "-fx-border-radius: 10;" +
                        "-fx-border-width: 1;");
        refreshSpotList();

        // Remove on click (only free spots)
        spotListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) handleRemoveSpot();
        });

        Label removeHint = new Label("Double-click a free spot in the list to remove it.");
        removeHint.setFont(Font.font("System", 11));
        removeHint.setTextFill(Color.web(ParkingApp.TEXT_M));

        card.getChildren().addAll(
                addTitle, addRow, addBtn,
                spotStatusLabel,
                makeDivider(),
                listTitle, spotListView, removeHint
        );
        return card;
    }

    private void refreshSpotList() {
        spotListView.getItems().clear();
        ParkingLot.getInstance().getAllSpots().stream()
                .sorted((a, b) -> a.getSpotId().compareTo(b.getSpotId()))
                .forEach(s -> {
                    String status = s.isOccupied() ? "🔴 OCCUPIED" : "🟢 FREE";
                    spotListView.getItems().add(
                            String.format("%-12s  Floor %d  %-12s  %s",
                                    s.getSpotId(), s.getFloor(),
                                    s.getSpotType().name(), status));
                });
    }

    private void handleAddSpot() {
        String id    = newSpotIdField.getText().trim().toUpperCase();
        String flStr = newSpotFloorField.getText().trim();
        String type  = newSpotTypeBox.getValue();

        if (id.isBlank())    { showSpotStatus("Spot ID cannot be empty.", ParkingApp.DANGER); return; }
        if (flStr.isBlank()) { showSpotStatus("Floor cannot be empty.",   ParkingApp.DANGER); return; }

        int floor;
        try { floor = Integer.parseInt(flStr); }
        catch (NumberFormatException e) { showSpotStatus("Floor must be a whole number.", ParkingApp.DANGER); return; }

        try {
            SpotType spotType = SpotType.valueOf(type);
            ParkingSpot spot  = new ParkingSpot(id, floor, spotType);
            ParkingLot.getInstance().addSpot(spot);
            refreshSpotList();
            newSpotIdField.clear();
            newSpotFloorField.clear();
            showSpotStatus("✓  Spot " + id + " added on floor " + floor + ".", ParkingApp.SUCCESS);
        } catch (IllegalArgumentException ex) {
            showSpotStatus("✗  " + ex.getMessage(), ParkingApp.DANGER);
        }
    }

    private void handleRemoveSpot() {
        String selected = spotListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        // Extract spot ID — it's the first token
        String spotId = selected.trim().split("\\s+")[0];

        try {
            ParkingLot.getInstance().removeSpot(spotId);
            refreshSpotList();
            showSpotStatus("✓  Spot " + spotId + " removed.", ParkingApp.SUCCESS);
        } catch (Exception ex) {
            showSpotStatus("✗  " + ex.getMessage(), ParkingApp.DANGER);
        }
    }

    private void showSpotStatus(String msg, String color) {
        spotStatusLabel.setText(msg);
        spotStatusLabel.setTextFill(Color.web(color));
        spotStatusLabel.setStyle(
                "-fx-background-color: " + color + "18;" +
                        "-fx-background-radius: 8;" +
                        "-fx-border-color: "     + color + "44;" +
                        "-fx-border-radius: 8;" +
                        "-fx-border-width: 1;");
        Animations.statusSlideIn(spotStatusLabel);
        if (color.equals(ParkingApp.DANGER)) Animations.shake(spotStatusLabel);
    }

    // ════════════════════════════════════════════════════════════════════════
    // C — SESSION STATS CARD
    // ════════════════════════════════════════════════════════════════════════

    private VBox buildStatsCard() {
        VBox card = ParkingApp.pageCard("Session Statistics");
        card.setMinWidth(260);
        card.setMaxWidth(300);
        card.setSpacing(16);

        List<Ticket> history = ParkingApp.TICKET_MANAGER.getSessionHistory();

        // ── Stat tiles ──
        double revenue   = SessionStats.totalRevenue(history);
        int    tickets   = history.size();
        double avg       = SessionStats.averageFee(history);
        int    busiest   = SessionStats.busiestEntryHour(history);

        VBox statRows = new VBox(0);
        statRows.setStyle(
                "-fx-background-color: " + ParkingApp.BG_RAISED + ";" +
                        "-fx-background-radius: 10;");

        statRevenue     = summaryValue(String.format("%.2f", revenue), ParkingApp.SUCCESS);
        statTickets     = summaryValue(String.valueOf(tickets),         ParkingApp.ACCENT);
        statAvgFee      = summaryValue(String.format("%.2f", avg),     ParkingApp.INFO);
        statBusiestHour = summaryValue(busiest >= 0 ? busiest + ":00" : "—", ParkingApp.WARNING);

        statRows.getChildren().addAll(
                summaryRow("Total Revenue",   statRevenue,     false),
                summaryRow("Completed",       statTickets,     true),
                summaryRow("Average Fee",     statAvgFee,      false),
                summaryRow("Busiest Hour",    statBusiestHour, true)
        );

        // ── Lot status ──
        ParkingLot lot  = ParkingLot.getInstance();
        long avail      = lot.getAvailableCount();
        long occupied   = lot.getOccupiedCount();
        int  total      = lot.getTotalSpots();
        double pct      = lot.getOccupancyRate();

        VBox lotBox = new VBox(8);
        lotBox.setPadding(new Insets(14));
        lotBox.setStyle(
                "-fx-background-color: " + ParkingApp.BG_RAISED + ";" +
                        "-fx-background-radius: 10;" +
                        "-fx-border-color: "     + ParkingApp.BORDER + ";" +
                        "-fx-border-radius: 10;" +
                        "-fx-border-width: 1;");

        Label lotTitle = new Label("LOT STATUS");
        lotTitle.setFont(Font.font("System", FontWeight.BOLD, 10));
        lotTitle.setTextFill(Color.web(ParkingApp.TEXT_M));

        String occupancyColor = pct >= 90 ? ParkingApp.DANGER
                : pct >= 75 ? ParkingApp.WARNING
                  : ParkingApp.SUCCESS;

        Label lotVal = new Label(String.format("%.0f%% occupied", pct));
        lotVal.setFont(Font.font("Georgia", FontWeight.BOLD, 22));
        lotVal.setTextFill(Color.web(occupancyColor));

        Label lotDetail = new Label(
                occupied + " / " + total + " spots used  •  " + avail + " free");
        lotDetail.setFont(Font.font("System", 12));
        lotDetail.setTextFill(Color.web(ParkingApp.TEXT_M));

        lotBox.getChildren().addAll(lotTitle, lotVal, lotDetail);

        // ── Export button ──
        Button exportBtn = ParkingApp.primaryBtn("📄  Export Tickets to TXT", ParkingApp.INFO);
        exportBtn.setOnAction(e -> {
            Animations.buttonPulse(exportBtn);
            ParkingApp.TICKET_MANAGER.saveSession();
            showStatus("✓  Exported to tickets_log.txt in project root.", ParkingApp.SUCCESS);
        });

        // ── Refresh stats button ──
        Button refreshBtn = ParkingApp.ghostBtn("↻  Refresh Stats");
        refreshBtn.setOnAction(e -> refreshStats());

        card.getChildren().addAll(statRows, lotBox, exportBtn, refreshBtn);
        return card;
    }

    private void refreshStats() {
        List<Ticket> history = ParkingApp.TICKET_MANAGER.getSessionHistory();
        statRevenue.setText(String.format("%.2f", SessionStats.totalRevenue(history)));
        statTickets.setText(String.valueOf(history.size()));
        statAvgFee.setText(String.format("%.2f", SessionStats.averageFee(history)));
        int busiest = SessionStats.busiestEntryHour(history);
        statBusiestHour.setText(busiest >= 0 ? busiest + ":00" : "—");
        showStatus("✓  Stats refreshed.", ParkingApp.SUCCESS);
    }

    // ════════════════════════════════════════════════════════════════════════
    // HANDLERS — PRICING
    // ════════════════════════════════════════════════════════════════════════

    private void handleSave() {
        try {
            double rate     = parseDouble(rateField,     "Base Hourly Rate");
            double cap      = parseDouble(capField,      "Daily Maximum Cap");
            int    grace    = parseInt(graceField,       "Grace Period");
            double discount = parseDouble(discountField, "Global Discount");
            double lost     = parseDouble(lostField,     "Lost Ticket Fee");

            // Apply to the live calculator
            calc().setBaseRatePerHour(rate);
            calc().setDailyMaxRate(cap);
            calc().setGracePeriodMinutes(grace);
            calc().setDiscountPercent(discount);
            calc().setLostTicketFee(lost);

            // Persist to database
            ParkingApp.TICKET_MANAGER.saveFeeConfig();

            // Refresh summary labels
            currentRate.setText(fmt(rate)         + "/hr");
            currentCap.setText(fmt(cap)           + "/day");
            currentGrace.setText(grace            + " min");
            currentDiscount.setText(fmt(discount) + "%");
            currentLost.setText(fmt(lost)         + " flat");

            updateLivePreview();
            showStatus("✓  Settings saved and persisted to database.", ParkingApp.SUCCESS);

        } catch (IllegalArgumentException e) {
            showStatus("✗  " + e.getMessage(), ParkingApp.DANGER);
        }
    }

    private void handleReset() {
        FeeCalculator defaults = new FeeCalculator();
        rateField.setText(fmt(defaults.getBaseRatePerHour()));
        capField.setText(fmt(defaults.getDailyMaxRate()));
        graceField.setText(String.valueOf(defaults.getGracePeriodMinutes()));
        discountField.setText(fmt(defaults.getDiscountPercent()));
        lostField.setText(fmt(defaults.getLostTicketFee()));
        updateLivePreview();
        showStatus("ℹ  Fields reset to defaults. Press Save to apply.", ParkingApp.INFO);
    }

    // ════════════════════════════════════════════════════════════════════════
    // SHARED HELPERS
    // ════════════════════════════════════════════════════════════════════════

    private HBox summaryRow(String key, Label valueLabel, boolean shaded) {
        HBox row = new HBox();
        row.setPadding(new Insets(11, 16, 11, 16));
        row.setAlignment(Pos.CENTER_LEFT);
        if (shaded) row.setStyle("-fx-background-color: " + ParkingApp.BG_SURFACE + "66;");
        Label k = new Label(key);
        k.setFont(Font.font("System", 13));
        k.setTextFill(Color.web(ParkingApp.TEXT_M));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(k, spacer, valueLabel);
        return row;
    }

    private Label summaryValue(String text, String color) {
        Label l = new Label(text);
        l.setFont(Font.font("System", FontWeight.BOLD, 13));
        l.setTextFill(Color.web(color));
        return l;
    }

    private void showStatus(String msg, String color) {
        statusLabel.setText(msg);
        statusLabel.setTextFill(Color.web(color));
        statusLabel.setStyle(
                "-fx-background-color: " + color + "18;" +
                        "-fx-background-radius: 8;" +
                        "-fx-border-color: "     + color + "44;" +
                        "-fx-border-radius: 8;" +
                        "-fx-border-width: 1;");
        Animations.statusSlideIn(statusLabel);
        if (color.equals(ParkingApp.DANGER)) Animations.shake(statusLabel);
    }

    private Separator makeDivider() {
        Separator s = new Separator();
        s.setStyle("-fx-background-color: " + ParkingApp.BORDER + ";");
        return s;
    }

    private double parseDouble(TextField field, String name) {
        try {
            double v = Double.parseDouble(field.getText().trim());
            if (v < 0) throw new IllegalArgumentException(name + " cannot be negative.");
            // Highlight field border green on valid
            field.setStyle(field.getStyle().replace(ParkingApp.DANGER, ParkingApp.SUCCESS));
            return v;
        } catch (NumberFormatException e) {
            // Highlight field border red on invalid
            markFieldError(field);
            throw new IllegalArgumentException(name + ": please enter a valid number.");
        }
    }

    private int parseInt(TextField field, String name) {
        try {
            int v = Integer.parseInt(field.getText().trim());
            if (v < 0) throw new IllegalArgumentException(name + " cannot be negative.");
            return v;
        } catch (NumberFormatException e) {
            markFieldError(field);
            throw new IllegalArgumentException(name + ": please enter a whole number.");
        }
    }

    private void markFieldError(TextField field) {
        String current = field.getStyle();
        field.setStyle(current.contains("-fx-border-color")
                ? current.replaceAll("-fx-border-color:[^;]+;",
                "-fx-border-color: " + ParkingApp.DANGER + ";")
                : current + "-fx-border-color: " + ParkingApp.DANGER + ";");
    }

    private String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
    }
}