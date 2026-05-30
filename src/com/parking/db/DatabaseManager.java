package com.parking.db;

import java.sql.*;

/**
 * Manages the SQLite database connection and schema.
 *
 * <p>Implements the Singleton pattern — only one connection is ever
 * open at a time. The database file {@code parking.db} is created
 * automatically in the project root on first run.</p>
 *
 * <h3>Setup</h3>
 * Add sqlite-jdbc JAR to your project libraries:
 * File → Project Structure → Libraries → + → Java → sqlite-jdbc-3.45.x.jar
 *
 * <h3>Default admin credentials</h3>
 * <pre>
 *   Username : admin
 *   Password : admin123
 * </pre>
 *
 * <h3>Usage</h3>
 * <pre>
 *   Connection conn = DatabaseManager.getInstance().getConnection();
 * </pre>
 */
public class DatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:parking.db";
    private static DatabaseManager instance;
    private Connection connection;

    // ── Singleton ─────────────────────────────────────────────────────────

    private DatabaseManager() {
        connectTo(DB_URL);
        createTables();
        seedData();
    }

    private DatabaseManager(String url) {
        connectTo(url);
        createTables();
        seedData();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    /**
     * Switches to a fresh in-memory SQLite database.
     * Call this at the start of every test class BEFORE getInstance().
     * In-memory DB is isolated — it never touches parking.db.
     */
    public static synchronized void useInMemoryDatabase() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
        instance = new DatabaseManager("jdbc:sqlite::memory:");
    }

    // ── Connection ────────────────────────────────────────────────────────

    private void connectTo(String url) {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(url);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL;");
                st.execute("PRAGMA foreign_keys=ON;");
            }
            System.out.println("✅ Connected to SQLite: " + url);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite driver JAR not found. Add sqlite-jdbc JAR to your libraries.", e);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to connect to database: " + e.getMessage(), e);
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) connectTo(DB_URL);
        } catch (SQLException e) {
            connectTo(DB_URL);
        }
        return connection;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("🔒 Database connection closed.");
            }
        } catch (SQLException e) {
            System.err.println("Warning: could not close DB connection: " + e.getMessage());
        }
    }

    // ── Schema creation ───────────────────────────────────────────────────

    private void createTables() {
        try (Statement st = connection.createStatement()) {

            // vehicle_types — maps to VehicleType enum
            st.execute("""
                CREATE TABLE IF NOT EXISTS vehicle_types (
                    vehicle_type_id   INTEGER PRIMARY KEY AUTOINCREMENT,
                    name              TEXT    NOT NULL UNIQUE,
                    display_name      TEXT    NOT NULL,
                    rate_multiplier   REAL    NOT NULL DEFAULT 1.0
                );
            """);

            // customers — name, phone, license plate
            st.execute("""
                CREATE TABLE IF NOT EXISTS customers (
                    customer_id    INTEGER PRIMARY KEY AUTOINCREMENT,
                    full_name      TEXT    NOT NULL DEFAULT 'Guest',
                    phone          TEXT    NOT NULL DEFAULT '',
                    license_plate  TEXT    NOT NULL UNIQUE,
                    vehicle_type   TEXT    NOT NULL,
                    created_at     TEXT    NOT NULL DEFAULT (datetime('now'))
                );
            """);

            // spot_types — maps to SpotType enum
            st.execute("""
                CREATE TABLE IF NOT EXISTS spot_types (
                    spot_type_id      INTEGER PRIMARY KEY AUTOINCREMENT,
                    name              TEXT    NOT NULL UNIQUE,
                    total_capacity    INTEGER NOT NULL DEFAULT 0,
                    current_occupied  INTEGER NOT NULL DEFAULT 0
                );
            """);

            // parking_spots — individual spots
            st.execute("""
                CREATE TABLE IF NOT EXISTS parking_spots (
                    spot_id       INTEGER PRIMARY KEY AUTOINCREMENT,
                    spot_code     TEXT    NOT NULL UNIQUE,
                    floor_number  INTEGER NOT NULL DEFAULT 0,
                    spot_type     TEXT    NOT NULL,
                    is_occupied   INTEGER NOT NULL DEFAULT 0
                );
            """);

            // tickets — core ticket lifecycle
            st.execute("""
                CREATE TABLE IF NOT EXISTS tickets (
                    ticket_id      INTEGER PRIMARY KEY AUTOINCREMENT,
                    ticket_code    TEXT    NOT NULL UNIQUE,
                    license_plate  TEXT    NOT NULL,
                    vehicle_type   TEXT    NOT NULL,
                    spot_code      TEXT    NOT NULL,
                    floor_number   INTEGER NOT NULL DEFAULT 0,
                    ticket_status  TEXT    NOT NULL DEFAULT 'ACTIVE',
                    issued_at      TEXT    NOT NULL,
                    exit_time      TEXT,
                    fee_charged    REAL    NOT NULL DEFAULT 0.0,
                    amount_paid    REAL    NOT NULL DEFAULT 0.0,
                    change_given   REAL    NOT NULL DEFAULT 0.0
                );
            """);

            // payments — payment record per ticket
            st.execute("""
                CREATE TABLE IF NOT EXISTS payments (
                    payment_id       INTEGER PRIMARY KEY AUTOINCREMENT,
                    ticket_code      TEXT    NOT NULL,
                    amount           REAL    NOT NULL,
                    fee_total        REAL    NOT NULL,
                    change_returned  REAL    NOT NULL,
                    is_paid          INTEGER NOT NULL DEFAULT 0,
                    paid_at          TEXT    NOT NULL DEFAULT (datetime('now'))
                );
            """);

            // fee_config — pricing rules (one active row)
            st.execute("""
                CREATE TABLE IF NOT EXISTS fee_config (
                    config_id             INTEGER PRIMARY KEY AUTOINCREMENT,
                    base_rate_per_hour    REAL    NOT NULL DEFAULT 20.0,
                    daily_max_rate        REAL    NOT NULL DEFAULT 200.0,
                    lost_ticket_fee       REAL    NOT NULL DEFAULT 150.0,
                    grace_period_minutes  INTEGER NOT NULL DEFAULT 15,
                    discount_percent      REAL    NOT NULL DEFAULT 0.0,
                    effective_from        TEXT    NOT NULL DEFAULT (datetime('now'))
                );
            """);

            // session_log — daily revenue summary
            st.execute("""
                CREATE TABLE IF NOT EXISTS session_log (
                    log_id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    log_date       TEXT    NOT NULL DEFAULT (date('now')),
                    total_tickets  INTEGER NOT NULL DEFAULT 0,
                    total_revenue  REAL    NOT NULL DEFAULT 0.0,
                    created_at     TEXT    NOT NULL DEFAULT (datetime('now'))
                );
            """);

            // admins — operator accounts for the Admin Panel
            // Passwords stored as SHA-256 hex hashes, never plain text.
            st.execute("""
                CREATE TABLE IF NOT EXISTS admins (
                    admin_id      INTEGER PRIMARY KEY AUTOINCREMENT,
                    username      TEXT    NOT NULL UNIQUE,
                    password_hash TEXT    NOT NULL,
                    display_name  TEXT    NOT NULL DEFAULT 'Administrator',
                    role          TEXT    NOT NULL DEFAULT 'ADMIN',
                    is_active     INTEGER NOT NULL DEFAULT 1,
                    last_login    TEXT,
                    created_at    TEXT    NOT NULL DEFAULT (datetime('now'))
                );
            """);

            System.out.println("✅ Database schema ready.");

        } catch (SQLException e) {
            throw new RuntimeException("Failed to create tables: " + e.getMessage(), e);
        }
    }

    // ── Seed data ─────────────────────────────────────────────────────────

    private void seedData() {
        try (Statement st = connection.createStatement()) {

            // Seed vehicle types if empty
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM vehicle_types");
            if (rs.getInt(1) == 0) {
                st.execute("""
                    INSERT INTO vehicle_types (name, display_name, rate_multiplier) VALUES
                    ('MOTORCYCLE', 'Motorcycle', 0.5),
                    ('CAR',        'Car',        1.0),
                    ('TRUCK',      'Truck',      2.0);
                """);
            }

            // Seed spot types if empty
            rs = st.executeQuery("SELECT COUNT(*) FROM spot_types");
            if (rs.getInt(1) == 0) {
                st.execute("""
                    INSERT INTO spot_types (name, total_capacity, current_occupied) VALUES
                    ('MOTORCYCLE', 15,  0),
                    ('COMPACT',    30,  0),
                    ('STANDARD',   60,  0),
                    ('LARGE',      15,  0);
                """);
            }

            // Seed default fee config if empty
            rs = st.executeQuery("SELECT COUNT(*) FROM fee_config");
            if (rs.getInt(1) == 0) {
                st.execute("""
                    INSERT INTO fee_config
                        (base_rate_per_hour, daily_max_rate, lost_ticket_fee,
                         grace_period_minutes, discount_percent)
                    VALUES (20.0, 200.0, 150.0, 15, 0.0);
                """);
            }

            // Seed parking spots to mirror ParkingLot.initializeSpots()
            // Layout per floor: 5 MOTORCYCLE, 10 COMPACT, 20 STANDARD, 5 LARGE
            // 3 floors → 120 spots total
            rs = st.executeQuery("SELECT COUNT(*) FROM parking_spots");
            if (rs.getInt(1) == 0) {
                String insertSpot =
                        "INSERT INTO parking_spots (spot_code, floor_number, spot_type, is_occupied) " +
                                "VALUES (?, ?, ?, 0)";

                String[][] spotLayout = {
                        { "M",  "5",  "MOTORCYCLE" },
                        { "C", "10",  "COMPACT"    },
                        { "S", "20",  "STANDARD"   },
                        { "L",  "5",  "LARGE"      }
                };

                try (PreparedStatement ps = connection.prepareStatement(insertSpot)) {
                    for (int floor = 0; floor < 3; floor++) {
                        for (String[] entry : spotLayout) {
                            String prefix   = entry[0];
                            int    count    = Integer.parseInt(entry[1]);
                            String spotType = entry[2];
                            for (int i = 1; i <= count; i++) {
                                String spotCode = String.format("F%d-%s%02d", floor, prefix, i);
                                ps.setString(1, spotCode);
                                ps.setInt(2, floor);
                                ps.setString(3, spotType);
                                ps.addBatch();
                            }
                        }
                    }
                    ps.executeBatch();
                    System.out.println("✅ Parking spots seeded (120 spots across 3 floors).");
                }
            }

            // Seed default admin account if table is empty
            // Default credentials: username=admin / password=admin123
            rs = st.executeQuery("SELECT COUNT(*) FROM admins");
            if (rs.getInt(1) == 0) {
                String defaultHash = AdminRepository.hashPassword("admin123");
                String insertAdmin =
                        "INSERT INTO admins (username, password_hash, display_name, role) " +
                                "VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = connection.prepareStatement(insertAdmin)) {
                    ps.setString(1, "admin");
                    ps.setString(2, defaultHash);
                    ps.setString(3, "Administrator");
                    ps.setString(4, "ADMIN");
                    ps.executeUpdate();
                    System.out.println("✅ Default admin account seeded (username: admin / password: admin123).");
                }
            }

        } catch (SQLException e) {
            System.err.println("Warning: seed data error: " + e.getMessage());
        }
    }
}