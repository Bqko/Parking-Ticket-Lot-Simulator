package com.parking.ui;

import com.parking.db.CustomerRepository;
import com.parking.db.SpotRepository;
import com.parking.db.VipTierRepository;
import com.parking.model.ParkingLot;
import com.parking.model.ParkingSpot;
import com.parking.enums.SpotType;
import com.parking.service.FeeCalculator;
import com.parking.service.PricingTier;
import com.parking.service.SessionStats;
import com.parking.model.Ticket;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.*;

import java.util.List;

public class AdminScreen {

    private final ParkingApp app;

    // ── Repositories ──────────────────────────────────────────────────────
    private final SpotRepository      spotRepo = new SpotRepository();
    private final VipTierRepository   vipRepo  = new VipTierRepository();
    private final CustomerRepository  custRepo = new CustomerRepository();

    // ── Pricing fields ────────────────────────────────────────────────────
    private TextField rateField;
    private TextField capField;
    private TextField graceField;
    private TextField discountField;
    private TextField lostField;

    private Label currentRate;
    private Label currentCap;
    private Label currentGrace;
    private Label currentDiscount;
    private Label currentLost;
    private Label exampleFeeLabel;

    // ── Spot manager fields ───────────────────────────────────────────────
    private TextField               newSpotIdField;
    private TextField               newSpotFloorField;
    private ComboBox<String>        newSpotTypeBox;
    private TableView<ParkingSpot>  spotTableView;
    private Label                   spotStatusLabel;
    private Button                  removeSpotBtn;   // kept as field so we can enable/disable it

    // ── Stats labels ──────────────────────────────────────────────────────
    private Label statRevenue;
    private Label statTickets;
    private Label statAvgFee;
    private Label statBusiestHour;

    // ── VIP tier fields ───────────────────────────────────────────────────
    private TextField                                    vipNameField;
    private TextField                                    vipDiscountField;
    private TextField                                    vipDescField;
    private TableView<VipTierRepository.VipTierRecord>   vipTable;
    private Label                                        vipStatusLabel;

    // ── Customer fields ───────────────────────────────────────────────────
    private TextField                                        custSearchField;
    private TableView<CustomerRepository.CustomerRecord>     custTable;
    private Label                                            custStatusLabel;

    // ── Pricing tier fields ───────────────────────────────────────────────
    private TableView<PricingTier> tierTable;
    private TextField              tierNameField;
    private TextField              tierStartField;
    private TextField              tierEndField;
    private TextField              tierMultField;
    private Label                  tierStatusLabel;

    // ── Global status banner ──────────────────────────────────────────────
    private Label statusLabel;

    public AdminScreen(ParkingApp app) {
        this.app = app;
    }

    private FeeCalculator calc() {
        return ParkingApp.TICKET_MANAGER.getFeeCalculator();
    }

    // ════════════════════════════════════════════════════════════════════════
    // BUILD
    // ════════════════════════════════════════════════════════════════════════

    Node build() {
        ScrollPane sp = new ScrollPane();
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: " + ParkingApp.BG_BASE +
                "; -fx-background-color: " + ParkingApp.BG_BASE +
                "; -fx-border-color: transparent;");

        VBox page = new VBox(24);
        page.setPadding(new Insets(40, 48, 40, 48));
        page.setStyle("-fx-background-color: " + ParkingApp.BG_BASE + ";");

        HBox header = new HBox(14);
        header.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label("⚙️");
        icon.setFont(Font.font("System", 28));
        VBox titles = new VBox(2);
        Label title = ParkingApp.pageTitle("Admin Panel");
        Label sub   = new Label("Configure pricing, manage spots, VIP tiers, customers, and analytics.");
        sub.setFont(Font.font("System", 13));
        sub.setTextFill(Color.web(ParkingApp.TEXT_M));
        titles.getChildren().addAll(title, sub);
        header.getChildren().addAll(icon, titles);

        statusLabel = new Label("");
        statusLabel.setFont(Font.font("System", 13));
        statusLabel.setWrapText(true);
        statusLabel.setVisible(false);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        statusLabel.setPadding(new Insets(12, 16, 12, 16));

        HBox row1 = new HBox(20);
        VBox pricingCard = buildPricingCard();
        VBox summaryCard = buildSummaryCard();
        HBox.setHgrow(pricingCard, Priority.ALWAYS);
        row1.getChildren().addAll(pricingCard, summaryCard);

        HBox row2 = new HBox(20);
        VBox spotCard = buildSpotManagerCard();
        VBox tierCard = buildPricingTierCard();
        HBox.setHgrow(spotCard, Priority.ALWAYS);
        HBox.setHgrow(tierCard, Priority.ALWAYS);
        row2.getChildren().addAll(spotCard, tierCard);

        HBox row3 = new HBox(20);
        VBox vipCard   = buildVipTierCard();
        VBox statsCard = buildStatsCard();
        HBox.setHgrow(vipCard, Priority.ALWAYS);
        row3.getChildren().addAll(vipCard, statsCard);

        VBox custCard = buildCustomerCard();

        page.getChildren().addAll(header, statusLabel, row1, row2, row3, custCard);
        sp.setContent(page);

