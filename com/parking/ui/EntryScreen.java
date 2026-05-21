package com.parking.ui;

import com.parking.enums.VehicleType;
import com.parking.model.Ticket;
import com.parking.model.Vehicle;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.*;

public class EntryScreen {

    private final ParkingApp app;

    private TextField   plateField;
    private ToggleGroup typeGroup;
    private Label       statusLabel;
    private VBox        resultCard;

    public EntryScreen(ParkingApp app) { this.app = app; }

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
        Label icon = new Label("🚗");
        icon.setFont(Font.font("System", 28));
        VBox titles = new VBox(2);
        Label title = ParkingApp.pageTitle("Vehicle Entry");
        Label sub   = new Label("Record a new arrival, assign a spot, and issue a ticket.");
        sub.setFont(Font.font("System", 13));
        sub.setTextFill(Color.web(ParkingApp.TEXT_M));
        titles.getChildren().addAll(title, sub);
        header.getChildren().addAll(icon, titles);

        // Main row — both cards grow equally
        HBox mainRow = new HBox(20);
        mainRow.setFillHeight(true);
        var formCard   = buildFormCard();
        var resultCard2 = buildResultCard();
        HBox.setHgrow(formCard,    Priority.ALWAYS);
        HBox.setHgrow(resultCard2, Priority.ALWAYS);
        formCard.setMaxWidth(Double.MAX_VALUE);
        resultCard2.setMaxWidth(Double.MAX_VALUE);
        mainRow.getChildren().addAll(formCard, resultCard2);

        page.getChildren().addAll(header, mainRow);
        sp.setContent(page);

        // stagger form card and result card entrance
        javafx.application.Platform.runLater(() ->
                Animations.staggerCards(60, 80, formCard, resultCard2));

