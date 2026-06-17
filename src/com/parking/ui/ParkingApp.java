package com.parking.ui;

import com.parking.db.SpotRepository;
import com.parking.db.PreferencesRepository;
import com.parking.model.ParkingLot;
import com.parking.model.ParkingSpot;
import com.parking.service.TicketManager;
import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.stage.Stage;
import javafx.util.Duration;

public class ParkingApp extends Application {

    // ── Shared state ──────────────────────────────────────────────────────
    static final TicketManager TICKET_MANAGER = new TicketManager();

    // ── Design tokens — live fields, refreshed on theme change ────────────
    static String BG_BASE    = Theme.BG_BASE();
    static String BG_SURFACE = Theme.BG_SURFACE();
    static String BG_RAISED  = Theme.BG_RAISED();
    static String BG_SIDEBAR = Theme.BG_SIDEBAR();
    static String ACCENT     = Theme.ACCENT();
    static String SUCCESS    = Theme.SUCCESS();
    static String WARNING    = Theme.WARNING();
    static String DANGER     = Theme.DANGER();
    static String INFO       = Theme.INFO();
    static String TEXT_H     = Theme.TEXT_H();
    static String TEXT_B     = Theme.TEXT_B();
    static String TEXT_M     = Theme.TEXT_M();
    static String BORDER     = Theme.BORDER();
    static String BORDER_LIT = Theme.BORDER_LIT();

    // back-compat aliases
    static String BG_DARK    = BG_BASE;
    static String BG_CARD    = BG_SURFACE;
    static String BG_NAVY    = BG_RAISED;
    static String BLUE       = ACCENT;
    static String TEAL       = INFO;
    static String GREEN      = SUCCESS;
    static String YELLOW     = WARNING;
    static String RED        = DANGER;
    static String TEXT_WHITE = TEXT_H;
    static String TEXT_MUTED = TEXT_M;

    /** Call this after every Theme.toggle() to sync all fields. */
    static void refreshTokens() {
        BG_BASE    = Theme.BG_BASE();
        BG_SURFACE = Theme.BG_SURFACE();
        BG_RAISED  = Theme.BG_RAISED();
        BG_SIDEBAR = Theme.BG_SIDEBAR();
        ACCENT     = Theme.ACCENT();
        SUCCESS    = Theme.SUCCESS();
        WARNING    = Theme.WARNING();
        DANGER     = Theme.DANGER();
        INFO       = Theme.INFO();
        TEXT_H     = Theme.TEXT_H();
        TEXT_B     = Theme.TEXT_B();
        TEXT_M     = Theme.TEXT_M();
        BORDER     = Theme.BORDER();
        BORDER_LIT = Theme.BORDER_LIT();
        // sync aliases
        BG_DARK    = BG_BASE;
        BG_CARD    = BG_SURFACE;
        BG_NAVY    = BG_RAISED;
        BLUE       = ACCENT;
        TEAL       = INFO;
        GREEN      = SUCCESS;
        YELLOW     = WARNING;
        RED        = DANGER;
        TEXT_WHITE = TEXT_H;
        TEXT_MUTED = TEXT_M;
    }

    String      activeNav   = "home";
    VBox        sidebar;
    StackPane   contentArea;
    Stage       primaryStage;
    Scene       mainScene;                          // kept for theme refresh

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle("ParkSpace — Parking Management System");
        stage.setMinWidth(720);
        stage.setMinHeight(520);

        com.parking.service.PricingTier.installDefaults();
        loadSpotsFromDb();
        loadSavedTheme();                 // ← restore last theme before first paint

        com.parking.service.OccupancyObserver.getInstance().addListener(event ->
                Platform.runLater(() -> {
                    if (sidebar != null) {
                        var children = sidebar.getChildren();
                        if (!children.isEmpty())
                            children.set(children.size() - 1, buildSidebarStatus());
                    }
                })
        );

        stage.setOnCloseRequest(e -> {
            TICKET_MANAGER.saveSession();
            saveTheme();                    // ← persist active theme
        });

        BorderPane root = buildShell();
        mainScene = new Scene(root, 1280, 800);
        mainScene.setFill(Color.web(Theme.BG_BASE()));
        stage.setScene(mainScene);
        stage.setMaximized(true);
        stage.show();
        showPage("home");

