package com.parking.ui;

import com.parking.service.TicketManager;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

/**
 * JavaFX entry point for the Parking Lot Ticket Simulator.
 *
 * <p>Launches the main window and holds the single shared
 * {@link TicketManager} instance that all screens use.</p>
 *
 * <h3>How to run (IntelliJ)</h3>
 * <ol>
 *   <li>Add the JavaFX SDK to your project libraries
 *       (File → Project Structure → Libraries → + → JavaFX lib/ folder).</li>
 *   <li>In Run Configuration add VM options:
 *       {@code --module-path /path/to/javafx/lib --add-modules javafx.controls,javafx.fxml}</li>
 *   <li>Set main class to {@code com.parking.ui.ParkingApp}.</li>
 * </ol>
 */
public class ParkingApp extends Application {

    /** Shared service — one instance for the whole application lifetime. */
    static final TicketManager TICKET_MANAGER = new TicketManager();

    // ── Styling constants (used by all screens) ───────────────────────────
    static final String BG_DARK    = "#0F172A";
    static final String BG_CARD    = "#1E293B";
    static final String BG_NAVY    = "#1E3A5F";
    static final String BLUE       = "#2563EB";
    static final String TEAL       = "#0EA5E9";
    static final String GREEN      = "#10B981";
    static final String YELLOW     = "#F59E0B";
    static final String RED        = "#EF4444";
    static final String TEXT_WHITE = "#F1F5F9";
    static final String TEXT_MUTED = "#94A3B8";

    private Stage primaryStage;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle("🅿 Parking Lot Ticket Simulator");
        stage.setMinWidth(900);
        stage.setMinHeight(650);
        showHome();
        stage.show();
    }

    // ── Navigation hub ────────────────────────────────────────────────────

    void showHome() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + BG_DARK + ";");

        // ── Header ──
        VBox header = new VBox(6);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(40, 20, 30, 20));

        Label pLabel = new Label("🅿");
        pLabel.setFont(Font.font("System", FontWeight.BOLD, 52));
        pLabel.setTextFill(Color.web(BLUE));

        Label title = new Label("Parking Lot Ticket Simulator");
        title.setFont(Font.font("System", FontWeight.BOLD, 28));
        title.setTextFill(Color.web(TEXT_WHITE));

        Label subtitle = new Label("Select an operation to continue");
        subtitle.setFont(Font.font("System", 14));
        subtitle.setTextFill(Color.web(TEXT_MUTED));

        header.getChildren().addAll(pLabel, title, subtitle);

        // ── Action cards ──
        HBox cards = new HBox(20);
        cards.setAlignment(Pos.CENTER);
        cards.setPadding(new Insets(0, 40, 0, 40));

        cards.getChildren().addAll(
                makeNavCard("🚗", "Vehicle Entry",
                        "Record a new arrival,\nassign a spot & issue ticket",
                        BLUE,
                        () -> new EntryScreen(this).show(primaryStage)),

                makeNavCard("💳", "Payment & Exit",
                        "Pay for ticket, calculate\nfee & release the spot",
                        GREEN,
                        () -> new PaymentScreen(this).show(primaryStage)),

                makeNavCard("📊", "Dashboard",
                        "Live lot status, active\ntickets & session revenue",
                        TEAL,
                        () -> new DashboardScreen(this).show(primaryStage))
        );

        // ── Footer ──
        Label footer = new Label("All classes (Vehicle · Ticket · ParkingLot · FeeCalculator) are running behind the scenes.");
        footer.setFont(Font.font("System", 11));
        footer.setTextFill(Color.web(TEXT_MUTED));
        footer.setPadding(new Insets(30, 0, 20, 0));
        footer.setAlignment(Pos.CENTER);

        root.setTop(header);
        root.setCenter(cards);
        root.setBottom(footer);
        BorderPane.setAlignment(footer, Pos.CENTER);

        primaryStage.setScene(new Scene(root, 960, 640));
    }

    // ── Reusable card builder ─────────────────────────────────────────────

    static VBox makeNavCard(String icon, String title, String desc,
                            String accentColor, Runnable onClick) {
        VBox card = new VBox(12);
        card.setAlignment(Pos.CENTER);
        card.setPrefSize(240, 220);
        card.setPadding(new Insets(30, 20, 30, 20));
        card.setStyle(
                "-fx-background-color: " + BG_CARD + ";" +
                        "-fx-background-radius: 12;" +
                        "-fx-cursor: hand;"
        );

        Label iconLabel = new Label(icon);
        iconLabel.setFont(Font.font("System", 44));

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        titleLabel.setTextFill(Color.web(accentColor));

        Label descLabel = new Label(desc);
        descLabel.setFont(Font.font("System", 12));
        descLabel.setTextFill(Color.web(TEXT_MUTED));
        descLabel.setAlignment(Pos.CENTER);
        descLabel.setWrapText(true);

        Button btn = makeButton("Open →", accentColor);
        btn.setOnAction(e -> onClick.run());

        // Hover effect
        card.setOnMouseEntered(e -> card.setStyle(
                "-fx-background-color: " + BG_NAVY + ";" +
                        "-fx-background-radius: 12;" +
                        "-fx-cursor: hand;"
        ));
        card.setOnMouseExited(e -> card.setStyle(
                "-fx-background-color: " + BG_CARD + ";" +
                        "-fx-background-radius: 12;" +
                        "-fx-cursor: hand;"
        ));
        card.setOnMouseClicked(e -> onClick.run());

        card.getChildren().addAll(iconLabel, titleLabel, descLabel, btn);
        return card;
    }

    // ── Shared widget factory methods (used by all screens) ───────────────

    static Button makeButton(String text, String color) {
        Button btn = new Button(text);
        btn.setStyle(
                "-fx-background-color: " + color + ";" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 13px;" +
                        "-fx-background-radius: 8;" +
                        "-fx-padding: 10 22 10 22;" +
                        "-fx-cursor: hand;"
        );
        btn.setOnMouseEntered(e -> btn.setStyle(
                "-fx-background-color: derive(" + color + ", -15%);" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 13px;" +
                        "-fx-background-radius: 8;" +
                        "-fx-padding: 10 22 10 22;" +
                        "-fx-cursor: hand;"
        ));
        btn.setOnMouseExited(e -> btn.setStyle(
                "-fx-background-color: " + color + ";" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 13px;" +
                        "-fx-background-radius: 8;" +
                        "-fx-padding: 10 22 10 22;" +
                        "-fx-cursor: hand;"
        ));
        return btn;
    }

    static Label makeFieldLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("System", FontWeight.BOLD, 12));
        l.setTextFill(Color.web(TEXT_MUTED));
        return l;
    }

    static Label sectionTitle(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("System", FontWeight.BOLD, 18));
        l.setTextFill(Color.web(TEXT_WHITE));
        return l;
    }

    public static void main(String[] args) {
        launch(args);
    }
}