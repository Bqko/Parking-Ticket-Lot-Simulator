package com.parking.ui;

import com.parking.db.AdminRepository;
import javafx.animation.*;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.util.Duration;

/**
 * Login screen that guards the Admin Panel.
 *
 * <p>Shown whenever the user clicks "Admin Panel" in the sidebar.
 * On successful authentication the callback {@code onSuccess} is invoked,
 * which switches the content area to {@link AdminScreen}.</p>
 */
public class AdminLoginScreen {

    private final ParkingApp        app;
    private final AdminRepository   adminRepo;
    private final Runnable          onSuccess;   // called after successful login

    private TextField     usernameField;
    private PasswordField passwordField;
    private Label         statusLabel;
    private Button        loginBtn;

    // Track failed attempts to add a short lockout
    private int  failedAttempts = 0;
    private long lockedUntil   = 0;

    public AdminLoginScreen(ParkingApp app, Runnable onSuccess) {
        this.app       = app;
        this.adminRepo = new AdminRepository();
        this.onSuccess = onSuccess;
    }

    Node build() {
        // Full-page centered layout
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: " + ParkingApp.BG_BASE + ";");

        VBox card = buildLoginCard();

        // Subtle animated background dots (decorative)
        root.getChildren().addAll(buildBgDecor(), card);
        StackPane.setAlignment(card, Pos.CENTER);

        return root;
    }

    // ── Login card ────────────────────────────────────────────────────────

    private VBox buildLoginCard() {
        VBox card = new VBox(24);
        card.setPadding(new Insets(48, 48, 48, 48));
        card.setMaxWidth(420);
        card.setMinWidth(380);
        card.setStyle(
                "-fx-background-color: " + ParkingApp.BG_SURFACE + ";" +
                        "-fx-background-radius: 20;" +
                        "-fx-border-color: " + ParkingApp.BORDER + ";" +
                        "-fx-border-radius: 20;" +
                        "-fx-border-width: 1;" +
                        "-fx-effect: dropshadow(gaussian, #00000088, 40, 0.3, 0, 8);"
        );

        // ── Header ──
        VBox header = new VBox(8);
        header.setAlignment(Pos.CENTER);

        // Lock icon circle
        StackPane iconCircle = new StackPane();
        Circle bg = new Circle(30);
        bg.setFill(Color.web(ParkingApp.ACCENT + "22"));
        bg.setStroke(Color.web(ParkingApp.ACCENT + "55"));
        bg.setStrokeWidth(1.5);
        Label iconLbl = new Label("🔐");
        iconLbl.setFont(Font.font("System", 24));
        iconCircle.getChildren().addAll(bg, iconLbl);

        Label title = new Label("Admin Login");
        title.setFont(Font.font("Georgia", FontWeight.BOLD, 26));
        title.setTextFill(Color.web(ParkingApp.TEXT_H));

        Label sub = new Label("Enter your credentials to access the Admin Panel.");
        sub.setFont(Font.font("System", 13));
        sub.setTextFill(Color.web(ParkingApp.TEXT_M));
        sub.setWrapText(true);
        sub.setTextAlignment(TextAlignment.CENTER);
        sub.setMaxWidth(320);

        header.getChildren().addAll(iconCircle, title, sub);

        // ── Divider ──
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: " + ParkingApp.BORDER + ";");

        // ── Fields ──
        usernameField = ParkingApp.styledField("Username");
        usernameField.setOnAction(e -> passwordField.requestFocus());
        VBox usernameGroup = ParkingApp.fieldGroup("USERNAME", usernameField);

        passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setPrefHeight(42);
        passwordField.setFont(Font.font("System", 13));
        String pfBase =
                "-fx-background-color: " + ParkingApp.BG_BASE + ";" +
                        "-fx-text-fill: " + ParkingApp.TEXT_H + ";" +
                        "-fx-prompt-text-fill: " + ParkingApp.TEXT_M + ";" +
                        "-fx-background-radius: 10;" +
                        "-fx-border-radius: 10;" +
                        "-fx-border-width: 1;" +
                        "-fx-padding: 0 14 0 14;";
        passwordField.setStyle(pfBase + "-fx-border-color: " + ParkingApp.BORDER_LIT + ";");
        passwordField.focusedProperty().addListener((obs, o, f) ->
                passwordField.setStyle(pfBase + "-fx-border-color: " + (f ? ParkingApp.ACCENT : ParkingApp.BORDER_LIT) + ";"));
        passwordField.setOnAction(e -> handleLogin());
        VBox passwordGroup = ParkingApp.fieldGroup("PASSWORD", passwordField);

        // ── Status banner (hidden until needed) ──
        statusLabel = new Label("");
        statusLabel.setFont(Font.font("System", 13));
        statusLabel.setWrapText(true);
        statusLabel.setVisible(false);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        statusLabel.setPadding(new Insets(10, 14, 10, 14));

        // ── Login button ──
        loginBtn = ParkingApp.primaryBtn("Login  →", ParkingApp.ACCENT);
        loginBtn.setOnAction(e -> { Animations.buttonPulse(loginBtn); handleLogin(); });

        // ── Back link ──
        Button backBtn = ParkingApp.ghostBtn("← Back to Home");
        backBtn.setMaxWidth(Double.MAX_VALUE);
        backBtn.setOnAction(e -> app.showPage("home"));

        card.getChildren().addAll(
                header, sep,
                usernameGroup, passwordGroup,
                statusLabel,
                loginBtn,
                backBtn
        );

        // Entrance animation
        card.setOpacity(0);
        card.setTranslateY(20);
        javafx.application.Platform.runLater(() -> {
            FadeTransition ft = new FadeTransition(Duration.millis(350), card);
            ft.setToValue(1);
            TranslateTransition tt = new TranslateTransition(Duration.millis(350), card);
            tt.setToY(0);
            tt.setInterpolator(Interpolator.EASE_OUT);
            new ParallelTransition(ft, tt).play();
        });

        return card;
    }

