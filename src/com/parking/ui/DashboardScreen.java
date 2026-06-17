package com.parking.ui;

import com.parking.db.TicketRepository;
import com.parking.model.ParkingLot;
import com.parking.model.Ticket;
import javafx.animation.*;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class DashboardScreen {

    private final ParkingApp app;
    private final TicketRepository ticketRepo = new TicketRepository();

    private Label totalVal, occupiedVal, availableVal, revenueVal, rateVal;
    private Label lastRefresh;
    private TableView<TicketRow>            table;
    private final ObservableList<TicketRow> tableData = FXCollections.observableArrayList();

    // Chart containers rebuilt on refresh
    private VBox barChartContainer;
    private VBox vehicleChartContainer;
    private VBox revenueStatsContainer;

    public DashboardScreen(ParkingApp app) { this.app = app; }

    Node build() {
        ScrollPane sp = new ScrollPane();
        sp.setFitToWidth(true);
        sp.setFitToHeight(false);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.setStyle("-fx-background: " + ParkingApp.BG_BASE
                + "; -fx-background-color: " + ParkingApp.BG_BASE
                + "; -fx-border-color: transparent;");
        // 2.5× faster vertical scroll — setOnScroll does NOT consume so layout is unaffected
        sp.setOnScroll(e -> {
            double contentH  = sp.getContent().getBoundsInLocal().getHeight();
            double viewportH = sp.getViewportBounds().getHeight();
            double scrollable = contentH - viewportH;
            if (scrollable <= 0) return;
            sp.setVvalue(Math.max(0, Math.min(1,
                    sp.getVvalue() - (e.getDeltaY() * 2.5) / scrollable)));
        });

        VBox page = new VBox(28);
        page.setPadding(new Insets(36, 40, 40, 40));
        page.setStyle("-fx-background-color: " + ParkingApp.BG_BASE + ";");
        page.setFillWidth(true);
        // Bind the page width to the viewport width so content never overflows.
        // This is the correct fix — setFitToWidth alone fails when children
        // have minWidth values that push the preferred width past the viewport.
        sp.viewportBoundsProperty().addListener((obs, oldB, newB) ->
                page.setPrefWidth(newB.getWidth()));

        // ── Page header ───────────────────────────────────────────────────
        HBox header = new HBox(14);
        header.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label("📊");
        icon.setFont(Font.font("System", 28));
        VBox titles = new VBox(2);
        Label title = ParkingApp.pageTitle("Dashboard");
        Label sub = new Label("Live occupancy, active tickets, and session analytics.");
        sub.setFont(Font.font("System", 13));
        sub.setTextFill(Color.web(ParkingApp.TEXT_M));
        titles.getChildren().addAll(title, sub);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        lastRefresh = new Label("");
        lastRefresh.setFont(Font.font("System", 11));
        lastRefresh.setTextFill(Color.web(ParkingApp.TEXT_M));

        Button refreshBtn = ParkingApp.primaryBtn("⟳  Refresh", ParkingApp.ACCENT);
        refreshBtn.setMaxWidth(Region.USE_PREF_SIZE);
        refreshBtn.setOnAction(e -> refresh());

        Button exportBtn = ParkingApp.ghostBtn("⬇  Export CSV");
        exportBtn.setMaxWidth(Region.USE_PREF_SIZE);
        exportBtn.setOnAction(e -> handleExportCSV());

        header.getChildren().addAll(icon, titles, spacer, lastRefresh, exportBtn, refreshBtn);
        HBox.setMargin(exportBtn,  new Insets(0, 0, 0, 8));
        HBox.setMargin(refreshBtn, new Insets(0, 0, 0, 8));

        // ── Stat cards ────────────────────────────────────────────────────
        Label statsHdr = ParkingApp.sectionTitle("LOT OVERVIEW");
        HBox statsRow = buildStatCards();
        statsRow.setMaxWidth(Double.MAX_VALUE);

        // ── Occupancy bar ─────────────────────────────────────────────────
        VBox occupancySection = buildOccupancyBar();

        // ── Charts row ────────────────────────────────────────────────────
        Label chartsHdr = ParkingApp.sectionTitle("ANALYTICS");
        HBox chartsRow  = buildChartsRow();
        chartsRow.setMaxWidth(Double.MAX_VALUE);

        // ── Revenue stats ─────────────────────────────────────────────────
        Label revenueHdr = ParkingApp.sectionTitle("REVENUE BREAKDOWN");
        revenueStatsContainer = new VBox();
        revenueStatsContainer.setMaxWidth(Double.MAX_VALUE);
        revenueStatsContainer.getChildren().add(buildRevenueStats());

        // ── Spot-type bar chart ───────────────────────────────────────────
        Label spotHdr = ParkingApp.sectionTitle("SPOT TYPE USAGE");
        barChartContainer = new VBox();
        barChartContainer.setMaxWidth(Double.MAX_VALUE);
        barChartContainer.getChildren().add(buildSpotTypeBarChart());

        // ── Active tickets table ──────────────────────────────────────────
        Label tableHdr  = ParkingApp.sectionTitle("CURRENTLY PARKED VEHICLES");
        Node  tableNode = buildTable();

        page.getChildren().addAll(
                header,
                statsHdr,   statsRow,
                occupancySection,
                chartsHdr,  chartsRow,
                revenueHdr, revenueStatsContainer,
                spotHdr,    barChartContainer,
                tableHdr,   tableNode
        );
        sp.setContent(page);

        javafx.application.Platform.runLater(() ->
                Animations.staggerCards(50, 60,
                        statsRow, occupancySection, chartsRow,
                        revenueStatsContainer, barChartContainer, tableNode));

        refresh();
        return sp;
    }

    // ══════════════════════════════════════════════════════════════════════
    // STAT CARDS
    // ══════════════════════════════════════════════════════════════════════

    private HBox buildStatCards() {
        totalVal     = bigStatLabel("—", ParkingApp.ACCENT);
        occupiedVal  = bigStatLabel("—", ParkingApp.WARNING);
        availableVal = bigStatLabel("—", ParkingApp.SUCCESS);
        revenueVal   = bigStatLabel("—", ParkingApp.INFO);
        rateVal      = bigStatLabel("—", ParkingApp.DANGER);

        HBox row = new HBox(14);
        row.setFillHeight(true);
        VBox c1 = statCard("🏢", "Total Spots",   totalVal,     ParkingApp.ACCENT);
        VBox c2 = statCard("🚗", "Occupied",      occupiedVal,  ParkingApp.WARNING);
        VBox c3 = statCard("✅", "Available",     availableVal, ParkingApp.SUCCESS);
        VBox c4 = statCard("💰", "Revenue (USD)", revenueVal,   ParkingApp.INFO);
        VBox c5 = statCard("📈", "Occupancy %",   rateVal,      ParkingApp.DANGER);
        for (VBox c : new VBox[]{c1, c2, c3, c4, c5}) {
            HBox.setHgrow(c, Priority.ALWAYS);
            c.setMaxWidth(Double.MAX_VALUE);
        }
        row.getChildren().addAll(c1, c2, c3, c4, c5);
        return row;
    }

    private VBox statCard(String icon, String label, Label valLbl, String color) {
        VBox inner = new VBox(6);
        inner.setMaxWidth(Double.MAX_VALUE);
        inner.setStyle(
                "-fx-background-color: " + ParkingApp.BG_SURFACE + ";" +
                        "-fx-background-radius: 12;" +
                        "-fx-border-color: " + ParkingApp.BORDER + " " + ParkingApp.BORDER
                        + " " + ParkingApp.BORDER + " " + color + ";" +
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

    // ══════════════════════════════════════════════════════════════════════
    // OCCUPANCY BAR
    // ══════════════════════════════════════════════════════════════════════

    private VBox buildOccupancyBar() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(20, 24, 20, 24));
        section.setMaxWidth(Double.MAX_VALUE);
        section.setStyle(cardStyle());

        Label hdr = new Label("Occupancy Progress");
        hdr.setFont(Font.font("System", FontWeight.BOLD, 13));
        hdr.setTextFill(Color.web(ParkingApp.TEXT_H));

        var lot = ParkingLot.getInstance();
        double pct = lot.getOccupancyRate();
        String barColor = pct > 80 ? ParkingApp.DANGER : pct > 50 ? ParkingApp.WARNING : ParkingApp.SUCCESS;

        StackPane barTrack = new StackPane();
        barTrack.setAlignment(Pos.CENTER_LEFT);
        barTrack.setMaxHeight(12); barTrack.setMinHeight(12);
        barTrack.setMaxWidth(Double.MAX_VALUE);

        Rectangle track = new Rectangle();
        track.setHeight(12);
        track.setArcWidth(8); track.setArcHeight(8);
        track.setFill(Color.web(ParkingApp.BORDER_LIT));
        track.widthProperty().bind(barTrack.widthProperty());

        Rectangle fill = new Rectangle(0, 12);
        fill.setArcWidth(8); fill.setArcHeight(8);
        fill.setFill(Color.web(barColor));
        // animate fill width once layout width is known
        barTrack.widthProperty().addListener((obs, o, w) -> {
            double target = Math.max(pct > 0 ? 8 : 0, w.doubleValue() * pct / 100.0);
            animateBarWidth(fill, target, 700);
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

    // ══════════════════════════════════════════════════════════════════════
    // CHARTS ROW
    // ══════════════════════════════════════════════════════════════════════

    private HBox buildChartsRow() {
        VBox pieCard = cardWrap("🥧  Occupancy by Spot Type", buildOccupancyPieChart());
        pieCard.setMaxWidth(Double.MAX_VALUE);

        vehicleChartContainer = new VBox();
        vehicleChartContainer.setMaxWidth(Double.MAX_VALUE);
        vehicleChartContainer.getChildren().add(buildVehicleTypeChart());
        VBox vehicleCard = cardWrap("🚘  Vehicle Type Distribution", vehicleChartContainer);
        vehicleCard.setMaxWidth(Double.MAX_VALUE);

        HBox row = new HBox(16);
        HBox.setHgrow(pieCard,     Priority.ALWAYS);
        HBox.setHgrow(vehicleCard, Priority.ALWAYS);
        row.getChildren().addAll(pieCard, vehicleCard);
        return row;
    }

    // ── Donut pie chart ───────────────────────────────────────────────────

    private Node buildOccupancyPieChart() {
        List<Ticket> active = ParkingApp.TICKET_MANAGER.getActiveTickets();

        Map<String, Long> occupiedByType = active.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getSpot().getSpotId().contains("-M") ? "MOTORCYCLE"
                                : t.getSpot().getSpotId().contains("-C") ? "COMPACT"
                                  : t.getSpot().getSpotId().contains("-L") ? "LARGE"
                                    : "STANDARD",
                        Collectors.counting()));

        Map<String, Integer> capacity = new LinkedHashMap<>();
        capacity.put("MOTORCYCLE", 15);
        capacity.put("COMPACT",    30);
        capacity.put("STANDARD",   60);
        capacity.put("LARGE",      15);

        String[] typeColors = {
                ParkingApp.ACCENT,
                ParkingApp.SUCCESS,
                ParkingApp.WARNING,
                ParkingApp.INFO
        };

        record Segment(String label, double value, String color) {}
        List<Segment> segments = new ArrayList<>();
        String[] types = {"MOTORCYCLE", "COMPACT", "STANDARD", "LARGE"};
        for (int i = 0; i < types.length; i++) {
            int  cap = capacity.get(types[i]);
            long occ = occupiedByType.getOrDefault(types[i], 0L);
            if (occ > 0)       segments.add(new Segment(types[i] + " occ",   occ,       typeColors[i]));
            if (cap - occ > 0) segments.add(new Segment(types[i] + " avail", cap - occ, typeColors[i] + "33"));
        }

        double total = segments.stream().mapToDouble(Segment::value).sum();
        double cx = 110, cy = 110, r = 90, innerR = 52;
        Pane canvas = new Pane();
        canvas.setPrefSize(220, 220);
        canvas.setMaxSize(220, 220);

        double startAngle = -90;
        for (Segment seg : segments) {
            double sweep = (seg.value() / total) * 360.0;
            if (sweep < 0.5) continue;

            double startRad = Math.toRadians(startAngle);
            double endRad   = Math.toRadians(startAngle + sweep);
            double x1 = cx + r * Math.cos(startRad), y1 = cy + r * Math.sin(startRad);
            double x2 = cx + r * Math.cos(endRad),   y2 = cy + r * Math.sin(endRad);
            double ix1 = cx + innerR * Math.cos(endRad),   iy1 = cy + innerR * Math.sin(endRad);
            double ix2 = cx + innerR * Math.cos(startRad), iy2 = cy + innerR * Math.sin(startRad);

            Path path = new Path();
            path.getElements().addAll(
                    new MoveTo(x1, y1),
                    new ArcTo(r, r, 0, x2, y2, sweep > 180, true),
                    new LineTo(ix1, iy1),
                    new ArcTo(innerR, innerR, 0, ix2, iy2, sweep > 180, false),
                    new ClosePath()
            );
            path.setFill(Color.web(seg.color()));
            path.setStroke(Color.web(ParkingApp.BG_SURFACE));
            path.setStrokeWidth(1.5);
            // pop-in animation per segment
            path.setOpacity(0);
            path.setScaleX(0.8); path.setScaleY(0.8);
            FadeTransition ft = new FadeTransition(Duration.millis(400), path);
            ft.setToValue(1); ft.setDelay(Duration.millis(200));
            ScaleTransition st = new ScaleTransition(Duration.millis(400), path);
            st.setToX(1); st.setToY(1);
            st.setInterpolator(Interpolator.EASE_OUT);
            st.setDelay(Duration.millis(200));
            new ParallelTransition(ft, st).play();

            canvas.getChildren().add(path);
            startAngle += sweep;
        }

        long totalOcc = active.size();
        Label centreTop = new Label(String.valueOf(totalOcc));
        centreTop.setFont(Font.font("Georgia", FontWeight.BOLD, 26));
        centreTop.setTextFill(Color.web(ParkingApp.TEXT_H));
        Label centreSub = new Label("occupied");
        centreSub.setFont(Font.font("System", 11));
        centreSub.setTextFill(Color.web(ParkingApp.TEXT_M));
        VBox centre = new VBox(2, centreTop, centreSub);
        centre.setAlignment(Pos.CENTER);
        centre.setMouseTransparent(true);

        StackPane pie = new StackPane(canvas, centre);
        pie.setAlignment(Pos.CENTER);
        pie.setMaxSize(220, 220);
        pie.setPrefSize(220, 220);

        String[] labels = {"Motorcycle", "Compact", "Standard", "Large"};
        VBox legend = new VBox(8);
        legend.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < types.length; i++) {
            int  cap = capacity.get(types[i]);
            long occ = occupiedByType.getOrDefault(types[i], 0L);
            Rectangle dot = new Rectangle(12, 12);
            dot.setArcWidth(3); dot.setArcHeight(3);
            dot.setFill(Color.web(typeColors[i]));
            Label lbl = new Label(labels[i] + "  " + occ + "/" + cap);
            lbl.setFont(Font.font("System", 12));
            lbl.setTextFill(Color.web(ParkingApp.TEXT_B));
            HBox row = new HBox(8, dot, lbl);
            row.setAlignment(Pos.CENTER_LEFT);
            legend.getChildren().add(row);
        }

        HBox content = new HBox(20, pie, legend);
        content.setAlignment(Pos.CENTER_LEFT);
        return content;
    }

    // ── Vehicle type horizontal bar chart ─────────────────────────────────

    private Node buildVehicleTypeChart() {
        List<Ticket> history = ParkingApp.TICKET_MANAGER.getSessionHistory();

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("Motorcycle", 0L);
        counts.put("Car",        0L);
        counts.put("Truck",      0L);
        for (Ticket t : history)
            counts.merge(t.getVehicle().getType().getDisplayName(), 1L, Long::sum);

        if (history.isEmpty()) {
            Label empty = new Label("No completed sessions yet.");
            empty.setFont(Font.font("System", 13));
            empty.setTextFill(Color.web(ParkingApp.TEXT_M));
            return empty;
        }

        long maxVal = Math.max(1, Collections.max(counts.values()));
        String[] colors = { ParkingApp.ACCENT, ParkingApp.SUCCESS, ParkingApp.WARNING };
        String[] keys   = { "Motorcycle", "Car", "Truck" };

        VBox bars = new VBox(14);
        bars.setFillWidth(true);
        bars.setMaxWidth(Double.MAX_VALUE);

        for (int i = 0; i < keys.length; i++) {
            long   val   = counts.get(keys[i]);
            String color = colors[i];
            double fillPct = val / (double) maxVal;

            Label nameLbl = new Label(keys[i]);
            nameLbl.setFont(Font.font("System", FontWeight.BOLD, 12));
            nameLbl.setTextFill(Color.web(ParkingApp.TEXT_B));
            nameLbl.setMinWidth(80);

            StackPane track = new StackPane();
            track.setAlignment(Pos.CENTER_LEFT);
            track.setMinHeight(10); track.setMaxHeight(10);
            track.setMaxWidth(Double.MAX_VALUE);

            Rectangle trackRect = new Rectangle(0, 10);
            trackRect.setArcWidth(6); trackRect.setArcHeight(6);
            trackRect.setFill(Color.web(ParkingApp.BORDER_LIT));
            trackRect.widthProperty().bind(track.widthProperty());

            Rectangle fillRect = new Rectangle(0, 10);
            fillRect.setArcWidth(6); fillRect.setArcHeight(6);
            fillRect.setFill(Color.web(color));
            // animate bar width when layout is ready
            final double fp = fillPct;
            final int barIdx = i;
            track.widthProperty().addListener((obs, o, w) -> {
                double target = Math.max(fp == 0 ? 0 : 6, w.doubleValue() * fp);
                animateBarWidth(fillRect, target, 600 + barIdx * 80);
            });
            track.getChildren().addAll(trackRect, fillRect);
            StackPane.setAlignment(fillRect, Pos.CENTER_LEFT);

            Label countLbl = new Label(String.valueOf(val));
            countLbl.setFont(Font.font("Georgia", FontWeight.BOLD, 14));
            countLbl.setTextFill(Color.web(color));
            countLbl.setMinWidth(36);
            countLbl.setAlignment(Pos.CENTER_RIGHT);

            HBox row = new HBox(10, nameLbl, track, countLbl);
            row.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(track, Priority.ALWAYS);
            bars.getChildren().add(row);
        }
        return bars;
    }

    // ══════════════════════════════════════════════════════════════════════
    // REVENUE STATS
    // ══════════════════════════════════════════════════════════════════════

    private Node buildRevenueStats() {
        List<Ticket> history = ParkingApp.TICKET_MANAGER.getSessionHistory();

        double totalRev = history.stream().mapToDouble(Ticket::getFeeCharged).sum();
        double avgFee   = history.isEmpty() ? 0 : totalRev / history.size();
        double maxFee   = history.stream().mapToDouble(Ticket::getFeeCharged).max().orElse(0);
        double minFee   = history.stream().filter(t -> t.getFeeCharged() > 0)
                .mapToDouble(Ticket::getFeeCharged).min().orElse(0);

        Map<String, Double> revByType = new LinkedHashMap<>();
        revByType.put("Motorcycle", 0.0);
        revByType.put("Car",        0.0);
        revByType.put("Truck",      0.0);
        for (Ticket t : history)
            revByType.merge(t.getVehicle().getType().getDisplayName(),
                    t.getFeeCharged(), Double::sum);

        // Summary card
        VBox summaryCard = new VBox(14);
        summaryCard.setPadding(new Insets(20));
        summaryCard.setStyle(cardStyle());

        Label summaryTitle = new Label("Session Summary");
        summaryTitle.setFont(Font.font("System", FontWeight.BOLD, 13));
        summaryTitle.setTextFill(Color.web(ParkingApp.TEXT_H));

        // Animated revenue labels
        Label totalRevLbl  = animatedMoneyLabel("0.00 USD", ParkingApp.INFO);
        Label avgFeeLbl    = animatedMoneyLabel("0.00 USD", ParkingApp.ACCENT);
        Label maxFeeLbl    = animatedMoneyLabel("0.00 USD", ParkingApp.WARNING);
        Label minFeeLbl    = animatedMoneyLabel("0.00 USD", ParkingApp.SUCCESS);
        Label sessionsLbl  = animatedMoneyLabel("0",        ParkingApp.TEXT_M);

        summaryCard.getChildren().addAll(
                summaryTitle,
                revenueStatRow("Total Revenue",     totalRevLbl),
                revenueStatRow("Avg Fee / Session",  avgFeeLbl),
                revenueStatRow("Highest Fee",        maxFeeLbl),
                revenueStatRow("Lowest Fee",         minFeeLbl),
                revenueStatRow("Total Sessions",     sessionsLbl)
        );

        // Trigger count-up after a short delay so the card is visible first
        javafx.application.Platform.runLater(() -> {
            Animations.countUpDecimal(totalRevLbl, totalRev,         800, " USD");
            Animations.countUpDecimal(avgFeeLbl,   avgFee,           800, " USD");
            Animations.countUpDecimal(maxFeeLbl,   maxFee,           800, " USD");
            Animations.countUpDecimal(minFeeLbl,   minFee,           800, " USD");
            Animations.countUp(sessionsLbl, history.size(),          600);
        });

        // Revenue by type card
        VBox typeCard = new VBox(16);
        typeCard.setPadding(new Insets(20));
        typeCard.setStyle(cardStyle());
        typeCard.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(typeCard, Priority.ALWAYS);

        Label typeTitle = new Label("Revenue by Vehicle Type");
        typeTitle.setFont(Font.font("System", FontWeight.BOLD, 13));
        typeTitle.setTextFill(Color.web(ParkingApp.TEXT_H));
        typeCard.getChildren().add(typeTitle);

        double maxTypeRev = Math.max(1, Collections.max(revByType.values()));
        String[] colors   = { ParkingApp.ACCENT, ParkingApp.SUCCESS, ParkingApp.WARNING };
        String[] typeKeys = { "Motorcycle", "Car", "Truck" };

        for (int i = 0; i < typeKeys.length; i++) {
            double rev   = revByType.get(typeKeys[i]);
            String color = colors[i];
            double pct   = rev / maxTypeRev;

            Label nameLbl = new Label(typeKeys[i]);
            nameLbl.setFont(Font.font("System", FontWeight.BOLD, 12));
            nameLbl.setTextFill(Color.web(ParkingApp.TEXT_B));
            nameLbl.setMinWidth(80);

            StackPane track = new StackPane();
            track.setAlignment(Pos.CENTER_LEFT);
            track.setMinHeight(10); track.setMaxHeight(10);
            track.setMaxWidth(Double.MAX_VALUE);

            Rectangle trackRect = new Rectangle(0, 10);
            trackRect.setArcWidth(6); trackRect.setArcHeight(6);
            trackRect.setFill(Color.web(ParkingApp.BORDER_LIT));
            trackRect.widthProperty().bind(track.widthProperty());

            Rectangle fillRect = new Rectangle(0, 10);
            fillRect.setArcWidth(6); fillRect.setArcHeight(6);
            fillRect.setFill(Color.web(color));
            final double fp = pct;
            final int idx = i;
            track.widthProperty().addListener((obs, o, w) -> {
                double target = Math.max(fp == 0 ? 0 : 6, w.doubleValue() * fp);
                animateBarWidth(fillRect, target, 700 + idx * 80);
            });
            track.getChildren().addAll(trackRect, fillRect);
            StackPane.setAlignment(fillRect, Pos.CENTER_LEFT);

            Label valLbl = new Label(String.format("%.0f", rev));
            valLbl.setFont(Font.font("Georgia", FontWeight.BOLD, 13));
            valLbl.setTextFill(Color.web(color));
            valLbl.setMinWidth(50);
            valLbl.setAlignment(Pos.CENTER_RIGHT);

            HBox barRow = new HBox(10, nameLbl, track, valLbl);
            barRow.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(track, Priority.ALWAYS);
            typeCard.getChildren().add(barRow);
        }

        HBox row = new HBox(16, summaryCard, typeCard);
        row.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(typeCard, Priority.ALWAYS);
        return row;
    }

    private HBox revenueStatRow(String label, Label valLbl) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(label);
        lbl.setFont(Font.font("System", 12));
        lbl.setTextFill(Color.web(ParkingApp.TEXT_M));
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        row.getChildren().addAll(lbl, sp, valLbl);
        return row;
    }

    private Label animatedMoneyLabel(String initial, String color) {
        Label l = new Label(initial);
        l.setFont(Font.font("Georgia", FontWeight.BOLD, 13));
        l.setTextFill(Color.web(color));
        return l;
    }

    // ══════════════════════════════════════════════════════════════════════
    // SPOT TYPE BAR CHART  (vertical stacked)
    // ══════════════════════════════════════════════════════════════════════

    private Node buildSpotTypeBarChart() {
        List<Ticket> active = ParkingApp.TICKET_MANAGER.getActiveTickets();

        Map<String, Long> occupiedByType = active.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getSpot().getSpotId().contains("-M") ? "MOTORCYCLE"
                                : t.getSpot().getSpotId().contains("-C") ? "COMPACT"
                                  : t.getSpot().getSpotId().contains("-L") ? "LARGE"
                                    : "STANDARD",
                        Collectors.counting()));

        String[] types    = {"MOTORCYCLE", "COMPACT", "STANDARD", "LARGE"};
        String[] labels   = {"Motorcycle", "Compact", "Standard", "Large"};
        int[]    capacity = {15, 30, 60, 15};
        String[] colors   = { ParkingApp.ACCENT, ParkingApp.SUCCESS, ParkingApp.WARNING, ParkingApp.INFO };

        double chartHeight = 140;

        HBox barsRow = new HBox(20);
        barsRow.setAlignment(Pos.BOTTOM_LEFT);
        barsRow.setPadding(new Insets(0, 0, 0, 10));

        for (int i = 0; i < types.length; i++) {
            int  cap   = capacity[i];
            long occ   = occupiedByType.getOrDefault(types[i], 0L);
            long avail = cap - occ;

            double occH   = occ   == 0 ? 0 : Math.max(4, chartHeight * occ   / cap);
            double availH = avail == 0 ? 0 : Math.max(4, chartHeight * avail / cap);

            VBox bar = new VBox(0);
            bar.setAlignment(Pos.BOTTOM_CENTER);
            bar.setPrefWidth(44);

            if (occ > 0) {
                Rectangle occRect = new Rectangle(44, 0); // start at 0, animate to occH
                occRect.setArcWidth(4); occRect.setArcHeight(4);
                occRect.setFill(Color.web(colors[i]));
                bar.getChildren().add(occRect);
                final double targetH = occH;
                final int delay = 300 + i * 80;
                javafx.application.Platform.runLater(() -> animateBarHeight(occRect, targetH, delay));
            }
            if (avail > 0) {
                Rectangle availRect = new Rectangle(44, 0);
                availRect.setFill(Color.web(colors[i] + "33"));
                if (occ == 0) { availRect.setArcWidth(4); availRect.setArcHeight(4); }
                bar.getChildren().add(availRect);
                final double targetH = availH;
                final int delay = 200 + i * 80;
                javafx.application.Platform.runLater(() -> animateBarHeight(availRect, targetH, delay));
            }

            Label countLbl = new Label(occ + "/" + cap);
            countLbl.setFont(Font.font("System", FontWeight.BOLD, 11));
            countLbl.setTextFill(Color.web(colors[i]));
            countLbl.setAlignment(Pos.CENTER);

            Label nameLbl = new Label(labels[i]);
            nameLbl.setFont(Font.font("System", 11));
            nameLbl.setTextFill(Color.web(ParkingApp.TEXT_M));
            nameLbl.setAlignment(Pos.CENTER);

            VBox col = new VBox(6, bar, countLbl, nameLbl);
            col.setAlignment(Pos.BOTTOM_CENTER);
            barsRow.getChildren().add(col);
        }

        // Y-axis labels
        VBox yAxis = new VBox();
        yAxis.setAlignment(Pos.TOP_RIGHT);
        yAxis.setPrefHeight(chartHeight);
        yAxis.setPrefWidth(34);
        for (int pct : new int[]{100, 75, 50, 25, 0}) {
            Label l = new Label(pct + "%");
            l.setFont(Font.font("System", 9));
            l.setTextFill(Color.web(ParkingApp.TEXT_M));
            VBox.setVgrow(l, Priority.ALWAYS);
            l.setAlignment(Pos.TOP_RIGHT);
            yAxis.getChildren().add(l);
        }

        // Grid lines
        Pane grid = new Pane();
        grid.setPrefHeight(chartHeight);
        grid.setMouseTransparent(true);
        grid.setMaxWidth(Double.MAX_VALUE);
        for (int p : new int[]{0, 25, 50, 75, 100}) {
            double y = chartHeight * (1 - p / 100.0);
            Line line = new Line(0, y, 2000, y); // wide enough for any screen
            line.setStroke(Color.web(ParkingApp.BORDER));
            line.setStrokeWidth(0.8);
            grid.getChildren().add(line);
        }

        StackPane chartArea = new StackPane(grid, barsRow);
        chartArea.setAlignment(Pos.BOTTOM_LEFT);
        chartArea.setMaxWidth(Double.MAX_VALUE);

        HBox legend = new HBox(20);
        legend.setAlignment(Pos.CENTER_LEFT);
        legend.setPadding(new Insets(10, 0, 0, 0));
        for (String[] item : new String[][]{{"Occupied", ParkingApp.ACCENT}, {"Available", ParkingApp.ACCENT + "33"}}) {
            Rectangle dot = new Rectangle(10, 10);
            dot.setArcWidth(2); dot.setArcHeight(2);
            dot.setFill(Color.web(item[1]));
            Label lbl = new Label(item[0]);
            lbl.setFont(Font.font("System", 11));
            lbl.setTextFill(Color.web(ParkingApp.TEXT_M));
            HBox it = new HBox(6, dot, lbl);
            it.setAlignment(Pos.CENTER_LEFT);
            legend.getChildren().add(it);
        }

        HBox layout = new HBox(12, yAxis, chartArea);
        layout.setAlignment(Pos.BOTTOM_LEFT);
        HBox.setHgrow(chartArea, Priority.ALWAYS);

        VBox full = new VBox(4, layout, legend);
        full.setPadding(new Insets(20, 24, 20, 24));
        full.setMaxWidth(Double.MAX_VALUE);
        full.setStyle(cardStyle());
        return full;
    }

    // ══════════════════════════════════════════════════════════════════════
    // TABLE  — styled via TableFactory
    // ══════════════════════════════════════════════════════════════════════

    private Node buildTable() {
        TableFactory.Result<TicketRow> result = TableFactory.build(
                tableData,
                "Search by plate, ticket ID, spot, or type…",
                (row, q) -> row.getTicketId().toLowerCase().contains(q)
                        || row.getPlate().toLowerCase().contains(q)
                        || row.getSpot().toLowerCase().contains(q)
                        || row.getType().toLowerCase().contains(q)
        );

        table = result.table();
        TableFactory.setVisibleRows(table, 8);

        // Vehicle type badge — color by type
        TableColumn<TicketRow, String> typeCol = TableFactory.badgeCol(
                "Vehicle Type",
                TicketRow::getType,
                row -> switch (row.getType().toUpperCase()) {
                    case "MOTORCYCLE" -> ParkingApp.ACCENT;
                    case "TRUCK"      -> ParkingApp.WARNING;
                    default           -> ParkingApp.INFO;
                },
                130
        );

        // Spot — dot colored by floor
        TableColumn<TicketRow, String> spotCol = TableFactory.dotCol(
                "Spot",
                TicketRow::getSpot,
                row -> {
                    String s = row.getSpot();
                    if (s.startsWith("F0")) return ParkingApp.SUCCESS;
                    if (s.startsWith("F1")) return ParkingApp.ACCENT;
                    return ParkingApp.INFO;
                },
                100
        );

        // Fee badge
        TableColumn<TicketRow, String> feeCol = TableFactory.badgeCol(
                "Est. Fee",
                TicketRow::getEstFee,
                row -> ParkingApp.SUCCESS,
                120
        );

        table.getColumns().addAll(
                TableFactory.copyableCol("Ticket ID", TicketRow::getTicketId, 180),
                TableFactory.textCol("Plate",       TicketRow::getPlate,     130),
                typeCol,
                spotCol,
                TableFactory.textCol("Entry Time",  TicketRow::getEntryTime, 120),
                feeCol
        );

        return result.container();
    }

    // ══════════════════════════════════════════════════════════════════════
    // REFRESH
    // ══════════════════════════════════════════════════════════════════════

    void refresh() {
        var lot = ParkingLot.getInstance();

        Animations.countUp(totalVal,     (int) lot.getTotalSpots(),     600);
        Animations.countUp(occupiedVal,  (int) lot.getOccupiedCount(),  600);
        Animations.countUp(availableVal, (int) lot.getAvailableCount(), 600);
        Animations.countUpDecimal(revenueVal, ParkingApp.TICKET_MANAGER.getTotalRevenue(), 700, "");
        Animations.countUp(rateVal, (int) lot.getOccupancyRate(), 600);
        javafx.animation.PauseTransition pt =
                new javafx.animation.PauseTransition(javafx.util.Duration.millis(720));
        pt.setOnFinished(e -> rateVal.setText(String.format("%.1f%%", lot.getOccupancyRate())));
        pt.play();

        if (barChartContainer     != null) barChartContainer.getChildren().setAll(buildSpotTypeBarChart());
        if (vehicleChartContainer != null) vehicleChartContainer.getChildren().setAll(buildVehicleTypeChart());
        if (revenueStatsContainer != null) revenueStatsContainer.getChildren().setAll(buildRevenueStats());

        tableData.clear();
        for (Ticket t : ParkingApp.TICKET_MANAGER.getActiveTickets()) {
            double fee = ParkingApp.TICKET_MANAGER.previewFee(t.getTicketId());
            tableData.add(new TicketRow(t, fee));
        }
        Animations.tableLoad(table);

        lastRefresh.setText("Updated "
                + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "  ·  ");
    }

    // ══════════════════════════════════════════════════════════════════════
    // ANIMATION HELPERS
    // ══════════════════════════════════════════════════════════════════════

    /** Smoothly animates a Rectangle's width from its current value to target. */
    private static void animateBarWidth(Rectangle rect, double target, int durationMs) {
        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO,            new KeyValue(rect.widthProperty(), rect.getWidth())),
                new KeyFrame(Duration.millis(durationMs),
                        new KeyValue(rect.widthProperty(), target, Interpolator.EASE_OUT))
        );
        tl.play();
    }

    /** Smoothly animates a Rectangle's height from 0 to target. */
    private static void animateBarHeight(Rectangle rect, double target, int delayMs) {
        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO,             new KeyValue(rect.heightProperty(), 0.0)),
                new KeyFrame(Duration.millis(500),
                        new KeyValue(rect.heightProperty(), target, Interpolator.EASE_OUT))
        );
        tl.setDelay(Duration.millis(delayMs));
        tl.play();
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private VBox cardWrap(String title, Node content) {
        VBox card = new VBox(16);
        card.setPadding(new Insets(20, 24, 20, 24));
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle(cardStyle());
        Label hdr = new Label(title);
        hdr.setFont(Font.font("System", FontWeight.BOLD, 13));
        hdr.setTextFill(Color.web(ParkingApp.TEXT_H));
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: " + ParkingApp.BORDER + ";");
        card.getChildren().addAll(hdr, sep, content);
        return card;
    }

    private String cardStyle() {
        return "-fx-background-color: " + ParkingApp.BG_SURFACE + ";" +
                "-fx-background-radius: 12;" +
                "-fx-border-color: "     + ParkingApp.BORDER     + ";" +
                "-fx-border-radius: 12;" +
                "-fx-border-width: 1;";
    }

    // ══════════════════════════════════════════════════════════════════════
    // TICKET ROW BEAN
    // ══════════════════════════════════════════════════════════════════════

    public static class TicketRow {
        private final String ticketId, plate, type, spot, entryTime, estFee;
        public TicketRow(Ticket t, double fee) {
            ticketId  = t.getTicketId();
            plate     = t.getVehicle().getLicensePlate();
            type      = t.getVehicle().getType().getDisplayName();
            spot      = t.getSpot().getSpotId();
            entryTime = t.getIssuedAt().toLocalTime()
                    .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            estFee    = String.format("%.2f USD", fee);
        }
        public String getTicketId()  { return ticketId; }
        public String getPlate()     { return plate; }
        public String getType()      { return type; }
        public String getSpot()      { return spot; }
        public String getEntryTime() { return entryTime; }
        public String getEstFee()    { return estFee; }
    }

    // ══════════════════════════════════════════════════════════════════════
    // CSV EXPORT
    // ══════════════════════════════════════════════════════════════════════

    private void handleExportCSV() {
        List<Ticket> history = ParkingApp.TICKET_MANAGER.getSessionHistory();
        if (history.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("No Data");
            alert.setHeaderText("Nothing to export yet.");
            alert.setContentText("Complete at least one session before exporting.");
            styleAlert(alert); alert.showAndWait(); return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Session Report as CSV");
        chooser.setInitialFileName("parking_session_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = chooser.showSaveDialog(null);
        if (file == null) return;

        try (BufferedWriter w = new BufferedWriter(new FileWriter(file))) {
            w.write("Ticket ID,License Plate,Vehicle Type,Spot,Floor," +
                    "Entry Time,Exit Time,Fee (USD),Amount Paid (USD),Change (USD),Status");
            w.newLine();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            for (Ticket t : history) {
                w.write(String.join(",",
                        t.getTicketId(), t.getVehicle().getLicensePlate(),
                        t.getVehicle().getType().getDisplayName(),
                        t.getSpot().getSpotId(), String.valueOf(t.getSpot().getFloor()),
                        t.getIssuedAt().format(fmt),
                        t.getExitTime() != null ? t.getExitTime().format(fmt) : "—",
                        String.format("%.2f", t.getFeeCharged()),
                        String.format("%.2f", t.getAmountPaid()),
                        String.format("%.2f", t.getChange()),
                        t.getStatus().name()));
                w.newLine();
            }
            w.newLine();
            w.write("SUMMARY,,,,,,,,,,"); w.newLine();
            w.write("Total Transactions," + history.size() + ",,,,,,,,,"); w.newLine();
            w.write("Total Revenue (USD)," +
                    String.format("%.2f", ParkingApp.TICKET_MANAGER.getTotalRevenue()) + ",,,,,,,,,");
            w.newLine();
            w.write("Exported," + LocalDateTime.now().format(fmt) + ",,,,,,,,,"); w.newLine();

            Alert ok = new Alert(Alert.AlertType.INFORMATION);
            ok.setTitle("Export Successful"); ok.setHeaderText("CSV saved successfully!");
            ok.setContentText("File: " + file.getAbsolutePath() +
                    "\n" + history.size() + " transactions exported.");
            styleAlert(ok); ok.showAndWait();
        } catch (IOException ex) {
            Alert err = new Alert(Alert.AlertType.ERROR);
            err.setTitle("Export Failed"); err.setHeaderText("Could not write file.");
            err.setContentText(ex.getMessage()); styleAlert(err); err.showAndWait();
        }
    }

    private void styleAlert(Alert alert) {
        alert.getDialogPane().setStyle(
                "-fx-background-color: " + ParkingApp.BG_SURFACE + ";" +
                        "-fx-border-color: "     + ParkingApp.BORDER     + ";");
        alert.getDialogPane().lookup(".content.label").setStyle(
                "-fx-text-fill: " + ParkingApp.TEXT_B + "; -fx-font-size: 13px;");
    }
}