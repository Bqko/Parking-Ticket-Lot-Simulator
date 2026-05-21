package com.parking.db;

import java.io.File;
import java.sql.*;

/**
 * Singleton database connection manager.
 * Keeps one shared SQLite connection for the app's lifetime.
 * The database file is stored next to the jar (plts.sqlite).
 */
public class DatabaseManager {

    private static final String DB_FILE = "plts.sqlite";
    private static volatile DatabaseManager instance;
    private Connection connection;

    private DatabaseManager() {
        try {
            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");

            // Use a file next to where the app runs
            String url = "jdbc:sqlite:" + DB_FILE;
            connection = DriverManager.getConnection(url);
            connection.setAutoCommit(true);

            // Enable WAL mode for better concurrent reads
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL;");
                st.execute("PRAGMA foreign_keys=ON;");
            }

            ensureSchema();
            seedVehicleTypes();
            seedSpotTypes();

            System.out.println("✅ Database connected: " + new File(DB_FILE).getAbsolutePath());

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize database: " + e.getMessage(), e);
        }
    }

    public static DatabaseManager getInstance() {
        if (instance == null) {
            synchronized (DatabaseManager.class) {
                if (instance == null) instance = new DatabaseManager();
            }
        }
        return instance;
    }

    public Connection getConnection() { return connection; }

    // ── Schema creation (safe — only creates if not exists) ───────────────

    private void ensureSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {

            st.execute("""
                CREATE TABLE IF NOT EXISTS `vehicles` (
                  `vehicle_type_id` INTEGER PRIMARY KEY AUTOINCREMENT,
                  `rate_multiplier` REAL NOT NULL,
                  `display_name`    TEXT NOT NULL
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS `spots` (
                  `spot_type_id`     INTEGER PRIMARY KEY AUTOINCREMENT,
                  `name`             TEXT NOT NULL,
                  `total_capacity`   INTEGER NOT NULL,
                  `current_occupied` INTEGER NOT NULL DEFAULT 0,
                  `available_number` INTEGER NOT NULL
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS `parking_spot` (
                  `spot_id`      INTEGER PRIMARY KEY AUTOINCREMENT,
                  `spot_code`    TEXT    NOT NULL UNIQUE,
                  `floor_number` INTEGER NOT NULL,
                  `spot_type_id` INTEGER NOT NULL,
                  `is_occupied`  INTEGER NOT NULL DEFAULT 0,
                  FOREIGN KEY (`spot_type_id`) REFERENCES `spots`(`spot_type_id`)
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS `customer` (
                  `customer_id`     INTEGER PRIMARY KEY AUTOINCREMENT,
                  `full_name`       TEXT NOT NULL DEFAULT '',
                  `phone`           TEXT NOT NULL DEFAULT '',
                  `license_plate`   TEXT NOT NULL,
                  `vehicle_type_id` INTEGER NOT NULL,
                  `created_at`      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  FOREIGN KEY (`vehicle_type_id`) REFERENCES `vehicles`(`vehicle_type_id`)
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS `ticket` (
                  `ticket_id`     INTEGER PRIMARY KEY AUTOINCREMENT,
                  `ticket_code`   TEXT    NOT NULL UNIQUE,
                  `customer_id`   INTEGER NOT NULL,
                  `spot_id`       INTEGER NOT NULL,
                  `ticket_status` TEXT    NOT NULL DEFAULT 'ACTIVE',
                  `issued_at`     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  `exit_time`     TIMESTAMP,
                  `fee_charged`   REAL NOT NULL DEFAULT 0,
                  `amount_paid`   REAL NOT NULL DEFAULT 0,
                  `change`        REAL NOT NULL DEFAULT 0,
                  FOREIGN KEY (`customer_id`) REFERENCES `customer`(`customer_id`),
                  FOREIGN KEY (`spot_id`)     REFERENCES `parking_spot`(`spot_id`)
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS `payment` (
                  `payment_id`      INTEGER PRIMARY KEY AUTOINCREMENT,
                  `ticket_id`       INTEGER NOT NULL,
                  `amount`          REAL NOT NULL,
                  `fee_total`       REAL NOT NULL,
                  `change_returned` REAL NOT NULL,
                  `is_paid`         INTEGER NOT NULL DEFAULT 0,
                  `paid_at`         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  FOREIGN KEY (`ticket_id`) REFERENCES `ticket`(`ticket_id`)
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS `fee_config` (
                  `config_id`            INTEGER PRIMARY KEY AUTOINCREMENT,
                  `base_rate_per_hour`   REAL NOT NULL,
                  `daily_max_rate`       REAL NOT NULL,
                  `lost_ticket_fee`      REAL NOT NULL,
                  `grace_period_minutes` INTEGER NOT NULL,
                  `discount_percent`     REAL NOT NULL,
                  `effective_from`       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");
        }
    }

    // ── Seed reference data (only inserts if tables are empty) ────────────

    private void seedVehicleTypes() throws SQLException {
        try (Statement st = connection.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM vehicles");
            if (rs.next() && rs.getInt(1) == 0) {
                st.execute("INSERT INTO vehicles (rate_multiplier, display_name) VALUES (0.5, 'Motorcycle')");
                st.execute("INSERT INTO vehicles (rate_multiplier, display_name) VALUES (1.0, 'Car')");
                st.execute("INSERT INTO vehicles (rate_multiplier, display_name) VALUES (2.0, 'Truck')");
                System.out.println("🌱 Seeded vehicle types.");
            }
        }
    }

    private void seedSpotTypes() throws SQLException {
        try (Statement st = connection.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM spots");
            if (rs.next() && rs.getInt(1) == 0) {
                st.execute("INSERT INTO spots (name, total_capacity, current_occupied, available_number) VALUES ('MOTORCYCLE', 15, 0, 15)");
                st.execute("INSERT INTO spots (name, total_capacity, current_occupied, available_number) VALUES ('COMPACT',    30, 0, 30)");
                st.execute("INSERT INTO spots (name, total_capacity, current_occupied, available_number) VALUES ('STANDARD',   60, 0, 60)");
                st.execute("INSERT INTO spots (name, total_capacity, current_occupied, available_number) VALUES ('LARGE',      15, 0, 15)");
                System.out.println("🌱 Seeded spot types.");
            }
        }
    }

    // ── Helper: close quietly ─────────────────────────────────────────────

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Database connection closed.");
            }
        } catch (SQLException e) {
            System.err.println("Error closing DB: " + e.getMessage());
        }
    }
}
