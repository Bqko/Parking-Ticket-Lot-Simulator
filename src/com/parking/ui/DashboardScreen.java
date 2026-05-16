package com.parking.ui;

import com.parking.model.ParkingLot;
import com.parking.model.Ticket;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Screen 3 — Dashboard.
 *
 * <p>Shows:</p>
 * <ul>
 *   <li>Live stat cards (total spots, occupied, available, revenue).</li>
 *   <li>A table of all currently active (parked) tickets.</li>
 *   <li>A refresh button to pull the latest data.</li>
 * </ul>
 */
public class DashboardScreen {

    private final ParkingApp app;

    // Stat labels — updated on refresh
    private Label totalSpotsVal;
    private Label occupiedVal;
    private Label availableVal;
    private Label revenueVal;
    private Label occupancyVal;
    private Label lastUpdated;

    // Active tickets table
    private TableView<TicketRow> table;
    private final ObservableList<TicketRow> tableData = FXCollections.observableArrayList();

    public DashboardScreen(ParkingApp app) {
        this.app = app;
    }

    public void show(Stage stage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + ParkingApp.BG_DARK + ";");
        root.setTop(buildHeader(stage));
        root.setCenter(buildBody());
        stage.setScene(new Scene(root, 960, 640));
        refresh(); // load data immediately
    }

    // ── Header ─────────────────────────────────────────────────────────────

    private HBox buildHeader(Stage stage) {
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20, 30, 20, 30));
        header.setStyle("-fx-background-color: " + ParkingApp.BG_NAVY + ";");

        Button back = ParkingApp.makeButton("← Back", ParkingApp.BG_CARD);
        back.setOnAction(e -> app.showHome());

        Label title = new Label("📊  Live Dashboard");
        title.setFont(Font.font("System", FontWeight.BOLD, 20));
        title.setTextFill(Color.web(ParkingApp.TEXT_WHITE));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        lastUpdated = new Label("");
        lastUpdated.setFont(Font.font("System", 11));
        lastUpdated.setTextFill(Color.web(ParkingApp.TEXT_MUTED));

        Button refreshBtn = ParkingApp.makeButton("⟳  Refresh", ParkingApp.TEAL);
        refreshBtn.setOnAction(e -> refresh());

        header.getChildren().addAll(back, title, spacer, lastUpdated, refreshBtn);
        return header;
    }

    // ── Body ───────────────────────────────────────────────────────────────

    private VBox buildBody() {
        VBox body = new VBox(24);
        body.setPadding(new Insets(30));
        body.getChildren().addAll(buildStatCards(), buildActiveTicketsSection());
        return body;
    }

    // ── Stat cards ─────────────────────────────────────────────────────────

    private HBox buildStatCards() {
        totalSpotsVal = statValue("—");
        occupiedVal   = statValue("—");
        availableVal  = statValue("—");
        revenueVal    = statValue("—");
        occupancyVal  = statValue("—");

        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().addAll(
                statCard("🏢", "Total Spots",  totalSpotsVal, ParkingApp.BLUE),
                statCard("🚗", "Occupied",     occupiedVal,   ParkingApp.RED),
                statCard("✅", "Available",    availableVal,  ParkingApp.GREEN),
                statCard("📈", "Occupancy %",  occupancyVal,  ParkingApp.YELLOW),
                statCard("💰", "Revenue (TRY)",revenueVal,    ParkingApp.TEAL)
        );
        return row;
    }

    private VBox statCard(String icon, String label, Label valueLabel, String accent) {
        VBox card = new VBox(6);
        card.setPrefSize(158, 110);
        card.setPadding(new Insets(18, 16, 18, 16));
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle(
                "-fx-background-color: " + ParkingApp.BG_CARD + ";" +
                        "-fx-background-radius: 10;" +
                        "-fx-border-color: " + accent + ";" +
                        "-fx-border-radius: 10;" +
                        "-fx-border-width: 0 0 0 4;"
        );

        Label iconLabel = new Label(icon + "  " + label);
        iconLabel.setFont(Font.font("System", 11));
        iconLabel.setTextFill(Color.web(ParkingApp.TEXT_MUTED));

        valueLabel.setTextFill(Color.web(accent));

        card.getChildren().addAll(iconLabel, valueLabel);
        return card;
    }

    private Label statValue(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("System", FontWeight.BOLD, 28));
        return l;
    }

    // ── Active tickets table ───────────────────────────────────────────────

    private VBox buildActiveTicketsSection() {
        Label heading = ParkingApp.sectionTitle("Currently Parked Vehicles");

        table = new TableView<>(tableData);
        table.setStyle(
                "-fx-background-color: " + ParkingApp.BG_CARD + ";" +
                        "-fx-table-cell-border-color: transparent;" +
                        "-fx-text-fill: " + ParkingApp.TEXT_WHITE + ";"
        );
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(280);
        table.setPlaceholder(buildEmptyPlaceholder());

        table.getColumns().addAll(
                col("Ticket ID",    "ticketId",   180),
                col("Plate",        "plate",      140),
                col("Type",         "type",       110),
                col("Spot",         "spot",       100),
                col("Entry Time",   "entryTime",  120),
                col("Est. Fee",     "estFee",     110)
        );

        VBox section = new VBox(12);
        section.getChildren().addAll(heading, table);
        return section;
    }

    @SuppressWarnings("unchecked")
    private <T> TableColumn<TicketRow, T> col(String header, String property, double width) {
        TableColumn<TicketRow, T> col = new TableColumn<>(header);
        col.setCellValueFactory(new PropertyValueFactory<>(property));
        col.setPrefWidth(width);
        col.setStyle("-fx-text-fill: " + ParkingApp.TEXT_WHITE + ";");
        return col;
    }

    private Label buildEmptyPlaceholder() {
        Label l = new Label("No vehicles currently parked  🅿");
        l.setFont(Font.font("System", 14));
        l.setTextFill(Color.web(ParkingApp.TEXT_MUTED));
        return l;
    }

    // ── Refresh ────────────────────────────────────────────────────────────

    private void refresh() {
        ParkingLot lot = ParkingLot.getInstance();

        totalSpotsVal.setText(String.valueOf(lot.getTotalSpots()));
        occupiedVal  .setText(String.valueOf(lot.getOccupiedCount()));
        availableVal .setText(String.valueOf(lot.getAvailableCount()));
        occupancyVal .setText(String.format("%.1f%%", lot.getOccupancyRate()));
        revenueVal   .setText(String.format("%.2f", ParkingApp.TICKET_MANAGER.getTotalRevenue()));

        // Rebuild table rows
        List<Ticket> active = ParkingApp.TICKET_MANAGER.getActiveTickets();
        tableData.clear();
        for (Ticket t : active) {
            double estFee = ParkingApp.TICKET_MANAGER.previewFee(t.getTicketId());
            tableData.add(new TicketRow(t, estFee));
        }

        lastUpdated.setText("Updated: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }

    // ── TableView row model ────────────────────────────────────────────────

    /**
     * Simple JavaBean wrapper so {@link PropertyValueFactory} can bind columns.
     */
    public static class TicketRow {
        private final String ticketId;
        private final String plate;
        private final String type;
        private final String spot;
        private final String entryTime;
        private final String estFee;

        public TicketRow(Ticket t, double fee) {
            this.ticketId  = t.getTicketId();
            this.plate     = t.getVehicle().getLicensePlate();
            this.type      = t.getVehicle().getType().getDisplayName();
            this.spot      = t.getSpot().getSpotId();
            this.entryTime = t.getIssuedAt().toLocalTime()
                    .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            this.estFee    = String.format("%.2f TRY", fee);
        }

        public String getTicketId()  { return ticketId; }
        public String getPlate()     { return plate; }
        public String getType()      { return type; }
        public String getSpot()      { return spot; }
        public String getEntryTime() { return entryTime; }
        public String getEstFee()    { return estFee; }
    }
}