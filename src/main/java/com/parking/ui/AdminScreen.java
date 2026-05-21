package com.parking.ui;

import com.parking.service.FeeCalculator;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.*;

/**
 * Admin panel — lets the operator change pricing rules at runtime.
 *
 * <p>All changes call the setters on {@link FeeCalculator} directly,
 * so they take effect immediately for the next ticket payment.</p>
 */
public class AdminScreen {

    private final ParkingApp   app;
    private final FeeCalculator calc;

    // Current-value display labels (updated after save)
    private Label currentRate;
    private Label currentCap;
    private Label currentGrace;
    private Label currentDiscount;
    private Label currentLost;

    // Input fields
    private TextField rateField;
    private TextField capField;
    private TextField graceField;
    private TextField discountField;
    private TextField lostField;

    // Status banner
    private Label statusLabel;

    public AdminScreen(ParkingApp app) {
        this.app  = app;
        this.calc = ParkingApp.TICKET_MANAGER.getFeeCalculator();
    }

    Node build() {
        ScrollPane sp = new ScrollPane();
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: " + ParkingApp.BG_BASE +
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
        Label sub = new Label("Configure pricing rules. Changes apply immediately to the next payment.");
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

        // ── Main content row ──
        HBox content = new HBox(20);
        var pricingCard = buildPricingCard();
        var summaryCard = buildSummaryCard();
        content.getChildren().addAll(pricingCard, summaryCard);

        page.getChildren().addAll(header, statusLabel, content);
        sp.setContent(page);

        javafx.application.Platform.runLater(() ->
                Animations.staggerCards(60, 100, pricingCard, summaryCard));

        return sp;
    }

    // ── Left: pricing form ────────────────────────────────────────────────

