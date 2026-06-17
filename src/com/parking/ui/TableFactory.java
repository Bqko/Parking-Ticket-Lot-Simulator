package com.parking.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Shared factory for all TableViews in ParkSpace.
 *
 * <p>Produces consistently styled tables with:
 * <ul>
 *   <li>Bold accent header with sort indicators</li>
 *   <li>Alternating row shading</li>
 *   <li>Row hover highlight</li>
 *   <li>Colored status badge cells</li>
 *   <li>Optional search / filter bar</li>
 *   <li>Empty-state placeholder</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 *   // 1. Build a table with a search bar
 *   TableFactory.Result&lt;MyRow&gt; result = TableFactory.build(myData, "Search...",
 *       (row, query) -> row.getName().toLowerCase().contains(query));
 *
 *   // 2. Add columns
 *   result.table().getColumns().addAll(
 *       TableFactory.textCol("Name",   r -> r.getName(),   180),
 *       TableFactory.badgeCol("Status", r -> r.getStatus(), r -> badgeColor(r))
 *   );
 *
 *   // 3. Place the container (search bar + table) in your layout
 *   card.getChildren().add(result.container());
 * </pre>
 */
public class TableFactory {

    // ── Result wrapper ────────────────────────────────────────────────────

    public record Result<T>(TableView<T> table, VBox container,
                            FilteredList<T> filtered) {}

    // ── Main builder ──────────────────────────────────────────────────────

    /**
     * Builds a styled table with an integrated search bar.
     *
     * @param data        source data
     * @param searchHint  placeholder text for the search field
     * @param matcher     returns true when a row matches the search query
     */
    public static <T> Result<T> build(ObservableList<T> data,
                                      String searchHint,
                                      BiPredicate<T, String> matcher) {
        FilteredList<T> filtered = new FilteredList<>(data, p -> true);
        SortedList<T>   sorted   = new SortedList<>(filtered);

        TableView<T> table = styledTable();
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        // ── Search bar ────────────────────────────────────────────────────
        TextField searchField = ParkingApp.styledField(searchHint);
        searchField.setMaxWidth(Double.MAX_VALUE);
        searchField.textProperty().addListener((obs, o, query) -> {
            String q = query == null ? "" : query.trim().toLowerCase();
            filtered.setPredicate(row -> q.isBlank() || matcher.test(row, q));
        });

        Label searchIcon = new Label("🔍");
        searchIcon.setFont(Font.font("System", 13));
        searchIcon.setPadding(new Insets(0, 4, 0, 0));

        Button clearBtn = new Button("✕");
        clearBtn.setFont(Font.font("System", 11));
        clearBtn.setTextFill(Color.web(ParkingApp.TEXT_M));
        clearBtn.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-cursor: hand;" +
                        "-fx-padding: 4 8 4 8;");
        clearBtn.setOnAction(e -> searchField.clear());
        clearBtn.visibleProperty().bind(
                searchField.textProperty().isNotEmpty());

        Label resultCount = new Label();
        resultCount.setFont(Font.font("System", 11));
        resultCount.setTextFill(Color.web(ParkingApp.TEXT_M));
        filtered.addListener((javafx.collections.ListChangeListener<T>) c ->
                resultCount.setText(filtered.size() + " row" +
                        (filtered.size() == 1 ? "" : "s")));
        resultCount.setText(data.size() + " row" + (data.size() == 1 ? "" : "s"));

        HBox searchRow = new HBox(8);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        searchRow.getChildren().addAll(searchIcon, searchField, clearBtn, spacer, resultCount);

        VBox container = new VBox(10, searchRow, table);
        container.setMaxWidth(Double.MAX_VALUE);

