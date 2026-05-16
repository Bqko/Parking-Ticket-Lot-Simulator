package com.parking.ui;

import com.parking.enums.VehicleType;
import com.parking.model.Ticket;
import com.parking.model.Vehicle;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

/**
 * Screen 1 — Vehicle Entry.
 *
 * <p>Presents a form for entering the license plate and vehicle type.
 * On submit it calls {@code TicketManager.issueTicket()} and displays
 * the resulting ticket details (ID, assigned spot, entry time).</p>
 */
public class EntryScreen {

    private final ParkingApp app;

    // Form fields kept as instance vars so the result panel can read them
    private TextField  plateField;
    private ToggleGroup typeGroup;
    private VBox       resultPanel;
    private Label      resultText;
    private Label      statusLabel;

    public EntryScreen(ParkingApp app) {
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

        Label title = new Label("🚗  Vehicle Entry");
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
        body.getChildren().addAll(buildForm(), buildResultPanel());
        return body;
    }

    // ── Left: entry form ───────────────────────────────────────────────────

    private VBox buildForm() {
        VBox form = new VBox(18);
        form.setPrefWidth(400);
        form.setPadding(new Insets(30));
        form.setStyle(
                "-fx-background-color: " + ParkingApp.BG_CARD + ";" +
                        "-fx-background-radius: 12;"
        );

        Label heading = ParkingApp.sectionTitle("New Vehicle Entry");

        // ── License Plate ──
        Label plateLabel = ParkingApp.makeFieldLabel("LICENSE PLATE");
        plateField = new TextField();
        plateField.setPromptText("e.g.  34 ABC 001");
        styleTextField(plateField);

        // ── Vehicle Type ──
        Label typeLabel = ParkingApp.makeFieldLabel("VEHICLE TYPE");
        typeGroup = new ToggleGroup();

        HBox typeRow = new HBox(12);
        typeRow.getChildren().addAll(
                makeTypeToggle("🚗  Car",          VehicleType.CAR,        true),
                makeTypeToggle("🏍  Motorcycle",   VehicleType.MOTORCYCLE, false),
                makeTypeToggle("🚛  Truck",        VehicleType.TRUCK,      false)
        );

        // ── Status / error message ──
        statusLabel = new Label("");
        statusLabel.setFont(Font.font("System", 12));
        statusLabel.setWrapText(true);

        // ── Submit ──
        Button submit = ParkingApp.makeButton("Issue Ticket  →", ParkingApp.BLUE);
        submit.setPrefWidth(340);
        submit.setOnAction(e -> handleIssue());

        form.getChildren().addAll(
                heading,
                new Separator(),
                plateLabel, plateField,
                typeLabel,  typeRow,
                statusLabel,
                submit
        );
        return form;
    }

    private RadioButton makeTypeToggle(String text, VehicleType type, boolean selected) {
        RadioButton rb = new RadioButton(text);
        rb.setToggleGroup(typeGroup);
        rb.setSelected(selected);
        rb.setUserData(type);
        rb.setStyle("-fx-text-fill: " + ParkingApp.TEXT_WHITE + "; -fx-font-size: 13px;");
        return rb;
    }

    // ── Right: result panel ────────────────────────────────────────────────

    private VBox buildResultPanel() {
        resultPanel = new VBox(14);
        resultPanel.setPrefWidth(380);
        resultPanel.setPadding(new Insets(30));
        resultPanel.setAlignment(Pos.TOP_CENTER);
        resultPanel.setStyle(
                "-fx-background-color: " + ParkingApp.BG_CARD + ";" +
                        "-fx-background-radius: 12;" +
                        "-fx-opacity: 0.4;"
        );

        Label icon = new Label("🎫");
        icon.setFont(Font.font("System", 48));

        Label waiting = new Label("Ticket details will\nappear here after entry");
        waiting.setFont(Font.font("System", 14));
        waiting.setTextFill(Color.web(ParkingApp.TEXT_MUTED));
        waiting.setAlignment(Pos.CENTER);
        waiting.setWrapText(true);

        resultText = new Label("");
        resultText.setFont(Font.font("Monospaced", 13));
        resultText.setTextFill(Color.web(ParkingApp.TEXT_WHITE));
        resultText.setWrapText(true);

        resultPanel.getChildren().addAll(icon, waiting, resultText);
        return resultPanel;
    }

    // ── Submit handler ─────────────────────────────────────────────────────

    private void handleIssue() {
        String plate = plateField.getText().trim().toUpperCase();

        if (plate.isEmpty()) {
            setStatus("❌  Please enter a license plate.", ParkingApp.RED);
            return;
        }

        VehicleType type = (VehicleType) typeGroup.getSelectedToggle().getUserData();

        try {
            Vehicle vehicle = new Vehicle(plate, type);
            Ticket  ticket  = ParkingApp.TICKET_MANAGER.issueTicket(vehicle);

            // Show result panel
            resultPanel.setStyle(
                    "-fx-background-color: " + ParkingApp.BG_CARD + ";" +
                            "-fx-background-radius: 12;" +
                            "-fx-border-color: " + ParkingApp.GREEN + ";" +
                            "-fx-border-radius: 12;" +
                            "-fx-border-width: 2;"
            );

            resultText.setText(
                    "✅  Ticket Issued!\n\n" +
                            "Ticket ID  :  " + ticket.getTicketId()          + "\n" +
                            "Plate      :  " + ticket.getVehicle().getLicensePlate() + "\n" +
                            "Type       :  " + ticket.getVehicle().getType().getDisplayName() + "\n" +
                            "Spot       :  " + ticket.getSpot().getSpotId()  + "\n" +
                            "Floor      :  " + ticket.getSpot().getFloor()   + "\n" +
                            "Entry time :  " + ticket.getIssuedAt().toLocalTime() + "\n\n" +
                            "⚠  Save the Ticket ID — you need\n   it to pay and exit."
            );

            setStatus("✅  Ticket issued successfully!", ParkingApp.GREEN);
            plateField.clear();

        } catch (IllegalStateException e) {
            setStatus("❌  " + e.getMessage(), ParkingApp.RED);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void setStatus(String msg, String color) {
        statusLabel.setText(msg);
        statusLabel.setTextFill(Color.web(color));
    }

    private void styleTextField(TextField tf) {
        tf.setStyle(
                "-fx-background-color: " + ParkingApp.BG_DARK + ";" +
                        "-fx-text-fill: " + ParkingApp.TEXT_WHITE + ";" +
                        "-fx-prompt-text-fill: " + ParkingApp.TEXT_MUTED + ";" +
                        "-fx-background-radius: 8;" +
                        "-fx-padding: 10 14 10 14;" +
                        "-fx-font-size: 14px;"
        );
        tf.setPrefHeight(42);
    }
}