package com.parking.ui;

import com.parking.db.CustomerRepository;
import com.parking.enums.VehicleType;
import com.parking.model.Ticket;
import com.parking.model.Vehicle;
import com.parking.util.DummyDataGenerator;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.*;

public class EntryScreen {

    private final ParkingApp         app;
    private final CustomerRepository customerRepo;

    private TextField   plateField;
    private TextField   nameField;
    private TextField   phoneField;
    private ToggleGroup typeGroup;
    private Label       statusLabel;
    private VBox        resultCard;

    public EntryScreen(ParkingApp app) {
        this.app          = app;
        this.customerRepo = new CustomerRepository();
    }

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
        var formCard    = buildFormCard();
        var resultCard2 = buildResultCard();
        HBox.setHgrow(formCard,    Priority.ALWAYS);
        HBox.setHgrow(resultCard2, Priority.ALWAYS);
        formCard.setMaxWidth(Double.MAX_VALUE);
        resultCard2.setMaxWidth(Double.MAX_VALUE);
        mainRow.getChildren().addAll(formCard, resultCard2);

        page.getChildren().addAll(header, mainRow);
        sp.setContent(page);

        javafx.application.Platform.runLater(() ->
                Animations.staggerCards(60, 80, formCard, resultCard2));

        return sp;
    }

    // ── Form card ─────────────────────────────────────────────────────────

    private VBox buildFormCard() {
        VBox card = ParkingApp.pageCard("New Entry");
        card.setMaxWidth(Double.MAX_VALUE);

        // ── License plate field (with auto-fill on focus-lost) ──
        plateField = ParkingApp.styledField("e.g.  GK-447-TN");
        plateField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) autoFillCustomer();
        });
        VBox plateGroup = ParkingApp.fieldGroup("LICENSE PLATE", plateField);

        // ── Customer name & phone side-by-side ──
        nameField  = ParkingApp.styledField("e.g.  Giorgi Beridze  (optional)");
        phoneField = ParkingApp.styledField("e.g.  +995 555 123 456  (optional)");

        VBox nameGroup  = ParkingApp.fieldGroup("CUSTOMER NAME", nameField);
        VBox phoneGroup = ParkingApp.fieldGroup("PHONE NUMBER",  phoneField);

        HBox customerRow = new HBox(12);
        HBox.setHgrow(nameGroup,  Priority.ALWAYS);
        HBox.setHgrow(phoneGroup, Priority.ALWAYS);
        nameGroup.setMaxWidth(Double.MAX_VALUE);
        phoneGroup.setMaxWidth(Double.MAX_VALUE);
        customerRow.getChildren().addAll(nameGroup, phoneGroup);

        // Auto-fill hint label
        Label autoFillHint = new Label("ℹ  Known customers are auto-filled when you enter their plate.");
        autoFillHint.setFont(Font.font("System", 11));
        autoFillHint.setTextFill(Color.web(ParkingApp.TEXT_M));

        // ── Vehicle type selector ──
        Label typeLabel = new Label("VEHICLE TYPE");
        typeLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        typeLabel.setTextFill(Color.web(ParkingApp.TEXT_M));

        typeGroup = new ToggleGroup();
        HBox typeRow = new HBox(10);
        typeRow.getChildren().addAll(
                typeChip("🚗  Car",        VehicleType.CAR,        true),
                typeChip("🏍  Motorcycle", VehicleType.MOTORCYCLE, false),
                typeChip("🚛  Truck",      VehicleType.TRUCK,      false)
        );

        VBox typeGroup2 = new VBox(6);
        typeGroup2.getChildren().addAll(typeLabel, typeRow);

        // ── Status banner ──
        statusLabel = new Label("");
        statusLabel.setFont(Font.font("System", 12));
        statusLabel.setWrapText(true);
        statusLabel.setVisible(false);
        statusLabel.setPadding(new Insets(10, 14, 10, 14));

        // ── Buttons ──
        Button submit = ParkingApp.primaryBtn("Issue Ticket  →", ParkingApp.ACCENT);
        submit.setOnAction(e -> { Animations.buttonPulse(submit); handleIssue(); });

        Button dummyBtn = buildDummyButton();

        Button clear = ParkingApp.ghostBtn("Clear");
        clear.setOnAction(e -> {
            plateField.clear();
            nameField.clear();
            phoneField.clear();
            statusLabel.setVisible(false);
            resultCard.setVisible(false);
        });

        HBox btnRow = new HBox(10);
        HBox.setHgrow(submit, Priority.ALWAYS);
        btnRow.getChildren().addAll(submit, dummyBtn, clear);

        card.getChildren().addAll(
                plateGroup,
                customerRow,
                autoFillHint,
                typeGroup2,
                statusLabel,
                btnRow
        );
        return card;
    }

    // ── Dummy data button ─────────────────────────────────────────────────

    /**
     * Builds the "Fill with Dummy Data" button.
     *
     * Clicking it generates a random Georgian plate, a random full name,
     * a random Georgian phone number, and picks a random vehicle type —
     * all using {@link DummyDataGenerator} — then populates the form fields.
     *
     * The button is styled distinctly (warning/amber tone) so it's easy
     * to spot and won't be confused for a production action.
     */
    private Button buildDummyButton() {
        Button btn = new Button("🎲  Dummy Data");
        btn.setCursor(javafx.scene.Cursor.HAND);
        btn.setFont(Font.font("System", 13));
        btn.setTooltip(new Tooltip("Fill all fields with randomly generated Georgian test data"));

        String base =
                "-fx-background-radius: 10;" +
                        "-fx-border-radius: 10;" +
                        "-fx-border-width: 1;" +
                        "-fx-padding: 9 18 9 18;";

        String normal =
                base +
                        "-fx-background-color: " + ParkingApp.WARNING + "22;" +
                        "-fx-text-fill: "        + ParkingApp.WARNING + ";" +
                        "-fx-border-color: "     + ParkingApp.WARNING + "66;";

        String hover =
                base +
                        "-fx-background-color: " + ParkingApp.WARNING + "44;" +
                        "-fx-text-fill: "        + ParkingApp.WARNING + ";" +
                        "-fx-border-color: "     + ParkingApp.WARNING + ";";

        btn.setStyle(normal);
        btn.setOnMouseEntered(e -> btn.setStyle(hover));
        btn.setOnMouseExited(e  -> btn.setStyle(normal));

        btn.setOnAction(e -> {
            Animations.buttonPulse(btn);
            fillDummyData();
        });

        return btn;
    }

    /**
     * Generates random Georgian test data and populates all form fields.
     * Vehicle type chip is also switched to match the generated type.
     */
    private void fillDummyData() {
        // Generate data
        String      plate     = DummyDataGenerator.plate();
        String      name      = DummyDataGenerator.fullNameLatin();
        String      phone     = DummyDataGenerator.phone();
        VehicleType type      = randomWeightedType();

        // Populate fields
        plateField.setText(plate);
        nameField.setText(name);
        phoneField.setText(phone);

        // Switch the type chip
        typeGroup.getToggles().stream()
                .filter(t -> t.getUserData() == type)
                .findFirst()
                .ifPresent(typeGroup::selectToggle);

        // Show a subtle confirmation in the status banner
        showStatus("🎲  Dummy data filled — plate " + plate + ", " + type.getDisplayName().toLowerCase() + ".",
                ParkingApp.WARNING);
    }

    /**
     * Returns a weighted random vehicle type.
     * Reflects realistic parking lot distribution:
     * ~70% cars, ~20% motorcycles, ~10% trucks.
     */
    private VehicleType randomWeightedType() {
        int roll = (int) (Math.random() * 10);
        if (roll < 7) return VehicleType.CAR;
        if (roll < 9) return VehicleType.MOTORCYCLE;
        return VehicleType.TRUCK;
    }

    // ── Result card ───────────────────────────────────────────────────────

    private VBox buildResultCard() {
        resultCard = ParkingApp.pageCard("Issued Ticket");
        resultCard.setMinWidth(320);
        resultCard.setVisible(false);

        Label placeholder = new Label("Ticket details will appear here.");
        placeholder.setFont(Font.font("System", 13));
        placeholder.setTextFill(Color.web(ParkingApp.TEXT_M));
        resultCard.getChildren().add(placeholder);

        return resultCard;
    }

    // ── Auto-fill known customer ──────────────────────────────────────────

    /**
     * Called when the plate field loses focus.
     * If the plate is already in the customers table, pre-fills name & phone.
     */
    private void autoFillCustomer() {
        String plate = plateField.getText().trim().toUpperCase();
        if (plate.isBlank()) return;

        CustomerRepository.CustomerRecord rec = customerRepo.findByPlate(plate);
        if (rec != null) {
            if (nameField.getText().isBlank())
                nameField.setText(rec.fullName.equals("Guest") ? "" : rec.fullName);
            if (phoneField.getText().isBlank())
                phoneField.setText(rec.phone);

            try {
                VehicleType storedType = VehicleType.valueOf(rec.vehicleType);
                typeGroup.getToggles().stream()
                        .filter(t -> t.getUserData() == storedType)
                        .findFirst()
                        .ifPresent(t -> typeGroup.selectToggle(t));
            } catch (IllegalArgumentException ignored) { /* unknown type — leave as is */ }
        }
    }

    // ── Handler ───────────────────────────────────────────────────────────

    private void handleIssue() {
        String plate = plateField.getText().trim().toUpperCase();
        if (plate.isBlank()) { showStatus("Please enter a license plate.", ParkingApp.DANGER); return; }

        VehicleType type = (VehicleType) typeGroup.getSelectedToggle().getUserData();

        try {
            Vehicle vehicle = new Vehicle(plate, type);
            Ticket  ticket  = ParkingApp.TICKET_MANAGER.issueTicket(vehicle);

            String name  = nameField.getText().trim();
            String phone = phoneField.getText().trim();
            if (!name.isBlank() || !phone.isBlank()) {
                String savedName  = name.isBlank()  ? "Guest" : name;
                String savedPhone = phone.isBlank() ? ""      : phone;
                customerRepo.updateCustomerInfo(plate, savedName, savedPhone);
            }

            showStatus("✓  Ticket issued successfully!", ParkingApp.SUCCESS);
            populateResult(ticket, name.isBlank() ? "Guest" : name);
            Animations.cardPop(resultCard);
            plateField.clear();
            nameField.clear();
            phoneField.clear();

        } catch (IllegalStateException | IllegalArgumentException e) {
            showStatus("✗  " + e.getMessage(), ParkingApp.DANGER);
        }
    }

    private void populateResult(Ticket t, String customerName) {
        resultCard.getChildren().clear();

        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Issued Ticket");
        title.setFont(Font.font("Georgia", FontWeight.BOLD, 17));
        title.setTextFill(Color.web(ParkingApp.TEXT_H));
        Label badge = ParkingApp.statusBadge("ACTIVE", ParkingApp.SUCCESS);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        titleRow.getChildren().addAll(title, sp, badge);

        Separator sep = new Separator();

        VBox idBox = new VBox(4);
        idBox.setPadding(new Insets(14, 16, 14, 16));
        idBox.setStyle(
                "-fx-background-color: " + ParkingApp.ACCENT + "18;" +
                        "-fx-background-radius: 10;" +
                        "-fx-border-color: "     + ParkingApp.ACCENT + "44;" +
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

        VBox details = new VBox(0);
        details.setStyle(
                "-fx-background-color: " + ParkingApp.BG_RAISED + ";" +
                        "-fx-background-radius: 10;"
        );
        details.getChildren().addAll(
                detailRow("Customer",      customerName,                              false),
                detailRow("License Plate", t.getVehicle().getLicensePlate(),          true),
                detailRow("Vehicle Type",  t.getVehicle().getType().getDisplayName(), false),
                detailRow("Assigned Spot", t.getSpot().getSpotId(),                  true),
                detailRow("Floor",         "Floor " + t.getSpot().getFloor(),         false),
                detailRow("Entry Time",    t.getIssuedAt().toLocalTime().toString(),  true)
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
                        "-fx-border-color: "     + color + "44;" +
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
                        "-fx-border-color: "     + ParkingApp.ACCENT + ";" +
                        "-fx-border-radius: 8;" +
                        "-fx-border-width: 1;");
            } else {
                tb.setStyle(baseStyle +
                        "-fx-background-color: " + ParkingApp.BG_RAISED + ";" +
                        "-fx-text-fill: "         + ParkingApp.TEXT_M + ";" +
                        "-fx-border-color: "      + ParkingApp.BORDER_LIT + ";" +
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