        return sp;
    }

    // ── Form card ─────────────────────────────────────────────────────────

    private VBox buildFormCard() {
        VBox card = ParkingApp.pageCard("New Entry");
        card.setMaxWidth(Double.MAX_VALUE);

        // License plate field
        plateField = ParkingApp.styledField("e.g.  34 ABC 001");
        VBox plateGroup = ParkingApp.fieldGroup("LICENSE PLATE", plateField);

        // Vehicle type selector
        Label typeLabel = new Label("VEHICLE TYPE");
        typeLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        typeLabel.setTextFill(Color.web(ParkingApp.TEXT_M));

        typeGroup = new ToggleGroup();
        HBox typeRow = new HBox(10);
        typeRow.getChildren().addAll(
                typeChip("🚗  Car",          VehicleType.CAR,        true),
                typeChip("🏍  Motorcycle",   VehicleType.MOTORCYCLE, false),
                typeChip("🚛  Truck",        VehicleType.TRUCK,      false)
        );

        VBox typeGroup2 = new VBox(6);
        typeGroup2.getChildren().addAll(typeLabel, typeRow);

        // Status
        statusLabel = new Label("");
        statusLabel.setFont(Font.font("System", 12));
        statusLabel.setWrapText(true);
        statusLabel.setVisible(false);
        statusLabel.setPadding(new Insets(10, 14, 10, 14));

        // Buttons
        Button submit = ParkingApp.primaryBtn("Issue Ticket  →", ParkingApp.ACCENT);
        submit.setOnAction(e -> { Animations.buttonPulse(submit); handleIssue(); });

        Button clear = ParkingApp.ghostBtn("Clear");
        clear.setOnAction(e -> {
            plateField.clear();
            statusLabel.setVisible(false);
            resultCard.setVisible(false);
        });

        HBox btnRow = new HBox(10);
        HBox.setHgrow(submit, Priority.ALWAYS);
        btnRow.getChildren().addAll(submit, clear);

        card.getChildren().addAll(plateGroup, typeGroup2, statusLabel, btnRow);
        return card;
    }

    // ── Result card ───────────────────────────────────────────────────────

    private VBox buildResultCard() {
        resultCard = ParkingApp.pageCard("Issued Ticket");
        resultCard.setMinWidth(320);
        resultCard.setVisible(false);

        // placeholder — populated in handleIssue()
        Label placeholder = new Label("Ticket details will appear here.");
        placeholder.setFont(Font.font("System", 13));
        placeholder.setTextFill(Color.web(ParkingApp.TEXT_M));
        resultCard.getChildren().add(placeholder);

        return resultCard;
    }

    // ── Handler ───────────────────────────────────────────────────────────

    private void handleIssue() {
        String plate = plateField.getText().trim().toUpperCase();
        if (plate.isBlank()) { showStatus("Please enter a license plate.", ParkingApp.DANGER); return; }

        VehicleType type = (VehicleType) typeGroup.getSelectedToggle().getUserData();

        try {
            Vehicle vehicle = new Vehicle(plate, type);
            Ticket  ticket  = ParkingApp.TICKET_MANAGER.issueTicket(vehicle);

            showStatus("✓  Ticket issued successfully!", ParkingApp.SUCCESS);
            populateResult(ticket);
            Animations.cardPop(resultCard);
            plateField.clear();

        } catch (IllegalStateException e) {
            showStatus("✗  " + e.getMessage(), ParkingApp.DANGER);
        }
    }

    private void populateResult(Ticket t) {
        resultCard.getChildren().clear();

        // Title row
        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Issued Ticket");
        title.setFont(Font.font("Georgia", FontWeight.BOLD, 17));
        title.setTextFill(Color.web(ParkingApp.TEXT_H));
        Label badge = ParkingApp.statusBadge("ACTIVE", ParkingApp.SUCCESS);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        titleRow.getChildren().addAll(title, sp, badge);

        Separator sep = new Separator();

        // Ticket ID highlight box
        VBox idBox = new VBox(4);
        idBox.setPadding(new Insets(14, 16, 14, 16));
        idBox.setStyle(
                "-fx-background-color: " + ParkingApp.ACCENT + "18;" +
                        "-fx-background-radius: 10;" +
                        "-fx-border-color: " + ParkingApp.ACCENT + "44;" +
                        "-fx-border-radius: 10;" +
                        "-fx-border-width: 1;"
        );
        Label idLbl = new Label("TICKET ID");
        idLbl.setFont(Font.font("System", FontWeight.BOLD, 10));
        idLbl.setTextFill(Color.web(ParkingApp.ACCENT));
        Label idVal = new Label(t.getTicketId());
        idVal.setFont(Font.font("Monospaced", FontWeight.BOLD, 20));
        idVal.setTextFill(Color.web(ParkingApp.TEXT_H));
        Label idNote = new Label("⚠  Save this ID — you'll need it to pay and exit.");
        idNote.setFont(Font.font("System", 11));
        idNote.setTextFill(Color.web(ParkingApp.WARNING));
        idBox.getChildren().addAll(idLbl, idVal, idNote);

        // Detail rows
        VBox details = new VBox(0);
        details.setStyle(
                "-fx-background-color: " + ParkingApp.BG_RAISED + ";" +
                        "-fx-background-radius: 10;"
        );
        details.getChildren().addAll(
                detailRow("License Plate", t.getVehicle().getLicensePlate(), false),
                detailRow("Vehicle Type",  t.getVehicle().getType().getDisplayName(), true),
                detailRow("Assigned Spot", t.getSpot().getSpotId(), false),
                detailRow("Floor",         "Floor " + t.getSpot().getFloor(), true),
                detailRow("Entry Time",    t.getIssuedAt().toLocalTime().toString(), false)
        );

        resultCard.getChildren().addAll(titleRow, sep, idBox, details);
        resultCard.setVisible(true);
    }

    private HBox detailRow(String key, String value, boolean shaded) {
        HBox row = new HBox();
        row.setPadding(new Insets(11, 16, 11, 16));
        row.setAlignment(Pos.CENTER_LEFT);
        if (shaded) row.setStyle("-fx-background-color: " + ParkingApp.BG_SURFACE + "55;");

        Label keyLbl = new Label(key);
        keyLbl.setFont(Font.font("System", 13));
        keyLbl.setTextFill(Color.web(ParkingApp.TEXT_M));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label valLbl = new Label(value);
        valLbl.setFont(Font.font("System", FontWeight.BOLD, 13));
        valLbl.setTextFill(Color.web(ParkingApp.TEXT_H));

        row.getChildren().addAll(keyLbl, spacer, valLbl);
        return row;
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

    // ── Type chip toggle ──────────────────────────────────────────────────

    private ToggleButton typeChip(String text, VehicleType type, boolean selected) {
        ToggleButton tb = new ToggleButton(text);
        tb.setToggleGroup(typeGroup);
        tb.setSelected(selected);
        tb.setUserData(type);
        tb.setCursor(javafx.scene.Cursor.HAND);
        tb.setFont(Font.font("System", 13));

        String baseStyle =
                "-fx-background-radius: 8;" +
                        "-fx-padding: 8 16 8 16;" +
                        "-fx-cursor: hand;";

        Runnable applyStyle = () -> {
            if (tb.isSelected()) {
                tb.setStyle(baseStyle +
                        "-fx-background-color: " + ParkingApp.ACCENT + ";" +
                        "-fx-text-fill: white;" +
                        "-fx-border-color: " + ParkingApp.ACCENT + ";" +
                        "-fx-border-radius: 8;" +
                        "-fx-border-width: 1;");
            } else {
                tb.setStyle(baseStyle +
                        "-fx-background-color: " + ParkingApp.BG_RAISED + ";" +
                        "-fx-text-fill: " + ParkingApp.TEXT_M + ";" +
                        "-fx-border-color: " + ParkingApp.BORDER_LIT + ";" +
                        "-fx-border-radius: 8;" +
                        "-fx-border-width: 1;");
            }
        };
        applyStyle.run();
        tb.selectedProperty().addListener((obs, o, n) -> applyStyle.run());
        typeGroup.selectedToggleProperty().addListener((obs, o, n) -> applyStyle.run());
        return tb;
    }
}