    // ── Background decoration ─────────────────────────────────────────────

    private Pane buildBgDecor() {
        Pane pane = new Pane();
        pane.setMouseTransparent(true);

        // A few large, very faint circles to add depth
        int[][] circles = {
                {-80, -80, 260},
                {900, 600, 200},
                {700, -50, 150},
                {100, 700, 180}
        };
        for (int[] c : circles) {
            Circle circle = new Circle(c[2]);
            circle.setCenterX(c[0] + c[2]);
            circle.setCenterY(c[1] + c[2]);
            circle.setFill(Color.web(ParkingApp.ACCENT + "08"));
            circle.setStroke(Color.web(ParkingApp.ACCENT + "12"));
            circle.setStrokeWidth(1);
            pane.getChildren().add(circle);
        }
        return pane;
    }

    // ── Handler ───────────────────────────────────────────────────────────

    private void handleLogin() {
        // Check lockout
        if (System.currentTimeMillis() < lockedUntil) {
            long secsLeft = (lockedUntil - System.currentTimeMillis()) / 1000;
            showStatus("Too many failed attempts. Try again in " + secsLeft + "s.", ParkingApp.WARNING);
            return;
        }

        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isBlank()) { showStatus("Please enter your username.", ParkingApp.DANGER); return; }
        if (password.isBlank()) { showStatus("Please enter your password.", ParkingApp.DANGER); return; }

        loginBtn.setDisable(true);
        loginBtn.setText("Authenticating…");

        // Slight delay for UX (avoids instant flash)
        PauseTransition pause = new PauseTransition(Duration.millis(400));
        pause.setOnFinished(e -> {
            AdminRepository.AdminRecord admin = adminRepo.authenticate(username, password);
            loginBtn.setDisable(false);
            loginBtn.setText("Login  →");

            if (admin != null) {
                failedAttempts = 0;
                showStatus("✓  Welcome, " + admin.displayName + "!", ParkingApp.SUCCESS);
                // Short delay then switch to admin screen
                PauseTransition nav = new PauseTransition(Duration.millis(600));
                nav.setOnFinished(ev -> onSuccess.run());
                nav.play();
            } else {
                failedAttempts++;
                passwordField.clear();
                if (failedAttempts >= 5) {
                    lockedUntil = System.currentTimeMillis() + 30_000; // 30 sec lockout
                    showStatus("✗  Too many failed attempts. Locked for 30 seconds.", ParkingApp.DANGER);
                } else {
                    int remaining = 5 - failedAttempts;
                    showStatus("✗  Invalid username or password. " + remaining + " attempt(s) remaining.", ParkingApp.DANGER);
                }
                Animations.shake(statusLabel);
            }
        });
        pause.play();
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
    }
}