    private VBox buildPricingCard() {
        VBox card = ParkingApp.pageCard("Pricing Configuration");
        card.setMinWidth(400);
        card.setMaxWidth(480);
        card.setSpacing(20);

        // Base hourly rate
        rateField = ParkingApp.styledField(String.valueOf(calc.getBaseRatePerHour()));
        rateField.setText(String.valueOf(calc.getBaseRatePerHour()));
        card.getChildren().add(buildSettingRow(
                "💵", "Base Hourly Rate (USD)",
                "Charged per hour for a standard car.",
                rateField, ParkingApp.ACCENT
        ));

        card.getChildren().add(makeDivider());

        // Daily maximum cap
        capField = ParkingApp.styledField(String.valueOf(calc.getDailyMaxRate()));
        capField.setText(String.valueOf(calc.getDailyMaxRate()));
        card.getChildren().add(buildSettingRow(
                "📆", "Daily Maximum Cap (USD)",
                "Maximum charged per 24-hour period, regardless of duration.",
                capField, ParkingApp.INFO
        ));

        card.getChildren().add(makeDivider());

        // Grace period
        graceField = ParkingApp.styledField(String.valueOf(calc.getGracePeriodMinutes()));
        graceField.setText(String.valueOf(calc.getGracePeriodMinutes()));
        card.getChildren().add(buildSettingRow(
                "⏱", "Grace Period (minutes)",
                "Vehicles parked shorter than this window are charged nothing.",
                graceField, ParkingApp.SUCCESS
        ));

        card.getChildren().add(makeDivider());

        // Discount
        discountField = ParkingApp.styledField(String.valueOf(calc.getDiscountPercent()));
        discountField.setText(String.valueOf(calc.getDiscountPercent()));
        card.getChildren().add(buildSettingRow(
                "🏷", "Global Discount (%)",
                "Applied to every ticket. Set to 0 for no discount.",
                discountField, ParkingApp.WARNING
        ));

        card.getChildren().add(makeDivider());

        // Lost ticket fee
        lostField = ParkingApp.styledField(String.valueOf(calc.getLostTicketFee()));
        lostField.setText(String.valueOf(calc.getLostTicketFee()));
        card.getChildren().add(buildSettingRow(
                "🎫", "Lost Ticket Flat Fee (USD)",
                "Flat fee charged when a customer loses their ticket.",
                lostField, ParkingApp.DANGER
        ));

        card.getChildren().add(makeDivider());

        // Buttons
        Button saveBtn  = ParkingApp.primaryBtn("Save Changes  ✓", ParkingApp.ACCENT);
        Button resetBtn = ParkingApp.ghostBtn("Reset to Defaults");
        saveBtn.setOnAction(e  -> { Animations.buttonPulse(saveBtn); handleSave(); });
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

    // ── Right: live summary card ───────────────────────────────────────────

    private VBox buildSummaryCard() {
        VBox card = ParkingApp.pageCard("Current Settings");
        card.setMinWidth(260);
        card.setMaxWidth(300);

        currentRate     = summaryValue(fmt(calc.getBaseRatePerHour()) + " USD/hr", ParkingApp.ACCENT);
        currentCap      = summaryValue(fmt(calc.getDailyMaxRate())    + " USD/day", ParkingApp.INFO);
        currentGrace    = summaryValue(calc.getGracePeriodMinutes()   + " min",     ParkingApp.SUCCESS);
        currentDiscount = summaryValue(fmt(calc.getDiscountPercent())  + "%",        ParkingApp.WARNING);
        currentLost     = summaryValue(fmt(calc.getLostTicketFee())    + " USD",     ParkingApp.DANGER);

        VBox rows = new VBox(0);
        rows.setStyle(
                "-fx-background-color: " + ParkingApp.BG_RAISED + ";" +
                        "-fx-background-radius: 10;"
        );
        rows.getChildren().addAll(
                summaryRow("Base Rate",     currentRate,     false),
                summaryRow("Daily Cap",     currentCap,      true),
                summaryRow("Grace Period",  currentGrace,    false),
                summaryRow("Discount",      currentDiscount, true),
                summaryRow("Lost Ticket",   currentLost,     false)
        );

        // Fee example calculator
        VBox exampleBox = new VBox(8);
        exampleBox.setPadding(new Insets(16));
        exampleBox.setStyle(
                "-fx-background-color: " + ParkingApp.ACCENT + "12;" +
                        "-fx-background-radius: 10;" +
                        "-fx-border-color: " + ParkingApp.ACCENT + "33;" +
                        "-fx-border-radius: 10;" +
                        "-fx-border-width: 1;"
        );
        Label exTitle = new Label("EXAMPLE");
        exTitle.setFont(Font.font("System", FontWeight.BOLD, 10));
        exTitle.setTextFill(Color.web(ParkingApp.ACCENT));

        Label exDesc = new Label("A car parked for 3 hours pays:");
        exDesc.setFont(Font.font("System", 12));
        exDesc.setTextFill(Color.web(ParkingApp.TEXT_M));
        exDesc.setWrapText(true);

        Label exVal = buildExampleFee();
        exampleBox.getChildren().addAll(exTitle, exDesc, exVal);

        card.getChildren().addAll(rows, exampleBox);
        return card;
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

    private Label buildExampleFee() {
        double rate = calc.getBaseRatePerHour();
        double fee  = 3 * rate * 1.0; // 3 hours, car multiplier = 1.0
        if (calc.getDiscountPercent() > 0) fee = calc.applyDiscount(fee, calc.getDiscountPercent());
        fee = Math.min(fee, calc.getDailyMaxRate());

        Label l = new Label(String.format("%.2f USD", fee));
        l.setFont(Font.font("Georgia", FontWeight.BOLD, 26));
        l.setTextFill(Color.web(ParkingApp.TEXT_H));
        return l;
    }

    // ── Handlers ──────────────────────────────────────────────────────────

    private void handleSave() {
        try {
            double rate     = parseDouble(rateField,     "Base Hourly Rate");
            double cap      = parseDouble(capField,      "Daily Maximum Cap");
            int    grace    = parseInt(graceField,       "Grace Period");
            double discount = parseDouble(discountField, "Global Discount");
            double lost     = parseDouble(lostField,     "Lost Ticket Fee");

            calc.setBaseRatePerHour(rate);
            calc.setDailyMaxRate(cap);
            calc.setGracePeriodMinutes(grace);
            calc.setDiscountPercent(discount);
            calc.setLostTicketFee(lost);

            // Refresh summary labels
            currentRate.setText(fmt(rate)     + " USD/hr");
            currentCap.setText(fmt(cap)       + " USD/day");
            currentGrace.setText(grace        + " min");
            currentDiscount.setText(fmt(discount) + "%");
            currentLost.setText(fmt(lost)     + " USD");

            // Persist to database so config survives restarts
            ParkingApp.TICKET_MANAGER.persistFeeConfig();

            showStatus("✓  Settings saved successfully! New rates apply from the next ticket.", ParkingApp.SUCCESS);

        } catch (IllegalArgumentException e) {
            showStatus("✗  " + e.getMessage(), ParkingApp.DANGER);
        }
    }

    private void handleReset() {
        FeeCalculator defaults = new FeeCalculator();
        rateField.setText(String.valueOf(defaults.getBaseRatePerHour()));
        capField.setText(String.valueOf(defaults.getDailyMaxRate()));
        graceField.setText(String.valueOf(defaults.getGracePeriodMinutes()));
        discountField.setText(String.valueOf(defaults.getDiscountPercent()));
        lostField.setText(String.valueOf(defaults.getLostTicketFee()));
        showStatus("ℹ  Fields reset to defaults. Press Save to apply.", ParkingApp.INFO);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private double parseDouble(TextField field, String name) {
        try {
            double v = Double.parseDouble(field.getText().trim());
            if (v < 0) throw new IllegalArgumentException(name + " cannot be negative.");
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + ": please enter a valid number.");
        }
    }

    private int parseInt(TextField field, String name) {
        try {
            int v = Integer.parseInt(field.getText().trim());
            if (v < 0) throw new IllegalArgumentException(name + " cannot be negative.");
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + ": please enter a whole number.");
        }
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

    private Separator makeDivider() {
        Separator s = new Separator();
        s.setStyle("-fx-background-color: " + ParkingApp.BORDER + ";");
        return s;
    }

    private String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
    }
}