        // ── Rebuild entire UI whenever theme changes ──────────────────────
        // Listen on Theme.current (the full ThemeId) so switching between
        // any two themes triggers a rebuild. The old isDark listener only
        // fired when crossing the dark/light boundary, so Dark -> Midnight
        // -> Forest were silently ignored.
        Theme.current.addListener((obs, oldVal, newVal) -> Platform.runLater(() -> {
            refreshTokens();
            mainScene.setFill(Color.web(Theme.BG_BASE()));
            BorderPane newRoot = buildShell();
            mainScene.setRoot(newRoot);
            showPage(activeNav);
        }));
    }

    // ── DB startup sync ───────────────────────────────────────────────────

    /**
     * Replaces ParkingLot's hardcoded in-memory spots with the persisted
     * state from the database. Called once at startup.
     *
     * Without this, ParkingLot.initializeSpots() always rebuilds the same
     * 120-spot default layout on every restart, ignoring any spots that were
     * added or removed via the Admin Panel.
     */
    // ── Theme persistence ─────────────────────────────────────────────────

    private void loadSavedTheme() {
        String saved = new PreferencesRepository()
                .get(PreferencesRepository.KEY_THEME, Theme.ThemeId.DARK.name());
        try {
            Theme.ThemeId id = Theme.ThemeId.valueOf(saved);
            Theme.setTheme(id);
            refreshTokens();
        } catch (IllegalArgumentException ignored) {
            // Unknown value in DB — fall back to DARK silently
        }
    }

    private void saveTheme() {
        new PreferencesRepository()
                .set(PreferencesRepository.KEY_THEME, Theme.current.get().name());
    }

    private void loadSpotsFromDb() {
        SpotRepository spotRepo = new SpotRepository();
        java.util.List<ParkingSpot> dbSpots = spotRepo.loadAll();
        if (dbSpots.isEmpty()) return;  // first run — seed hasn't populated yet

        ParkingLot lot = ParkingLot.getInstance();

        // Remove the default spots initializeSpots() just created
        lot.getAllSpots().stream()
                .map(ParkingSpot::getSpotId)
                .toList()   // snapshot — avoid ConcurrentModificationException
                .forEach(id -> {
                    try { lot.removeSpot(id); }
                    catch (Exception ignored) {}
                });

        // Re-add from DB
        for (ParkingSpot spot : dbSpots) {
            try { lot.addSpot(spot); }
            catch (IllegalArgumentException ignored) {}
        }

        System.out.println("✅ Loaded " + dbSpots.size() + " spots from database.");
    }

    // ── App shell ─────────────────────────────────────────────────────────

    private BorderPane buildShell() {
        BorderPane shell = new BorderPane();
        shell.setStyle("-fx-background-color: " + Theme.BG_BASE() + ";");
        sidebar     = buildSidebar();
        contentArea = new StackPane();
        contentArea.setStyle("-fx-background-color: " + Theme.BG_BASE() + ";");
        shell.setLeft(sidebar);
        shell.setCenter(contentArea);
        return shell;
    }

    // ── Sidebar ───────────────────────────────────────────────────────────

    private VBox buildSidebar() {
        VBox sb = new VBox(0);
        sb.setPrefWidth(220);
        sb.setStyle(
                "-fx-background-color: " + Theme.BG_SIDEBAR() + ";" +
                        "-fx-border-color: "     + Theme.BORDER()     + ";" +
                        "-fx-border-width: 0 1 0 0;"
        );

        // Logo
        VBox logo = new VBox(4);
        logo.setPadding(new Insets(26, 20, 22, 20));
        logo.setStyle("-fx-border-color: " + Theme.BORDER() + "; -fx-border-width: 0 0 1 0;");

        HBox logoRow = new HBox(10);
        logoRow.setAlignment(Pos.CENTER_LEFT);

        StackPane badge = new StackPane();
        Rectangle badgeBg = new Rectangle(36, 36);
        badgeBg.setArcWidth(10); badgeBg.setArcHeight(10);
        badgeBg.setFill(Color.web(Theme.ACCENT()));
        Label badgeLetter = new Label("P");
        badgeLetter.setFont(Font.font("Georgia", FontWeight.BOLD, 20));
        badgeLetter.setTextFill(Color.WHITE);
        badge.getChildren().addAll(badgeBg, badgeLetter);

        VBox logoText = new VBox(1);
        Label appName = new Label("ParkSpace");
        appName.setFont(Font.font("Georgia", FontWeight.BOLD, 17));
        appName.setTextFill(Color.web(Theme.TEXT_H()));
        Label appSub = new Label("Management System");
        appSub.setFont(Font.font("System", 10));
        appSub.setTextFill(Color.web(Theme.TEXT_M()));
        logoText.getChildren().addAll(appName, appSub);
        logoRow.getChildren().addAll(badge, logoText);
        logo.getChildren().add(logoRow);

        Label navLabel = new Label("NAVIGATION");
        navLabel.setFont(Font.font("System", FontWeight.BOLD, 10));
        navLabel.setTextFill(Color.web(Theme.TEXT_M()));
        navLabel.setPadding(new Insets(18, 20, 8, 20));

        VBox navItems = new VBox(3);
        navItems.setPadding(new Insets(0, 10, 0, 10));
        navItems.getChildren().addAll(
                navItem("home",      "🏠", "Home"),
                navItem("entry",     "🚗", "Vehicle Entry"),
                navItem("payment",   "💳", "Payment & Exit"),
                navItem("dashboard", "📊", "Dashboard"),
                navItem("admin",     "⚙️",  "Admin Panel")
        );

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        sb.getChildren().addAll(logo, navLabel, navItems, spacer,
                buildThemeToggle(), buildSidebarStatus());
        return sb;
    }

    // ── Theme picker (all 5 themes) ───────────────────────────────────────

    private VBox buildThemeToggle() {
        VBox wrapper = new VBox(6);
        wrapper.setPadding(new Insets(10, 14, 6, 14));

        Label lbl = new Label("THEME");
        lbl.setFont(Font.font("System", FontWeight.BOLD, 10));
        lbl.setTextFill(Color.web(Theme.TEXT_M()));

        ComboBox<Theme.ThemeId> combo = new ComboBox<>();
        combo.getItems().addAll(Theme.ThemeId.values());
        combo.setValue(Theme.current.get());
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setCursor(javafx.scene.Cursor.HAND);

        // Cell factory — show emoji + display name
        javafx.util.Callback<javafx.scene.control.ListView<Theme.ThemeId>,
                javafx.scene.control.ListCell<Theme.ThemeId>> cellFactory = lv -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(Theme.ThemeId item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                String emoji = switch (item) {
                    case DARK     -> "🌑";
                    case LIGHT    -> "☀️";
                    case MIDNIGHT -> "🌌";
                    case FOREST   -> "🌲";
                    case ROSE     -> "🌸";
                };
                setText(emoji + "  " + item.displayName);
            }
        };
        combo.setCellFactory(cellFactory);
        combo.setButtonCell(cellFactory.call(null));

        combo.setStyle(
                "-fx-background-color: " + Theme.BG_RAISED() + ";" +
                        "-fx-border-color: "     + Theme.BORDER_LIT() + ";" +
                        "-fx-border-radius: 8;" +
                        "-fx-background-radius: 8;" +
                        "-fx-text-fill: "        + Theme.TEXT_H() + ";" +
                        "-fx-font-size: 12px;" +
                        "-fx-cursor: hand;"
        );

        combo.setOnAction(e -> {
            Theme.ThemeId selected = combo.getValue();
            if (selected != null && selected != Theme.current.get()) {
                Theme.setTheme(selected);
                // Theme.current listener in start() handles the rebuild
            }
        });

        wrapper.getChildren().addAll(lbl, combo);
        return wrapper;
    }

    HBox navItem(String id, String icon, String label) {
        HBox item = new HBox(10);
        item.setAlignment(Pos.CENTER_LEFT);
        item.setPadding(new Insets(10, 14, 10, 14));
        item.setCursor(javafx.scene.Cursor.HAND);
        item.setMaxWidth(Double.MAX_VALUE);

        Rectangle accentBar = new Rectangle(3, 20);
        accentBar.setArcWidth(3); accentBar.setArcHeight(3);
        accentBar.setFill(Color.web(Theme.ACCENT()));
        accentBar.setVisible(false);

        Label iconLbl = new Label(icon);
        iconLbl.setFont(Font.font("System", 15));
        Label textLbl = new Label(label);
        textLbl.setFont(Font.font("System", 13));
        item.getChildren().addAll(accentBar, iconLbl, textLbl);

        Runnable applyActive = () -> {
            item.setStyle("-fx-background-color: " + Theme.ACCENT() + "20; -fx-background-radius: 8;");
            accentBar.setVisible(true);
            textLbl.setTextFill(Color.web(Theme.TEXT_H()));
            textLbl.setFont(Font.font("System", FontWeight.BOLD, 13));
        };
        Runnable applyInactive = () -> {
            item.setStyle("-fx-background-color: transparent; -fx-background-radius: 8;");
            accentBar.setVisible(false);
            textLbl.setTextFill(Color.web(Theme.TEXT_M()));
            textLbl.setFont(Font.font("System", 13));
        };
        applyInactive.run();

        item.setUserData(new Object[]{ id, applyActive, applyInactive });
        item.setOnMouseEntered(e -> { if (!activeNav.equals(id)) { item.setStyle("-fx-background-color: " + Theme.BORDER() + "; -fx-background-radius: 8;"); textLbl.setTextFill(Color.web(Theme.TEXT_B())); } });
        item.setOnMouseExited(e  -> { if (!activeNav.equals(id)) applyInactive.run(); });
        item.setOnMouseClicked(e -> { Animations.navClick(item); showPage(id); });
        return item;
    }

    private VBox buildSidebarStatus() {
        VBox strip = new VBox(6);
        strip.setPadding(new Insets(14, 16, 20, 16));
        strip.setStyle("-fx-border-color: " + Theme.BORDER() + "; -fx-border-width: 1 0 0 0;");

        Label lbl = new Label("LOT STATUS");
        lbl.setFont(Font.font("System", FontWeight.BOLD, 9));
        lbl.setTextFill(Color.web(Theme.TEXT_M()));

        var lot = com.parking.model.ParkingLot.getInstance();
        long avail = lot.getAvailableCount();
        long total = lot.getTotalSpots();
        double pct = lot.getOccupancyRate();

        Label spotsLbl = new Label(avail + " / " + total + " available");
        spotsLbl.setFont(Font.font("System", FontWeight.BOLD, 13));
        spotsLbl.setTextFill(Color.web(avail > 10 ? Theme.SUCCESS() : Theme.WARNING()));

        StackPane bar = new StackPane();
        bar.setAlignment(Pos.CENTER_LEFT);
        Rectangle track = new Rectangle(184, 5);
        track.setArcWidth(4); track.setArcHeight(4);
        track.setFill(Color.web(Theme.BORDER_LIT()));
        Rectangle fill = new Rectangle(Math.max(5, 184 * pct / 100.0), 5);
        fill.setArcWidth(4); fill.setArcHeight(4);
        fill.setFill(Color.web(pct > 80 ? Theme.DANGER() : pct > 50 ? Theme.WARNING() : Theme.SUCCESS()));
        bar.getChildren().addAll(track, fill);
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);

        strip.getChildren().addAll(lbl, spotsLbl, bar);
        return strip;
    }

    // ── Page routing ──────────────────────────────────────────────────────

    void showPage(String id) {
        activeNav = id;
        sidebar.lookupAll("*").forEach(n -> {
            if (n instanceof HBox item && item.getUserData() instanceof Object[] data) {
                String itemId = (String) data[0];
                ((Runnable)(itemId.equals(id) ? data[1] : data[2])).run();
            }
        });

        javafx.scene.Node page = switch (id) {
            case "entry"     -> new EntryScreen(this).build();
            case "payment"   -> new PaymentScreen(this).build();
            case "dashboard" -> new DashboardScreen(this).build();
            case "admin"     -> new AdminLoginScreen(this,
                    () -> switchContent(new AdminScreen(this).build())).build();
            default          -> buildHome();
        };

        if (contentArea.getChildren().isEmpty()) {
            contentArea.getChildren().setAll(page);
            Animations.pageEnter(page);
        } else {
            javafx.scene.Node old = contentArea.getChildren().get(0);
            Animations.pageExit(old, () -> {
                contentArea.getChildren().setAll(page);
                Animations.pageEnter(page);
            });
        }
    }

    void switchContent(javafx.scene.Node newPage) {
        if (contentArea.getChildren().isEmpty()) {
            contentArea.getChildren().setAll(newPage);
            Animations.pageEnter(newPage);
        } else {
            javafx.scene.Node old = contentArea.getChildren().get(0);
            Animations.pageExit(old, () -> {
                contentArea.getChildren().setAll(newPage);
                Animations.pageEnter(newPage);
            });
        }
    }

    /**
     * Navigates directly to AdminScreen after a successful login.
     * Bypasses pageExit/pageEnter entirely to avoid animation race conditions.
     */
    void showAdmin() {
        activeNav = "admin";
        sidebar.lookupAll("*").forEach(n -> {
            if (n instanceof HBox item && item.getUserData() instanceof Object[] data) {
                String itemId = (String) data[0];
                ((Runnable)(itemId.equals("admin") ? data[1] : data[2])).run();
            }
        });
        javafx.scene.Node adminPage = new AdminScreen(this).build();
        adminPage.setOpacity(1);
        adminPage.setTranslateX(0);
        contentArea.getChildren().setAll(adminPage);
    }

    // ── Home page ─────────────────────────────────────────────────────────

    javafx.scene.Node buildHome() {
        ScrollPane sp = new ScrollPane();
        sp.setFitToWidth(true);
        sp.setFitToHeight(false);
        sp.setStyle("-fx-background: " + Theme.BG_BASE() + "; -fx-background-color: " + Theme.BG_BASE() + "; -fx-border-color: transparent;");

        VBox page = new VBox(28);
        page.setPadding(new Insets(40, 48, 40, 48));
        page.setStyle("-fx-background-color: " + Theme.BG_BASE() + ";");
        page.setFillWidth(true);

        VBox hero = new VBox(8);
        Label hi = new Label("Good day 👋");
        hi.setFont(Font.font("System", 13));
        hi.setTextFill(Color.web(Theme.TEXT_M()));
        Label heroTitle = new Label("Parking Lot\nManagement");
        heroTitle.setFont(Font.font("Georgia", FontWeight.BOLD, 40));
        heroTitle.setTextFill(Color.web(Theme.TEXT_H()));
        heroTitle.setLineSpacing(2);
        Label heroSub = new Label("Monitor, manage, and operate your parking facility from one place.");
        heroSub.setFont(Font.font("System", 14));
        heroSub.setTextFill(Color.web(Theme.TEXT_M()));
        hero.getChildren().addAll(hi, heroTitle, heroSub);

        Label statsHdr = sectionTitle("QUICK STATS");
        var lot = com.parking.model.ParkingLot.getInstance();
        HBox stats = new HBox(14);
        stats.setFillHeight(true);
        var sc1 = miniStatCard("Total Spots",   String.valueOf(lot.getTotalSpots()),                    Theme.ACCENT());
        var sc2 = miniStatCard("Available",     String.valueOf(lot.getAvailableCount()),                 Theme.SUCCESS());
        var sc3 = miniStatCard("Occupied",      String.valueOf(lot.getOccupiedCount()),                  Theme.WARNING());
        var sc4 = miniStatCard("Revenue (USD)", String.format("%.0f", TICKET_MANAGER.getTotalRevenue()), Theme.INFO());
        for (var c : new VBox[]{sc1, sc2, sc3, sc4}) {
            HBox.setHgrow(c, Priority.ALWAYS);
            c.setMaxWidth(Double.MAX_VALUE);
        }
        stats.getChildren().addAll(sc1, sc2, sc3, sc4);

        Label actionsHdr = sectionTitle("QUICK ACTIONS");
        HBox actions = new HBox(16);
        var ac1 = actionCard("🚗", "Vehicle Entry",  "Record arrival & issue ticket", Theme.ACCENT(),  "entry");
        var ac2 = actionCard("💳", "Payment & Exit", "Process payment & open gate",   Theme.SUCCESS(), "payment");
        var ac3 = actionCard("📊", "Dashboard",      "Live lot status & analytics",   Theme.INFO(),    "dashboard");
        var ac4 = actionCard("⚙️",  "Admin Panel",    "Configure pricing & settings",  Theme.WARNING(), "admin");
        for (var c : new VBox[]{ac1, ac2, ac3, ac4}) {
            HBox.setHgrow(c, Priority.ALWAYS);
            c.setMaxWidth(Double.MAX_VALUE);
        }
        actions.getChildren().addAll(ac1, ac2, ac3, ac4);

        page.getChildren().addAll(hero, statsHdr, stats, actionsHdr, actions);
        sp.setContent(page);

        Platform.runLater(() -> {
            Animations.staggerCards(80, 60,  sc1, sc2, sc3, sc4);
            Animations.staggerCards(320, 70, ac1, ac2, ac3, ac4);
        });

        return sp;
    }

    private VBox miniStatCard(String label, String value, String color) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(18, 18, 18, 18));
        card.setStyle(
                "-fx-background-color: " + Theme.BG_SURFACE() + ";" +
                        "-fx-background-radius: 12;" +
                        "-fx-border-color: "      + Theme.BORDER()     + ";" +
                        "-fx-border-radius: 12;" +
                        "-fx-border-width: 1;"
        );
        Label val = new Label(value);
        val.setFont(Font.font("Georgia", FontWeight.BOLD, 30));
        val.setTextFill(Color.web(color));
        Label lbl = new Label(label);
        lbl.setFont(Font.font("System", 12));
        lbl.setTextFill(Color.web(Theme.TEXT_M()));
        card.getChildren().addAll(val, lbl);
        return card;
    }

    private VBox actionCard(String icon, String title, String desc, String color, String nav) {
        VBox card = new VBox(14);
        card.setPadding(new Insets(24, 22, 24, 22));
        card.setCursor(javafx.scene.Cursor.HAND);
        String base =
                "-fx-background-color: " + Theme.BG_SURFACE() + ";" +
                        "-fx-background-radius: 14;" +
                        "-fx-border-color: "      + Theme.BORDER()     + ";" +
                        "-fx-border-radius: 14;" +
                        "-fx-border-width: 1;";
        card.setStyle(base);

        StackPane iconCircle = new StackPane();
        iconCircle.setAlignment(Pos.CENTER);
        iconCircle.setPrefSize(44, 44);
        iconCircle.setMaxSize(44, 44);
        Circle circle = new Circle(22);
        circle.setFill(Color.web(color + "22"));
        Label iconLbl = new Label(icon);
        iconLbl.setFont(Font.font("System", 20));
        iconLbl.setAlignment(Pos.CENTER);
        iconCircle.getChildren().addAll(circle, iconLbl);

        Label titleLbl = new Label(title);
        titleLbl.setFont(Font.font("System", FontWeight.BOLD, 15));
        titleLbl.setTextFill(Color.web(Theme.TEXT_H()));
        Label descLbl = new Label(desc);
        descLbl.setFont(Font.font("System", 12));
        descLbl.setTextFill(Color.web(Theme.TEXT_M()));
        descLbl.setWrapText(true);
        Label arrow = new Label("Open →");
        arrow.setFont(Font.font("System", FontWeight.BOLD, 12));
        arrow.setTextFill(Color.web(color));

        card.getChildren().addAll(iconCircle, titleLbl, descLbl, arrow);
        card.setOnMouseEntered(e -> {
            card.setStyle(
                    "-fx-background-color: " + color + "11;" +
                            "-fx-background-radius: 14;" +
                            "-fx-border-color: "      + color + "66;" +
                            "-fx-border-radius: 14;" +
                            "-fx-border-width: 1;"
            );
            ScaleTransition st = new ScaleTransition(Duration.millis(120), card);
            st.setToX(1.03); st.setToY(1.03);
            st.setInterpolator(Interpolator.EASE_OUT);
            st.play();
        });
        card.setOnMouseExited(e -> {
            card.setStyle(base);
            ScaleTransition st = new ScaleTransition(Duration.millis(120), card);
            st.setToX(1.0); st.setToY(1.0);
            st.setInterpolator(Interpolator.EASE_OUT);
            st.play();
        });
        card.setOnMouseClicked(e -> { Animations.buttonPulse(card); showPage(nav); });
        return card;
    }

    // ── Shared helpers ────────────────────────────────────────────────────

    static Button primaryBtn(String text, String color) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPrefHeight(42);
        btn.setFont(Font.font("System", FontWeight.BOLD, 13));
        String s =
                "-fx-background-color: " + color + ";" +
                        "-fx-text-fill: white;" +
                        "-fx-background-radius: 10;" +
                        "-fx-cursor: hand;" +
                        "-fx-padding: 0 20 0 20;";
        btn.setStyle(s);
        btn.setOnMouseEntered(e -> btn.setStyle(s + "-fx-opacity:.85;"));
        btn.setOnMouseExited(e  -> btn.setStyle(s));
        return btn;
    }

    static Button ghostBtn(String text) {
        Button btn = new Button(text);
        btn.setPrefHeight(42);
        btn.setFont(Font.font("System", 13));
        String s =
                "-fx-background-color: transparent;" +
                        "-fx-text-fill: "    + Theme.TEXT_M()     + ";" +
                        "-fx-border-color: " + Theme.BORDER_LIT() + ";" +
                        "-fx-border-radius: 10;" +
                        "-fx-background-radius: 10;" +
                        "-fx-cursor: hand;" +
                        "-fx-padding: 0 18 0 18;";
        btn.setStyle(s);
        btn.setOnMouseEntered(e -> btn.setStyle(s + "-fx-background-color: " + Theme.BORDER() + ";"));
        btn.setOnMouseExited(e  -> btn.setStyle(s));
        return btn;
    }

    static TextField styledField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setPrefHeight(42);
        tf.setFont(Font.font("System", 13));
        String base =
                "-fx-background-color: " + Theme.BG_BASE()  + ";" +
                        "-fx-text-fill: "        + Theme.TEXT_H()   + ";" +
                        "-fx-prompt-text-fill: " + Theme.TEXT_M()   + ";" +
                        "-fx-background-radius: 10;" +
                        "-fx-border-radius: 10;" +
                        "-fx-border-width: 1;" +
                        "-fx-padding: 0 14 0 14;";
        tf.setStyle(base + "-fx-border-color: " + Theme.BORDER_LIT() + ";");
        tf.focusedProperty().addListener((obs, o, f) ->
                tf.setStyle(base + "-fx-border-color: " + (f ? Theme.ACCENT() : Theme.BORDER_LIT()) + ";"));
        return tf;
    }

    static VBox fieldGroup(String labelText, javafx.scene.Node input) {
        VBox g = new VBox(6);
        Label l = new Label(labelText);
        l.setFont(Font.font("System", FontWeight.BOLD, 12));
        l.setTextFill(Color.web(Theme.TEXT_M()));
        g.getChildren().addAll(l, input);
        return g;
    }

    static VBox pageCard(String title) {
        VBox card = new VBox(20);
        card.setPadding(new Insets(28));
        card.setStyle(
                "-fx-background-color: " + Theme.BG_SURFACE() + ";" +
                        "-fx-background-radius: 16;" +
                        "-fx-border-color: "      + Theme.BORDER()     + ";" +
                        "-fx-border-radius: 16;" +
                        "-fx-border-width: 1;"
        );
        if (title != null && !title.isBlank()) {
            Label t = new Label(title);
            t.setFont(Font.font("Georgia", FontWeight.BOLD, 17));
            t.setTextFill(Color.web(Theme.TEXT_H()));
            Separator sep = new Separator();
            sep.setStyle("-fx-background-color: " + Theme.BORDER() + ";");
            card.getChildren().addAll(t, sep);
        }
        return card;
    }

    static Label statusBadge(String text, String color) {
        Label l = new Label(text);
        l.setFont(Font.font("System", FontWeight.BOLD, 12));
        l.setTextFill(Color.web(color));
        l.setPadding(new Insets(5, 12, 5, 12));
        l.setStyle(
                "-fx-background-color: " + color + "22;" +
                        "-fx-background-radius: 20;" +
                        "-fx-border-color: "      + color + "55;" +
                        "-fx-border-radius: 20;" +
                        "-fx-border-width: 1;"
        );
        return l;
    }

    static Label pageTitle(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Georgia", FontWeight.BOLD, 26));
        l.setTextFill(Color.web(Theme.TEXT_H()));
        return l;
    }

    static Label sectionTitle(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("System", FontWeight.BOLD, 11));
        l.setTextFill(Color.web(Theme.TEXT_M()));
        return l;
    }

    static Label makeFieldLabel(String t) { return sectionTitle(t); }
    static Button makeButton(String t, String c) { return primaryBtn(t, c); }

    public static void main(String[] args) { launch(args); }
}