        return new Result<>(table, container, filtered);
    }

    /**
     * Builds a table without a search bar.
     */
    public static <T> Result<T> buildSimple(ObservableList<T> data) {
        FilteredList<T> filtered = new FilteredList<>(data, p -> true);
        SortedList<T>   sorted   = new SortedList<>(filtered);
        TableView<T>    table    = styledTable();
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        VBox container = new VBox(table);
        return new Result<>(table, container, filtered);
    }

    // ── Column factories ──────────────────────────────────────────────────

    /**
     * Plain text column with sort indicator support.
     */
    public static <T> TableColumn<T, String> textCol(String header,
                                                     Function<T, String> valueGetter,
                                                     double width) {
        TableColumn<T, String> col = new TableColumn<>();
        col.setGraphic(styledHeader(header));
        col.setCellValueFactory(c ->
                new SimpleStringProperty(valueGetter.apply(c.getValue())));
        col.setPrefWidth(width);
        col.setMinWidth(60);
        col.setCellFactory(tc -> plainCell());
        col.setSortable(true);
        return col;
    }

    /**
     * Copyable text column — clicking the cell copies the value to the
     * system clipboard and briefly flashes a "✓ Copied" confirmation
     * inside the cell before restoring the original text.
     */
    public static <T> TableColumn<T, String> copyableCol(String header,
                                                         Function<T, String> valueGetter,
                                                         double width) {
        TableColumn<T, String> col = new TableColumn<>();
        col.setGraphic(styledHeader(header));
        col.setCellValueFactory(c ->
                new SimpleStringProperty(valueGetter.apply(c.getValue())));
        col.setPrefWidth(width);
        col.setMinWidth(60);
        col.setSortable(true);
        col.setCellFactory(tc -> new TableCell<>() {
            private javafx.animation.PauseTransition resetTimer;

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setFont(Font.font("System", 13));
                setPadding(new Insets(0, 14, 0, 14));
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                    setOnMouseClicked(null);
                    setCursor(null);
                    return;
                }

                setText(item);
                setTextFill(Color.web(ParkingApp.TEXT_H));
                // Underline hint + pointer cursor so it looks clickable
                setStyle("-fx-cursor: hand; -fx-underline: false;");
                setTooltip(new Tooltip("Click to copy"));

                setOnMouseClicked(e -> {
                    // Copy to clipboard
                    javafx.scene.input.ClipboardContent content =
                            new javafx.scene.input.ClipboardContent();
                    content.putString(item);
                    javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);

                    // Visual feedback — flash "✓ Copied"
                    if (resetTimer != null) resetTimer.stop();
                    setText("✓  Copied!");
                    setTextFill(Color.web(ParkingApp.SUCCESS));
                    setStyle("-fx-cursor: hand; -fx-font-weight: bold;");

                    resetTimer = new javafx.animation.PauseTransition(
                            javafx.util.Duration.millis(1200));
                    resetTimer.setOnFinished(ev -> {
                        setText(item);
                        setTextFill(Color.web(ParkingApp.TEXT_H));
                        setStyle("-fx-cursor: hand;");
                    });
                    resetTimer.play();
                });
            }
        });
        return col;
    }

    /**
     * Badge column — renders the value inside a colored pill badge.
     * {@code colorGetter} maps a row to a hex color string.
     */
    public static <T> TableColumn<T, String> badgeCol(String header,
                                                      Function<T, String> valueGetter,
                                                      Function<T, String> colorGetter,
                                                      double width) {
        TableColumn<T, String> col = new TableColumn<>();
        col.setGraphic(styledHeader(header));
        col.setCellValueFactory(c ->
                new SimpleStringProperty(valueGetter.apply(c.getValue())));
        col.setPrefWidth(width);
        col.setMinWidth(60);
        col.setSortable(true);
        col.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || getTableRow() == null
                        || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                T row   = getTableRow().getItem();
                String color = colorGetter.apply(row);
                Label badge = makeBadge(item, color);
                setGraphic(badge);
                setPadding(new Insets(6, 14, 6, 14));
            }
        });
        return col;
    }

    /**
     * Icon + text column — prepends a small colored dot to the cell value.
     */
    public static <T> TableColumn<T, String> dotCol(String header,
                                                    Function<T, String> valueGetter,
                                                    Function<T, String> colorGetter,
                                                    double width) {
        TableColumn<T, String> col = new TableColumn<>();
        col.setGraphic(styledHeader(header));
        col.setCellValueFactory(c ->
                new SimpleStringProperty(valueGetter.apply(c.getValue())));
        col.setPrefWidth(width);
        col.setMinWidth(60);
        col.setSortable(true);
        col.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || getTableRow() == null
                        || getTableRow().getItem() == null) {
                    setGraphic(null); setText(null);
                    return;
                }
                T row = getTableRow().getItem();
                String color = colorGetter.apply(row);
                Rectangle dot = new Rectangle(8, 8);
                dot.setArcWidth(8); dot.setArcHeight(8);
                dot.setFill(Color.web(color));
                Label lbl = new Label(item);
                lbl.setFont(Font.font("System", 13));
                lbl.setTextFill(Color.web(ParkingApp.TEXT_H));
                HBox cell = new HBox(8, dot, lbl);
                cell.setAlignment(Pos.CENTER_LEFT);
                setGraphic(cell);
                setText(null);
                setPadding(new Insets(6, 14, 6, 14));
            }
        });
        return col;
    }

    // ── Core table styling ────────────────────────────────────────────────

    private static <T> TableView<T> styledTable() {
        TableView<T> table = new TableView<>();
        table.setMaxWidth(Double.MAX_VALUE);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setFixedCellSize(46);

        // Placeholder
        Label placeholder = new Label("No data to display");
        placeholder.setFont(Font.font("System", 13));
        placeholder.setTextFill(Color.web(ParkingApp.TEXT_M));
        table.setPlaceholder(placeholder);

        applyCSS(table);
        addHoverEffect(table);
        return table;
    }

    private static <T> void applyCSS(TableView<T> table) {
        String css =
                // overall table
                ".table-view{" +
                        "-fx-background-color:" + ParkingApp.BG_SURFACE + ";" +
                        "-fx-border-color:" + ParkingApp.BORDER + ";" +
                        "-fx-border-radius:12;" +
                        "-fx-background-radius:12;}" +

                        // header row background
                        ".table-view .column-header-background{" +
                        "-fx-background-color:" + ParkingApp.BG_RAISED + ";" +
                        "-fx-background-radius:12 12 0 0;}" +

                        // individual header cells
                        ".table-view .column-header{" +
                        "-fx-background-color:transparent;" +
                        "-fx-border-color:transparent transparent " + ParkingApp.BORDER + " transparent;" +
                        "-fx-border-width:0 0 2 0;" +
                        "-fx-padding:0;}" +

                        // header label — overridden by setGraphic so this is a fallback
                        ".table-view .column-header .label{" +
                        "-fx-text-fill:" + ParkingApp.TEXT_H + ";" +
                        "-fx-font-weight:bold;" +
                        "-fx-font-size:12px;" +
                        "-fx-alignment:CENTER-LEFT;}" +

                        // data rows
                        ".table-row-cell{" +
                        "-fx-background-color:" + ParkingApp.BG_SURFACE + ";" +
                        "-fx-border-color:transparent transparent " + ParkingApp.BORDER + " transparent;" +
                        "-fx-border-width:0 0 1 0;" +
                        "-fx-table-cell-border-color:transparent;}" +

                        // alternating rows
                        ".table-row-cell:odd{" +
                        "-fx-background-color:" + ParkingApp.BG_RAISED + ";}" +

                        // selected row
                        ".table-row-cell:selected{" +
                        "-fx-background-color:" + ParkingApp.ACCENT + "28;}" +

                        // cells
                        ".table-cell{" +
                        "-fx-text-fill:" + ParkingApp.TEXT_H + ";" +
                        "-fx-font-size:13px;" +
                        "-fx-padding:0 14 0 14;" +
                        "-fx-alignment:CENTER-LEFT;" +
                        "-fx-background-color:transparent;}" +

                        // selected cell text
                        ".table-row-cell:selected .table-cell{" +
                        "-fx-text-fill:" + ParkingApp.TEXT_H + ";}" +

                        // sort arrow
                        ".table-view .arrow{" +
                        "-fx-background-color:" + ParkingApp.ACCENT + ";}" +

                        // scroll bars
                        ".table-view .scroll-bar:vertical .thumb," +
                        ".table-view .scroll-bar:horizontal .thumb{" +
                        "-fx-background-color:" + ParkingApp.BORDER_LIT + ";" +
                        "-fx-background-radius:4;}" +

                        ".table-view .scroll-bar .track{" +
                        "-fx-background-color:" + ParkingApp.BG_SURFACE + ";}" +

                        // remove focus ring
                        ".table-view:focused{-fx-border-color:" + ParkingApp.BORDER + ";}";

        table.getStylesheets().clear();
        table.getStylesheets().add("data:text/css," +
                css.replace(" ", "%20")
                        .replace("{", "%7B")
                        .replace("}", "%7D")
                        .replace(":", "%3A")
                        .replace(";", "%3B")
                        .replace(",", "%2C")
                        .replace("#", "%23")
                        .replace("(", "%28")
                        .replace(")", "%29")
                        .replace("'", "%27")
                        .replace(".", "%2E")
                        .replace(">", "%3E"));
    }

    // ── Hover effect via row factory ──────────────────────────────────────

    private static <T> void addHoverEffect(TableView<T> table) {
        table.setRowFactory(tv -> {
            TableRow<T> row = new TableRow<>();
            row.setOnMouseEntered(e -> {
                if (!row.isSelected() && !row.isEmpty()) {
                    row.setStyle("-fx-background-color: " + ParkingApp.ACCENT + "14;");
                }
            });
            row.setOnMouseExited(e -> {
                if (!row.isSelected()) {
                    row.setStyle("");
                }
            });
            row.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                if (isSelected) {
                    row.setStyle("-fx-background-color: " + ParkingApp.ACCENT + "28;");
                } else {
                    row.setStyle("");
                }
            });
            return row;
        });
    }

    // ── Shared cell/header helpers ────────────────────────────────────────

    /**
     * Styled column header graphic with accent underline and bold font.
     */
    public static Label styledHeader(String text) {
        Label l = new Label(text.toUpperCase());
        l.setFont(Font.font("System", FontWeight.BOLD, 11));
        l.setTextFill(Color.web(ParkingApp.TEXT_M));
        l.setPadding(new Insets(12, 14, 12, 14));
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    /**
     * Colored pill badge label.
     */
    public static Label makeBadge(String text, String hexColor) {
        Label l = new Label(text);
        l.setFont(Font.font("System", FontWeight.BOLD, 11));
        l.setTextFill(Color.web(hexColor));
        l.setPadding(new Insets(3, 10, 3, 10));
        l.setStyle(
                "-fx-background-color: " + hexColor + "22;" +
                        "-fx-background-radius: 20;" +
                        "-fx-border-color: "     + hexColor + "55;" +
                        "-fx-border-radius: 20;" +
                        "-fx-border-width: 1;");
        return l;
    }

    private static <T> TableCell<T, String> plainCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setFont(Font.font("System", 13));
                setTextFill(Color.web(ParkingApp.TEXT_H));
                setPadding(new Insets(0, 14, 0, 14));
            }
        };
    }

    // ── Height helpers ────────────────────────────────────────────────────

    /**
     * Sets the preferred height to show exactly {@code rows} data rows
     * plus the header. Use this instead of setPrefHeight magic numbers.
     */
    public static <T> void setVisibleRows(TableView<T> table, int rows) {
        table.setFixedCellSize(46);
        table.prefHeightProperty().bind(
                table.fixedCellSizeProperty().multiply(rows).add(36));
    }
}