        javafx.application.Platform.runLater(() ->
                Animations.staggerCards(60, 80,
                        pricingCard, summaryCard, spotCard, tierCard, vipCard, statsCard, custCard));

        return sp;
    }

    // ════════════════════════════════════════════════════════════════════════
    // A — PRICING CONFIGURATION CARD
    // ════════════════════════════════════════════════════════════════════════

    private VBox buildPricingCard() {
        VBox card = ParkingApp.pageCard("Pricing Configuration");
        card.setMinWidth(400);
        card.setMaxWidth(500);
        card.setSpacing(18);

        rateField = ParkingApp.styledField("e.g. 20.0");
        rateField.setText(fmt(calc().getBaseRatePerHour()));
        rateField.textProperty().addListener((obs, o, n) -> updateLivePreview());
        card.getChildren().add(buildSettingRow("💵", "Base Hourly Rate",
                "Charged per hour for a standard car.", rateField, ParkingApp.ACCENT));
        card.getChildren().add(makeDivider());

        capField = ParkingApp.styledField("e.g. 200.0");
        capField.setText(fmt(calc().getDailyMaxRate()));
        capField.textProperty().addListener((obs, o, n) -> updateLivePreview());
        card.getChildren().add(buildSettingRow("📆", "Daily Maximum Cap",
                "Maximum charged per 24-hour period.", capField, ParkingApp.INFO));
        card.getChildren().add(makeDivider());

        graceField = ParkingApp.styledField("e.g. 15");
        graceField.setText(String.valueOf(calc().getGracePeriodMinutes()));
        card.getChildren().add(buildSettingRow("⏱", "Grace Period (minutes)",
                "Stays shorter than this are free.", graceField, ParkingApp.SUCCESS));
        card.getChildren().add(makeDivider());

        discountField = ParkingApp.styledField("e.g. 0.0");
        discountField.setText(fmt(calc().getDiscountPercent()));
        discountField.textProperty().addListener((obs, o, n) -> updateLivePreview());
        card.getChildren().add(buildSettingRow("🏷", "Global Discount (%)",
                "Applied to every ticket. 0 = no discount.", discountField, ParkingApp.WARNING));
        card.getChildren().add(makeDivider());

        lostField = ParkingApp.styledField("e.g. 150.0");
        lostField.setText(fmt(calc().getLostTicketFee()));
        card.getChildren().add(buildSettingRow("🎫", "Lost Ticket Flat Fee",
                "Flat fee when a customer loses their ticket.", lostField, ParkingApp.DANGER));
        card.getChildren().add(makeDivider());

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
    // B — CURRENT SETTINGS SUMMARY + LIVE PREVIEW
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
        rows.setStyle("-fx-background-color: " + ParkingApp.BG_RAISED + "; -fx-background-radius: 10;");
        rows.getChildren().addAll(
                summaryRow("Base Rate",    currentRate,     false),
                summaryRow("Daily Cap",    currentCap,      true),
                summaryRow("Grace Period", currentGrace,    false),
                summaryRow("Discount",     currentDiscount, true),
                summaryRow("Lost Ticket",  currentLost,     false)
        );

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

    private void updateLivePreview() {
        if (exampleFeeLabel == null) return;
        exampleFeeLabel.setText(calcExampleFee());
    }

    private String calcExampleFee() {
        try {
            double rate     = parseDoubleRaw(rateField);
            double cap      = parseDoubleRaw(capField);
            double discount = parseDoubleRaw(discountField);
            double fee      = 3.0 * rate;
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
    // C — PARKING SPOT MANAGER CARD
    // ════════════════════════════════════════════════════════════════════════

    private VBox buildSpotManagerCard() {
        VBox card = ParkingApp.pageCard("Parking Spot Manager");
        card.setSpacing(16);

        // ── ADD section ──────────────────────────────────────────────────
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
        HBox.setHgrow(newSpotIdField, Priority.ALWAYS);
        newSpotFloorField.setMaxWidth(80);
        newSpotTypeBox.setPrefWidth(130);
        addRow.getChildren().addAll(newSpotIdField, newSpotFloorField, newSpotTypeBox);

        Button addBtn = ParkingApp.primaryBtn("Add Spot  +", ParkingApp.SUCCESS);
        addBtn.setOnAction(e -> { Animations.buttonPulse(addBtn); handleAddSpot(); });

        // ── Status label ─────────────────────────────────────────────────
        spotStatusLabel = new Label("");
        spotStatusLabel.setFont(Font.font("System", 12));
        spotStatusLabel.setWrapText(true);
        spotStatusLabel.setVisible(false);
        spotStatusLabel.setPadding(new Insets(8, 12, 8, 12));

        // ── REMOVE section ────────────────────────────────────────────────
        Label listTitle = new Label("CURRENT SPOTS");
        listTitle.setFont(Font.font("System", FontWeight.BOLD, 11));
        listTitle.setTextFill(Color.web(ParkingApp.TEXT_M));

        ObservableList<ParkingSpot> spotData =
                FXCollections.observableArrayList(
                        ParkingLot.getInstance().getAllSpots().stream()
                                .sorted((a, b) -> a.getSpotId().compareTo(b.getSpotId()))
                                .toList());
        TableFactory.Result<ParkingSpot> spotResult =
                TableFactory.build(spotData, "Search by ID, floor, or type…",
                        (s, q) -> s.getSpotId().toLowerCase().contains(q)
                                || String.valueOf(s.getFloor()).contains(q)
                                || s.getSpotType().name().toLowerCase().contains(q));
        spotTableView = spotResult.table();
        TableFactory.setVisibleRows(spotTableView, 6);

        spotTableView.getColumns().addAll(
                TableFactory.dotCol("Spot ID",
                        ParkingSpot::getSpotId,
                        s -> s.isOccupied() ? ParkingApp.DANGER : ParkingApp.SUCCESS,
                        120),
                TableFactory.textCol("Floor",
                        s -> "Floor " + s.getFloor(), 90),
                TableFactory.badgeCol("Type",
                        s -> s.getSpotType().name(),
                        s -> switch (s.getSpotType()) {
                            case MOTORCYCLE -> ParkingApp.ACCENT;
                            case COMPACT    -> ParkingApp.INFO;
                            case LARGE      -> ParkingApp.WARNING;
                            default         -> ParkingApp.TEXT_M;
                        }, 120),
                TableFactory.badgeCol("Status",
                        s -> s.isOccupied() ? "Occupied" : "Free",
                        s -> s.isOccupied() ? ParkingApp.DANGER : ParkingApp.SUCCESS,
                        110)
        );

        // Remove button — disabled until a free spot is selected
        removeSpotBtn = ParkingApp.primaryBtn("\uD83D\uDDD1  Remove Selected Spot", ParkingApp.DANGER);
        removeSpotBtn.setDisable(true);
        removeSpotBtn.setOnAction(e -> { Animations.buttonPulse(removeSpotBtn); handleRemoveSpot(); });

        refreshSpotList();

        // Enable/disable remove button based on selection
        spotTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSpot, selected) -> {
            if (selected == null) {
                removeSpotBtn.setDisable(true);
                return;
            }
            if (selected.isOccupied()) {
                removeSpotBtn.setDisable(true);
                showSpotStatus("\u2717  Cannot remove an occupied spot.", ParkingApp.DANGER);
            } else {
                removeSpotBtn.setDisable(false);
                spotStatusLabel.setVisible(false);
            }
        });

        Label removeHint = new Label("Select a free spot in the table, then click Remove. Occupied spots cannot be removed.");
        removeHint.setFont(Font.font("System", 11));
        removeHint.setTextFill(Color.web(ParkingApp.TEXT_M));
        removeHint.setWrapText(true);

        card.getChildren().addAll(
                addTitle, addRow, addBtn,
                spotStatusLabel,
                makeDivider(),
                listTitle, spotResult.container(),
                removeSpotBtn,
                removeHint
        );
        return card;
    }

    private void refreshSpotList() {
        if (spotTableView == null) return;
        // Rebuild the backing list via the sorted source
        var sorted   = (javafx.collections.transformation.SortedList<ParkingSpot>) spotTableView.getItems();
        var filtered = (javafx.collections.transformation.FilteredList<ParkingSpot>) sorted.getSource();
        var source   = (ObservableList<ParkingSpot>) filtered.getSource();
        source.setAll(
                ParkingLot.getInstance().getAllSpots().stream()
                        .sorted((a, b) -> a.getSpotId().compareTo(b.getSpotId()))
                        .toList());
        if (removeSpotBtn != null) removeSpotBtn.setDisable(true);
        spotTableView.getSelectionModel().clearSelection();
    }

    private void handleAddSpot() {
        String id    = newSpotIdField.getText().trim().toUpperCase();
        String flStr = newSpotFloorField.getText().trim();
        String type  = newSpotTypeBox.getValue();

        if (id.isBlank())    { showSpotStatus("Spot ID cannot be empty.", ParkingApp.DANGER); return; }
        if (flStr.isBlank()) { showSpotStatus("Floor cannot be empty.",   ParkingApp.DANGER); return; }

        int floor;
        try { floor = Integer.parseInt(flStr); }
        catch (NumberFormatException e) {
            showSpotStatus("Floor must be a whole number.", ParkingApp.DANGER);
            return;
        }

        try {
            SpotType    spotType = SpotType.valueOf(type);
            ParkingSpot spot     = new ParkingSpot(id, floor, spotType);

            ParkingLot.getInstance().addSpot(spot);   // memory
            spotRepo.insertSpot(spot);                // database

            refreshSpotList();
            newSpotIdField.clear();
            newSpotFloorField.clear();
            showSpotStatus("✓  Spot " + id + " added on floor " + floor + ".", ParkingApp.SUCCESS);
        } catch (IllegalArgumentException ex) {
            showSpotStatus("✗  " + ex.getMessage(), ParkingApp.DANGER);
        }
    }

    private void handleRemoveSpot() {
        ParkingSpot selected = spotTableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showSpotStatus("Select a spot from the table first.", ParkingApp.WARNING);
            return;
        }

        // Guard: refuse occupied spots (belt-and-suspenders, button should already be disabled)
        if (selected.isOccupied()) {
            showSpotStatus("\u2717  Cannot remove an occupied spot.", ParkingApp.DANGER);
            return;
        }

        String spotId = selected.getSpotId();
        try {
            ParkingLot.getInstance().removeSpot(spotId);  // memory
            spotRepo.deleteSpot(spotId);                   // database
            refreshSpotList();
            showSpotStatus("\u2713  Spot " + spotId + " removed.", ParkingApp.SUCCESS);
        } catch (Exception ex) {
            showSpotStatus("\u2717  " + ex.getMessage(), ParkingApp.DANGER);
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
    // D — TIME-OF-DAY PRICING TIERS CARD
    // ════════════════════════════════════════════════════════════════════════

    private VBox buildPricingTierCard() {
        VBox card = ParkingApp.pageCard("Time-of-Day Pricing Tiers");
        card.setSpacing(16);

        ObservableList<PricingTier> tierData = FXCollections.observableArrayList(PricingTier.getAllTiers());
        TableFactory.Result<PricingTier> tierResult = TableFactory.build(
                tierData, "Search tiers…",
                (t, q) -> t.getName().toLowerCase().contains(q));
        tierTable = tierResult.table();
        TableFactory.setVisibleRows(tierTable, 5);
        tierTable.getColumns().addAll(
                TableFactory.textCol("Name",  PricingTier::getName, 140),
                TableFactory.textCol("Start", t -> String.format("%02d:00", t.getStartHour()), 80),
                TableFactory.textCol("End",   t -> String.format("%02d:00", t.getEndHour()), 80),
                TableFactory.badgeCol("Multiplier",
                        t -> String.format("×%.2f", t.getRateMultiplier()),
                        t -> t.getRateMultiplier() > 1.0 ? ParkingApp.DANGER
                                : t.getRateMultiplier() < 1.0 ? ParkingApp.SUCCESS
                                  : ParkingApp.INFO, 110)
        );
        refreshTierTable();

        Label addTitle = new Label("ADD / EDIT TIER");
        addTitle.setFont(Font.font("System", FontWeight.BOLD, 11));
        addTitle.setTextFill(Color.web(ParkingApp.TEXT_M));

        tierNameField  = ParkingApp.styledField("Name  e.g. Peak");
        tierStartField = ParkingApp.styledField("Start hr  e.g. 8");
        tierEndField   = ParkingApp.styledField("End hr  e.g. 18");
        tierMultField  = ParkingApp.styledField("Multiplier  e.g. 1.5");

        HBox tierFormRow = new HBox(8);
        HBox.setHgrow(tierNameField, Priority.ALWAYS);
        tierStartField.setMaxWidth(90);
        tierEndField.setMaxWidth(90);
        tierMultField.setMaxWidth(110);
        tierFormRow.getChildren().addAll(tierNameField, tierStartField, tierEndField, tierMultField);

        tierTable.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) -> {
            if (sel != null) {
                tierNameField.setText(sel.getName());
                tierStartField.setText(String.valueOf(sel.getStartHour()));
                tierEndField.setText(String.valueOf(sel.getEndHour()));
                tierMultField.setText(fmt(sel.getRateMultiplier()));
            }
        });

        Button addTierBtn    = ParkingApp.primaryBtn("Save Tier  ✓", ParkingApp.ACCENT);
        Button removeTierBtn = ParkingApp.ghostBtn("Remove Selected");
        addTierBtn.setOnAction(e    -> { Animations.buttonPulse(addTierBtn);    handleSaveTier(); });
        removeTierBtn.setOnAction(e -> { Animations.buttonPulse(removeTierBtn); handleRemoveTier(); });

        HBox tierBtnRow = new HBox(10);
        HBox.setHgrow(addTierBtn, Priority.ALWAYS);
        tierBtnRow.getChildren().addAll(addTierBtn, removeTierBtn);

        tierStatusLabel = new Label("");
        tierStatusLabel.setFont(Font.font("System", 12));
        tierStatusLabel.setVisible(false);
        tierStatusLabel.setPadding(new Insets(8, 12, 8, 12));
        tierStatusLabel.setWrapText(true);

        Label hint = new Label("Changes apply immediately for new tickets. Existing tickets keep the rate at entry time.");
        hint.setFont(Font.font("System", 11));
        hint.setTextFill(Color.web(ParkingApp.TEXT_M));
        hint.setWrapText(true);

        card.getChildren().addAll(tierTable, addTitle, tierFormRow, tierBtnRow, tierStatusLabel, hint);
        return card;
    }

    private void refreshTierTable() {
        if (tierTable == null) return;
        getSourceList(tierTable).setAll(PricingTier.getAllTiers());
    }

    private void handleSaveTier() {
        String name = tierNameField.getText().trim();
        if (name.isBlank()) { showTierStatus("Tier name cannot be empty.", ParkingApp.DANGER); return; }
        try {
            int    start = Integer.parseInt(tierStartField.getText().trim());
            int    end   = Integer.parseInt(tierEndField.getText().trim());
            double mult  = Double.parseDouble(tierMultField.getText().trim());
            if (start < 0 || start > 23 || end < 0 || end > 23)
                throw new NumberFormatException("hours out of range");
            if (mult <= 0)
                throw new NumberFormatException("multiplier must be positive");
            PricingTier.upsertTier(name, start, end, mult);
            refreshTierTable();
            tierNameField.clear(); tierStartField.clear();
            tierEndField.clear();  tierMultField.clear();
            showTierStatus("✓  Tier '" + name + "' saved.", ParkingApp.SUCCESS);
        } catch (NumberFormatException ex) {
            showTierStatus("✗  Check values: hours 0–23, multiplier > 0.", ParkingApp.DANGER);
        }
    }

    private void handleRemoveTier() {
        PricingTier selected = tierTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showTierStatus("Select a tier to remove.", ParkingApp.WARNING); return; }
        PricingTier.removeTier(selected.getName());
        refreshTierTable();
        showTierStatus("✓  Tier '" + selected.getName() + "' removed.", ParkingApp.SUCCESS);
    }

    private void showTierStatus(String msg, String color) {
        tierStatusLabel.setText(msg);
        tierStatusLabel.setTextFill(Color.web(color));
        tierStatusLabel.setStyle(
                "-fx-background-color: " + color + "18;" +
                        "-fx-background-radius: 8;" +
                        "-fx-border-color: "     + color + "44;" +
                        "-fx-border-radius: 8; -fx-border-width: 1;");
        Animations.statusSlideIn(tierStatusLabel);
    }

    // ════════════════════════════════════════════════════════════════════════
    // E — VIP / DISCOUNT TIERS CARD
    // ════════════════════════════════════════════════════════════════════════

    private VBox buildVipTierCard() {
        VBox card = ParkingApp.pageCard("VIP & Discount Tiers");
        card.setSpacing(16);

        ObservableList<VipTierRepository.VipTierRecord> vipData =
                FXCollections.observableArrayList(vipRepo.findAll());
        TableFactory.Result<VipTierRepository.VipTierRecord> vipResult = TableFactory.build(
                vipData, "Search tiers…",
                (r, q) -> r.tierName.toLowerCase().contains(q)
                        || r.description.toLowerCase().contains(q));
        vipTable = vipResult.table();
        TableFactory.setVisibleRows(vipTable, 5);
        vipTable.getColumns().addAll(
                TableFactory.textCol("Tier Name",   r -> r.tierName,    160),
                TableFactory.badgeCol("Discount",
                        r -> String.format("%.1f%%", r.discountPercent),
                        r -> r.discountPercent >= 30 ? ParkingApp.DANGER
                                : r.discountPercent >= 15 ? ParkingApp.WARNING
                                  : ParkingApp.SUCCESS, 100),
                TableFactory.textCol("Description", r -> r.description, 200),
                TableFactory.badgeCol("Status",
                        r -> r.isActive ? "Active" : "Inactive",
                        r -> r.isActive ? ParkingApp.SUCCESS : ParkingApp.DANGER, 90)
        );
        refreshVipTable();

        Label addTitle = new Label("ADD NEW TIER");
        addTitle.setFont(Font.font("System", FontWeight.BOLD, 11));
        addTitle.setTextFill(Color.web(ParkingApp.TEXT_M));

        vipNameField     = ParkingApp.styledField("Name  e.g. Staff");
        vipDiscountField = ParkingApp.styledField("Discount %  e.g. 20");
        vipDescField     = ParkingApp.styledField("Description  e.g. Employee benefit");

        HBox vipFormRow = new HBox(8);
        vipNameField.setMaxWidth(140);
        vipDiscountField.setMaxWidth(120);
        HBox.setHgrow(vipDescField, Priority.ALWAYS);
        vipFormRow.getChildren().addAll(vipNameField, vipDiscountField, vipDescField);

        Button addVipBtn    = ParkingApp.primaryBtn("Add Tier  +", ParkingApp.WARNING);
        Button removeVipBtn = ParkingApp.ghostBtn("Remove Selected");
        Button toggleVipBtn = ParkingApp.ghostBtn("Toggle Active");

        addVipBtn.setOnAction(e    -> { Animations.buttonPulse(addVipBtn);    handleAddVip(); });
        removeVipBtn.setOnAction(e -> { Animations.buttonPulse(removeVipBtn); handleRemoveVip(); });
        toggleVipBtn.setOnAction(e -> { Animations.buttonPulse(toggleVipBtn); handleToggleVip(); });

        HBox vipBtnRow = new HBox(10);
        HBox.setHgrow(addVipBtn, Priority.ALWAYS);
        vipBtnRow.getChildren().addAll(addVipBtn, removeVipBtn, toggleVipBtn);

        vipStatusLabel = new Label("");
        vipStatusLabel.setFont(Font.font("System", 12));
        vipStatusLabel.setVisible(false);
        vipStatusLabel.setPadding(new Insets(8, 12, 8, 12));
        vipStatusLabel.setWrapText(true);

        Label hint = new Label("Active tiers are selectable during payment to override the global discount.");
        hint.setFont(Font.font("System", 11));
        hint.setTextFill(Color.web(ParkingApp.TEXT_M));
        hint.setWrapText(true);

        card.getChildren().addAll(vipTable, addTitle, vipFormRow, vipBtnRow, vipStatusLabel, hint);
        return card;
    }

    @SuppressWarnings("unchecked")
    private void refreshVipTable() {
        if (vipTable == null) return;
        getSourceList(vipTable).setAll(vipRepo.findAll());
    }

    @SuppressWarnings("unchecked")
    private <T> ObservableList<T> getSourceList(TableView<T> table) {
        var sorted   = (javafx.collections.transformation.SortedList<T>) table.getItems();
        var filtered = (javafx.collections.transformation.FilteredList<T>) sorted.getSource();
        return (ObservableList<T>) filtered.getSource();
    }

    private void handleAddVip() {
        String name   = vipNameField.getText().trim();
        String pctStr = vipDiscountField.getText().trim();
        String desc   = vipDescField.getText().trim();
        if (name.isBlank()) { showVipStatus("Tier name cannot be empty.", ParkingApp.DANGER); return; }
        try {
            double pct = Double.parseDouble(pctStr);
            if (pct < 0 || pct > 100) throw new NumberFormatException();
            vipRepo.insert(name, pct, desc);
            refreshVipTable();
            vipNameField.clear(); vipDiscountField.clear(); vipDescField.clear();
            showVipStatus("✓  Tier '" + name + "' added.", ParkingApp.SUCCESS);
        } catch (NumberFormatException e) {
            showVipStatus("✗  Discount must be a number between 0 and 100.", ParkingApp.DANGER);
        } catch (IllegalArgumentException e) {
            showVipStatus("✗  " + e.getMessage(), ParkingApp.DANGER);
        }
    }

    private void handleRemoveVip() {
        var sel = vipTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showVipStatus("Select a tier to remove.", ParkingApp.WARNING); return; }
        vipRepo.delete(sel.tierId);
        refreshVipTable();
        showVipStatus("✓  Tier '" + sel.tierName + "' removed.", ParkingApp.SUCCESS);
    }

    private void handleToggleVip() {
        var sel = vipTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showVipStatus("Select a tier to toggle.", ParkingApp.WARNING); return; }
        vipRepo.setActive(sel.tierId, !sel.isActive);
        refreshVipTable();
        showVipStatus("✓  Tier '" + sel.tierName + "' is now " + (sel.isActive ? "inactive" : "active") + ".",
                ParkingApp.SUCCESS);
    }

    private void showVipStatus(String msg, String color) {
        vipStatusLabel.setText(msg);
        vipStatusLabel.setTextFill(Color.web(color));
        vipStatusLabel.setStyle(
                "-fx-background-color: " + color + "18;" +
                        "-fx-background-radius: 8;" +
                        "-fx-border-color: "     + color + "44;" +
                        "-fx-border-radius: 8; -fx-border-width: 1;");
        Animations.statusSlideIn(vipStatusLabel);
    }

    // ════════════════════════════════════════════════════════════════════════
    // F — SESSION STATS CARD (with charts)
    // ════════════════════════════════════════════════════════════════════════

    private VBox buildStatsCard() {
        VBox card = ParkingApp.pageCard("Session Statistics");
        card.setMinWidth(300);
        card.setMaxWidth(380);
        card.setSpacing(16);

        List<Ticket> history = ParkingApp.TICKET_MANAGER.getSessionHistory();
        double revenue = SessionStats.totalRevenue(history);
        int    tickets = history.size();
        double avg     = SessionStats.averageFee(history);
        int    busiest = SessionStats.busiestEntryHour(history);

        VBox statRows = new VBox(0);
        statRows.setStyle("-fx-background-color: " + ParkingApp.BG_RAISED + "; -fx-background-radius: 10;");
        statRevenue     = summaryValue(String.format("%.2f", revenue), ParkingApp.SUCCESS);
        statTickets     = summaryValue(String.valueOf(tickets),         ParkingApp.ACCENT);
        statAvgFee      = summaryValue(String.format("%.2f", avg),     ParkingApp.INFO);
        statBusiestHour = summaryValue(busiest >= 0 ? busiest + ":00" : "—", ParkingApp.WARNING);
        statRows.getChildren().addAll(
                summaryRow("Total Revenue", statRevenue,     false),
                summaryRow("Completed",     statTickets,     true),
                summaryRow("Average Fee",   statAvgFee,      false),
                summaryRow("Busiest Hour",  statBusiestHour, true)
        );

        Label barTitle = new Label("REVENUE BY HOUR");
        barTitle.setFont(Font.font("System", FontWeight.BOLD, 10));
        barTitle.setTextFill(Color.web(ParkingApp.TEXT_M));

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis   yAxis = new NumberAxis();
        xAxis.setTickLabelFill(Color.web(ParkingApp.TEXT_M));
        xAxis.setTickLabelFont(Font.font("System", 10));
        yAxis.setTickLabelFill(Color.web(ParkingApp.TEXT_M));
        yAxis.setTickLabelFont(Font.font("System", 10));
        yAxis.setLabel(""); xAxis.setLabel("");

        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        barChart.setLegendVisible(false);
        barChart.setAnimated(false);
        barChart.setPrefHeight(160);
        barChart.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        barChart.setBarGap(2);
        barChart.setCategoryGap(4);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        double[] revenueByHour = SessionStats.revenueByHour(history);
        for (int h = 0; h < 24; h++) {
            if (revenueByHour[h] > 0)
                series.getData().add(new XYChart.Data<>(h + ":00", revenueByHour[h]));
        }
        if (series.getData().isEmpty())
            series.getData().add(new XYChart.Data<>("—", 0));
        barChart.getData().add(series);

        javafx.application.Platform.runLater(() ->
                barChart.lookupAll(".bar").forEach(n ->
                        n.setStyle("-fx-bar-fill: " + ParkingApp.ACCENT + ";")));

        Label pieTitle = new Label("SPOT TYPE USAGE");
        pieTitle.setFont(Font.font("System", FontWeight.BOLD, 10));
        pieTitle.setTextFill(Color.web(ParkingApp.TEXT_M));

        PieChart pieChart = new PieChart();
        pieChart.setLegendVisible(true);
        pieChart.setAnimated(false);
        pieChart.setLabelsVisible(false);
        pieChart.setPrefHeight(180);
        pieChart.setStyle("-fx-background-color: transparent;");

        ParkingLot lot = ParkingLot.getInstance();
        long motorcycle = lot.getAllSpots().stream().filter(s -> s.getSpotType() == SpotType.MOTORCYCLE).count();
        long compact    = lot.getAllSpots().stream().filter(s -> s.getSpotType() == SpotType.COMPACT).count();
        long standard   = lot.getAllSpots().stream().filter(s -> s.getSpotType() == SpotType.STANDARD).count();
        long large      = lot.getAllSpots().stream().filter(s -> s.getSpotType() == SpotType.LARGE).count();

        if (motorcycle > 0) pieChart.getData().add(new PieChart.Data("Motorcycle " + motorcycle, motorcycle));
        if (compact    > 0) pieChart.getData().add(new PieChart.Data("Compact "    + compact,    compact));
        if (standard   > 0) pieChart.getData().add(new PieChart.Data("Standard "   + standard,   standard));
        if (large      > 0) pieChart.getData().add(new PieChart.Data("Large "      + large,      large));

        String[] pieColors = { ParkingApp.ACCENT, ParkingApp.SUCCESS, ParkingApp.INFO, ParkingApp.WARNING };
        javafx.application.Platform.runLater(() -> {
            var slices = pieChart.getData();
            for (int i = 0; i < slices.size(); i++) {
                slices.get(i).getNode().setStyle("-fx-pie-color: " + pieColors[i % pieColors.length] + ";");
            }
        });

        long avail    = lot.getAvailableCount();
        long occupied = lot.getOccupiedCount();
        int  total    = lot.getTotalSpots();
        double pct    = lot.getOccupancyRate();

        VBox lotBox = new VBox(8);
        lotBox.setPadding(new Insets(14));
        lotBox.setStyle(
                "-fx-background-color: " + ParkingApp.BG_RAISED + ";" +
                        "-fx-background-radius: 10;" +
                        "-fx-border-color: "     + ParkingApp.BORDER + ";" +
                        "-fx-border-radius: 10;" +
                        "-fx-border-width: 1;");
        Label lotTitle2 = new Label("LOT STATUS");
        lotTitle2.setFont(Font.font("System", FontWeight.BOLD, 10));
        lotTitle2.setTextFill(Color.web(ParkingApp.TEXT_M));
        String occColor = pct >= 90 ? ParkingApp.DANGER : pct >= 75 ? ParkingApp.WARNING : ParkingApp.SUCCESS;
        Label lotVal = new Label(String.format("%.0f%% occupied", pct));
        lotVal.setFont(Font.font("Georgia", FontWeight.BOLD, 22));
        lotVal.setTextFill(Color.web(occColor));
        Label lotDetail = new Label(occupied + " / " + total + " spots used  •  " + avail + " free");
        lotDetail.setFont(Font.font("System", 12));
        lotDetail.setTextFill(Color.web(ParkingApp.TEXT_M));
        lotBox.getChildren().addAll(lotTitle2, lotVal, lotDetail);

        Button exportBtn  = ParkingApp.primaryBtn("📄  Export Tickets to TXT", ParkingApp.INFO);
        Button refreshBtn = ParkingApp.ghostBtn("↻  Refresh Stats");
        exportBtn.setOnAction(e -> {
            Animations.buttonPulse(exportBtn);
            ParkingApp.TICKET_MANAGER.saveSession();
            showStatus("✓  Exported to tickets_log.txt in project root.", ParkingApp.SUCCESS);
        });
        refreshBtn.setOnAction(e -> refreshStats());

        card.getChildren().addAll(
                statRows, barTitle, barChart, pieTitle, pieChart,
                lotBox, exportBtn, refreshBtn);
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
    // G — CUSTOMER MANAGEMENT CARD (full-width)
    // ════════════════════════════════════════════════════════════════════════

    private VBox buildCustomerCard() {
        VBox card = ParkingApp.pageCard("Customer Management");
        card.setSpacing(16);

        // TableFactory provides the search bar — no need for a separate one
        ObservableList<CustomerRepository.CustomerRecord> custData =
                FXCollections.observableArrayList(custRepo.findAll());
        TableFactory.Result<CustomerRepository.CustomerRecord> custResult = TableFactory.build(
                custData,
                "Search by name, plate, or phone…",
                (r, q) -> r.fullName.toLowerCase().contains(q)
                        || r.licensePlate.toLowerCase().contains(q)
                        || r.phone.toLowerCase().contains(q));
        custTable = custResult.table();
        TableFactory.setVisibleRows(custTable, 8);

        custTable.getColumns().addAll(
                TableFactory.textCol("#",     r -> String.valueOf(r.customerId), 50),
                TableFactory.textCol("Full Name",      r -> r.fullName,      180),
                TableFactory.textCol("Phone",          r -> r.phone,         140),
                TableFactory.dotCol("License Plate",
                        r -> r.licensePlate,
                        r -> switch (r.vehicleType.toUpperCase()) {
                            case "MOTORCYCLE" -> ParkingApp.ACCENT;
                            case "TRUCK"      -> ParkingApp.WARNING;
                            default           -> ParkingApp.INFO;
                        }, 150),
                TableFactory.badgeCol("Vehicle",
                        r -> r.vehicleType,
                        r -> switch (r.vehicleType.toUpperCase()) {
                            case "MOTORCYCLE" -> ParkingApp.ACCENT;
                            case "TRUCK"      -> ParkingApp.WARNING;
                            default           -> ParkingApp.INFO;
                        }, 120),
                TableFactory.textCol("First Visit",
                        r -> r.createdAt != null ? r.createdAt.substring(0, 10) : "—", 120)
        );
        refreshCustTable(null);

        VBox editPanel = new VBox(12);
        editPanel.setPadding(new Insets(16));
        editPanel.setVisible(false);
        editPanel.setManaged(false);
        editPanel.setStyle(
                "-fx-background-color: " + ParkingApp.ACCENT + "10;" +
                        "-fx-background-radius: 10;" +
                        "-fx-border-color: "     + ParkingApp.ACCENT + "33;" +
                        "-fx-border-radius: 10;" +
                        "-fx-border-width: 1;");

        Label editTitle = new Label("EDIT SELECTED CUSTOMER");
        editTitle.setFont(Font.font("System", FontWeight.BOLD, 11));
        editTitle.setTextFill(Color.web(ParkingApp.ACCENT));

        TextField editNameField  = ParkingApp.styledField("Full name");
        TextField editPhoneField = ParkingApp.styledField("Phone number");

        HBox editRow = new HBox(10);
        HBox.setHgrow(editNameField, Priority.ALWAYS);
        editPhoneField.setMaxWidth(200);
        editRow.getChildren().addAll(editNameField, editPhoneField);

        Button saveEditBtn   = ParkingApp.primaryBtn("Save Changes  ✓", ParkingApp.ACCENT);
        Button cancelEditBtn = ParkingApp.ghostBtn("Cancel");
        HBox editBtnRow = new HBox(10);
        HBox.setHgrow(saveEditBtn, Priority.ALWAYS);
        editBtnRow.getChildren().addAll(saveEditBtn, cancelEditBtn);
        editPanel.getChildren().addAll(editTitle, editRow, editBtnRow);

        custTable.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) -> {
            if (sel != null) {
                editNameField.setText(sel.fullName);
                editPhoneField.setText(sel.phone);
                editPanel.setVisible(true);
                editPanel.setManaged(true);
            }
        });

        saveEditBtn.setOnAction(e -> {
            var sel = custTable.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            String newName  = editNameField.getText().trim();
            String newPhone = editPhoneField.getText().trim();
            if (newName.isBlank()) { showCustStatus("Name cannot be empty.", ParkingApp.DANGER); return; }
            custRepo.updateCustomerInfo(sel.licensePlate, newName, newPhone);
            refreshCustTable(null);
            editPanel.setVisible(false);
            editPanel.setManaged(false);
            custTable.getSelectionModel().clearSelection();
            showCustStatus("✓  Customer '" + newName + "' updated.", ParkingApp.SUCCESS);
        });

        cancelEditBtn.setOnAction(e -> {
            editPanel.setVisible(false);
            editPanel.setManaged(false);
            custTable.getSelectionModel().clearSelection();
        });

        custStatusLabel = new Label("Total: " + custRepo.totalCustomers() + " customers");
        custStatusLabel.setFont(Font.font("System", 12));
        custStatusLabel.setTextFill(Color.web(ParkingApp.TEXT_M));

        card.getChildren().addAll(custResult.container(), editPanel, custStatusLabel);
        return card;
    }

    private void refreshCustTable(String ignored) {
        if (custTable == null) return;
        getSourceList(custTable).setAll(custRepo.findAll());
        if (custStatusLabel != null)
            custStatusLabel.setText("Total: " + custRepo.totalCustomers() + " customer(s)");
    }

    private void showCustStatus(String msg, String color) {
        custStatusLabel.setText(msg);
        custStatusLabel.setTextFill(Color.web(color));
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

            calc().setBaseRatePerHour(rate);
            calc().setDailyMaxRate(cap);
            calc().setGracePeriodMinutes(grace);
            calc().setDiscountPercent(discount);
            calc().setLostTicketFee(lost);

            ParkingApp.TICKET_MANAGER.saveFeeConfig();

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

    @SafeVarargs
    private <T> void styleTableColumns(String headerColor, TableColumn<T, String>... cols) {
        for (TableColumn<T, String> col : cols) {
            col.setStyle("-fx-alignment: CENTER-LEFT;");
            col.setSortable(true);
        }
    }

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
            field.setStyle(field.getStyle().replace(ParkingApp.DANGER, ParkingApp.SUCCESS));
            return v;
        } catch (NumberFormatException e) {
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