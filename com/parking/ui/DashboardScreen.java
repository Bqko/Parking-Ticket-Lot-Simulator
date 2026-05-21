package com.parking.ui;

import com.parking.model.ParkingLot;
import com.parking.model.Ticket;
import javafx.collections.*;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.stage.FileChooser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DashboardScreen {

    private final ParkingApp app;

    private Label totalVal, occupiedVal, availableVal, revenueVal, rateVal;
    private Label lastRefresh;
    private TableView<TicketRow>              table;
    private final ObservableList<TicketRow>   tableData = FXCollections.observableArrayList();

    public DashboardScreen(ParkingApp app) { this.app = app; }

    Node build() {
        ScrollPane sp = new ScrollPane();
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: " + ParkingApp.BG_BASE + "; -fx-background-color: " + ParkingApp.BG_BASE + "; -fx-border-color: transparent;");

        VBox page = new VBox(24);
        page.setPadding(new Insets(40, 48, 40, 48));
        page.setStyle("-fx-background-color: " + ParkingApp.BG_BASE + ";");

        // Page header
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label("📊");
        icon.setFont(Font.font("System", 28));
        VBox titles = new VBox(2);
        Label title = ParkingApp.pageTitle("Dashboard");
        Label sub = new Label("Live occupancy, active tickets, and session revenue.");
        sub.setFont(Font.font("System", 13));
        sub.setTextFill(Color.web(ParkingApp.TEXT_M));
        titles.getChildren().addAll(title, sub);
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        lastRefresh = new Label("");
        lastRefresh.setFont(Font.font("System", 11));
        lastRefresh.setTextFill(Color.web(ParkingApp.TEXT_M));

        Button refreshBtn = ParkingApp.primaryBtn("⟳  Refresh", ParkingApp.ACCENT);
        refreshBtn.setMaxWidth(Region.USE_PREF_SIZE);
        refreshBtn.setOnAction(e -> refresh());

        Button exportBtn = ParkingApp.ghostBtn("⬇  Export CSV");
        exportBtn.setMaxWidth(Region.USE_PREF_SIZE);
        exportBtn.setOnAction(e -> handleExportCSV());

        HBox.setMargin(icon, new Insets(0, 14, 0, 0));
        header.getChildren().addAll(icon, titles, spacer, lastRefresh, exportBtn, refreshBtn);
        HBox.setMargin(exportBtn,  new Insets(0, 0, 0, 14));
        HBox.setMargin(refreshBtn, new Insets(0, 0, 0, 8));

        // Stat cards
        Label statsHdr = ParkingApp.sectionTitle("LOT OVERVIEW");
        HBox statsRow = buildStatCards();

        // Occupancy bar section
        VBox occupancySection = buildOccupancyBar();

        // Table section
        Label tableHdr = ParkingApp.sectionTitle("CURRENTLY PARKED VEHICLES");
        Node tableNode = buildTable();

        page.getChildren().addAll(header, statsHdr, statsRow, occupancySection, tableHdr, tableNode);
        sp.setContent(page);

        javafx.application.Platform.runLater(() ->
                Animations.staggerCards(50, 80, statsRow, occupancySection, tableNode));

        refresh();
        return sp;
    }

    // ── Stat cards ────────────────────────────────────────────────────────

    private HBox buildStatCards() {
        totalVal    = bigStatLabel("—", ParkingApp.ACCENT);
        occupiedVal = bigStatLabel("—", ParkingApp.WARNING);
        availableVal= bigStatLabel("—", ParkingApp.SUCCESS);
        revenueVal  = bigStatLabel("—", ParkingApp.INFO);
        rateVal     = bigStatLabel("—", ParkingApp.DANGER);

        HBox row = new HBox(14);
        row.getChildren().addAll(
                statCard("🏢", "Total Spots",    totalVal,     ParkingApp.ACCENT),
                statCard("🚗", "Occupied",       occupiedVal,  ParkingApp.WARNING),
                statCard("✅", "Available",      availableVal, ParkingApp.SUCCESS),
                statCard("💰", "Revenue (USD)",  revenueVal,   ParkingApp.INFO),
                statCard("📈", "Occupancy %",    rateVal,      ParkingApp.DANGER)
        );
        return row;
    }

    private VBox statCard(String icon, String label, Label valLbl, String color) {
        VBox card = new VBox(8);
        card.setPrefWidth(170);
        card.setPadding(new Insets(18, 18, 18, 18));
        card.setStyle(
                "-fx-background-color: " + ParkingApp.BG_SURFACE + ";" +
                        "-fx-background-radius: 12;" +
                        "-fx-border-color: " + BORDER_LEFT_HACK + ";" +   // handled below
                        "-fx-border-radius: 12;" +
                        "-fx-border-width: 1;"
        );
        // left accent using a StackPane trick
        VBox inner = new VBox(6);
        inner.setStyle(
                "-fx-background-color: " + ParkingApp.BG_SURFACE + ";" +
                        "-fx-background-radius: 12;" +
                        "-fx-border-color: " + ParkingApp.BORDER + " " + ParkingApp.BORDER + " " + ParkingApp.BORDER + " " + color + ";" +
                        "-fx-border-radius: 0 0 0 0;" +
                        "-fx-border-width: 1 1 1 4;" +
                        "-fx-padding: 14 16 14 14;"
        );
        Label iconLbl = new Label(icon + "  " + label);
        iconLbl.setFont(Font.font("System", 11));
        iconLbl.setTextFill(Color.web(ParkingApp.TEXT_M));
        inner.getChildren().addAll(iconLbl, valLbl);
        return inner;
    }

    private Label bigStatLabel(String text, String color) {
        Label l = new Label(text);
        l.setFont(Font.font("Georgia", FontWeight.BOLD, 28));
        l.setTextFill(Color.web(color));
        return l;
    }

    // ── Occupancy bar ─────────────────────────────────────────────────────

    private VBox buildOccupancyBar() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(20, 24, 20, 24));
        section.setStyle(
                "-fx-background-color: " + ParkingApp.BG_SURFACE + ";" +
                        "-fx-background-radius: 12;" +
                        "-fx-border-color: " + ParkingApp.BORDER + ";" +
                        "-fx-border-radius: 12;" +
                        "-fx-border-width: 1;"
        );

        Label hdr = new Label("Occupancy Progress");
        hdr.setFont(Font.font("System", FontWeight.BOLD, 13));
        hdr.setTextFill(Color.web(ParkingApp.TEXT_H));

        var lot = ParkingLot.getInstance();
        double pct = lot.getOccupancyRate();
        String barColor = pct > 80 ? ParkingApp.DANGER : pct > 50 ? ParkingApp.WARNING : ParkingApp.SUCCESS;

        StackPane barTrack = new StackPane();
        barTrack.setAlignment(Pos.CENTER_LEFT);
        barTrack.setMaxHeight(12); barTrack.setMinHeight(12);

        Rectangle track = new Rectangle();
        track.setHeight(12);
        track.setArcWidth(8); track.setArcHeight(8);
        track.setFill(Color.web(ParkingApp.BORDER_LIT));
        track.widthProperty().bind(barTrack.widthProperty());

        Rectangle fill = new Rectangle(Math.max(8, barTrack.getPrefWidth() * pct / 100.0), 12);
        fill.setArcWidth(8); fill.setArcHeight(8);
        fill.setFill(Color.web(barColor));

        // animate fill width once layout is done
        barTrack.widthProperty().addListener((obs, o, w) -> {
            fill.setWidth(Math.max(8, w.doubleValue() * pct / 100.0));
        });

        barTrack.getChildren().addAll(track, fill);
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);

        HBox meta = new HBox();
        Label pctLbl = new Label(String.format("%.1f%% occupied", pct));
        pctLbl.setFont(Font.font("System", FontWeight.BOLD, 12));
        pctLbl.setTextFill(Color.web(barColor));
        Region sp2 = new Region(); HBox.setHgrow(sp2, Priority.ALWAYS);
        Label countLbl = new Label(lot.getOccupiedCount() + " of " + lot.getTotalSpots() + " spots used");
        countLbl.setFont(Font.font("System", 12));
        countLbl.setTextFill(Color.web(ParkingApp.TEXT_M));
        meta.getChildren().addAll(pctLbl, sp2, countLbl);

        section.getChildren().addAll(hdr, barTrack, meta);
        return section;
    }

    // ── Table ─────────────────────────────────────────────────────────────

    private Node buildTable() {
        table = new TableView<>(tableData);   // ← assigned FIRST
        table.setPrefHeight(300);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Inline CSS — no external file needed, no NullPointerException risk
        table.getStylesheets().add(
                "data:text/css," +
                        ".table-view{-fx-background-color:#111827;-fx-border-color:#1F2937;}" +
                        ".table-view .column-header-background{-fx-background-color:#1C2333;}" +
                        ".table-view .column-header .label{-fx-text-fill:#6B7280;-fx-font-weight:bold;-fx-font-size:12px;}" +
                        ".table-row-cell{-fx-background-color:#111827;-fx-border-color:#1F2937;-fx-border-width:0 0 1 0;}" +
                        ".table-row-cell:odd{-fx-background-color:#1C2333;}" +
                        ".table-row-cell:selected{-fx-background-color:#3B82F622;}" +
                        ".table-cell{-fx-text-fill:#F9FAFB;-fx-font-size:13px;-fx-padding:10 14 10 14;}" +
                        ".table-row-cell:selected .table-cell{-fx-text-fill:#F9FAFB;}" +
                        ".table-view .scroll-bar:vertical .thumb{-fx-background-color:#374151;-fx-background-radius:4;}"
        );

        // Enable cell selection and Ctrl+C copy
        table.getSelectionModel().setCellSelectionEnabled(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == javafx.scene.input.KeyCode.C) {
                StringBuilder sb = new StringBuilder();
                for (TablePosition<?, ?> pos : table.getSelectionModel().getSelectedCells()) {
                    Object cell = table.getColumns().get(pos.getColumn()).getCellData(pos.getRow());
                    if (cell != null) sb.append(cell).append("\t");
                }
                javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                content.putString(sb.toString().trim());
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
            }
        });

        table.setPlaceholder(buildPlaceholder());
        table.getColumns().addAll(
                col("Ticket ID",   "ticketId",  160),
                col("Plate",       "plate",     130),
                col("Type",        "type",      110),
                col("Spot",        "spot",       90),
                col("Entry Time",  "entryTime", 110),
                col("Est. Fee",    "estFee",    110)
        );
        return table;
    }

    @SuppressWarnings("unchecked")
    private <T> TableColumn<TicketRow,T> col(String hdr, String prop, double w) {
        TableColumn<TicketRow,T> c = new TableColumn<>(hdr);
        c.setCellValueFactory(new PropertyValueFactory<>(prop));
        c.setPrefWidth(w);
        c.setStyle("-fx-text-fill: " + ParkingApp.TEXT_H + "; -fx-font-size: 13px;");
        return c;
    }

    private Label buildPlaceholder() {
        Label l = new Label("No vehicles currently parked  🅿");
        l.setFont(Font.font("System", 14));
        l.setTextFill(Color.web(ParkingApp.TEXT_M));
        return l;
    }

    // ── Refresh ───────────────────────────────────────────────────────────

    void refresh() {
        var lot = ParkingLot.getInstance();

        // Count-up animations for stat values
        Animations.countUp(totalVal,    (int) lot.getTotalSpots(),      600);
        Animations.countUp(occupiedVal, (int) lot.getOccupiedCount(),   600);
        Animations.countUp(availableVal,(int) lot.getAvailableCount(),  600);
        Animations.countUpDecimal(revenueVal, ParkingApp.TICKET_MANAGER.getTotalRevenue(), 700, "");
        // occupancy rate as integer
        Animations.countUp(rateVal, (int) lot.getOccupancyRate(), 600);
        // fix suffix after count-up finishes
        javafx.animation.PauseTransition pt = new javafx.animation.PauseTransition(javafx.util.Duration.millis(720));
        pt.setOnFinished(e -> rateVal.setText(String.format("%.1f%%", lot.getOccupancyRate())));
        pt.play();

        tableData.clear();
        List<Ticket> active = ParkingApp.TICKET_MANAGER.getActiveTickets();
        for (Ticket t : active) {
            double fee = ParkingApp.TICKET_MANAGER.previewFee(t.getTicketId());
            tableData.add(new TicketRow(t, fee));
        }
        Animations.tableLoad(table);

        lastRefresh.setText("Updated " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "  ·  ");
    }

    // ── TicketRow bean ────────────────────────────────────────────────────

    public static class TicketRow {
        private final String ticketId, plate, type, spot, entryTime, estFee;
        public TicketRow(Ticket t, double fee) {
            ticketId  = t.getTicketId();
            plate     = t.getVehicle().getLicensePlate();
            type      = t.getVehicle().getType().getDisplayName();
            spot      = t.getSpot().getSpotId();
            entryTime = t.getIssuedAt().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            estFee    = String.format("%.2f USD", fee);
        }
        public String getTicketId()  { return ticketId; }
        public String getPlate()     { return plate; }
        public String getType()      { return type; }
        public String getSpot()      { return spot; }
        public String getEntryTime() { return entryTime; }
        public String getEstFee()    { return estFee; }
    }

    // ── CSV Export ────────────────────────────────────────────────────────

    private void handleExportCSV() {
        List<Ticket> history = ParkingApp.TICKET_MANAGER.getSessionHistory();

        if (history.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("No Data");
            alert.setHeaderText("Nothing to export yet.");
            alert.setContentText("Complete at least one parking session (entry → payment → exit) before exporting.");
            styleAlert(alert);
            alert.showAndWait();
            return;
        }

        // File chooser
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Session Report as CSV");
        chooser.setInitialFileName("parking_session_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".csv");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        File file = chooser.showSaveDialog(null);
        if (file == null) return; // user cancelled

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            // Header row
            writer.write("Ticket ID,License Plate,Vehicle Type,Spot,Floor," +
                    "Entry Time,Exit Time,Fee (USD),Amount Paid (USD),Change (USD),Status");
            writer.newLine();

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            for (Ticket t : history) {
                writer.write(String.join(",",
                        t.getTicketId(),
                        t.getVehicle().getLicensePlate(),
                        t.getVehicle().getType().getDisplayName(),
                        t.getSpot().getSpotId(),
                        String.valueOf(t.getSpot().getFloor()),
                        t.getIssuedAt().format(fmt),
                        t.getExitTime() != null ? t.getExitTime().format(fmt) : "—",
                        String.format("%.2f", t.getFeeCharged()),
                        String.format("%.2f", t.getAmountPaid()),
                        String.format("%.2f", t.getChange()),
                        t.getStatus().name()
                ));
                writer.newLine();
            }

            // Summary rows
            writer.newLine();
            writer.write("SUMMARY,,,,,,,,,,");
            writer.newLine();
            writer.write("Total Transactions," + history.size() + ",,,,,,,,,");
            writer.newLine();
            writer.write("Total Revenue (USD)," +
                    String.format("%.2f", ParkingApp.TICKET_MANAGER.getTotalRevenue()) + ",,,,,,,,,");
            writer.newLine();
            writer.write("Exported," +
                    LocalDateTime.now().format(fmt) + ",,,,,,,,,");
            writer.newLine();

            // Success alert
            Alert ok = new Alert(Alert.AlertType.INFORMATION);
            ok.setTitle("Export Successful");
            ok.setHeaderText("CSV saved successfully!");
            ok.setContentText("File: " + file.getAbsolutePath() +
                    "\n" + history.size() + " transactions exported.");
            styleAlert(ok);
            ok.showAndWait();

        } catch (IOException ex) {
            Alert err = new Alert(Alert.AlertType.ERROR);
            err.setTitle("Export Failed");
            err.setHeaderText("Could not write file.");
            err.setContentText(ex.getMessage());
            styleAlert(err);
            err.showAndWait();
        }
    }

    private void styleAlert(Alert alert) {
        alert.getDialogPane().setStyle(
                "-fx-background-color: " + ParkingApp.BG_SURFACE + ";" +
                        "-fx-border-color: " + ParkingApp.BORDER + ";"
        );
        alert.getDialogPane().lookup(".content.label").setStyle(
                "-fx-text-fill: " + ParkingApp.TEXT_B + "; -fx-font-size: 13px;"
        );
    }


    private static final String BORDER_LEFT_HACK = "transparent";
}