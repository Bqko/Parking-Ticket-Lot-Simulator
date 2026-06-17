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

            // user_preferences — persists UI settings across restarts
            st.execute("""
                CREATE TABLE IF NOT EXISTS user_preferences (
                    key    TEXT PRIMARY KEY,
                    value  TEXT NOT NULL
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

            // Seed dummy historical data on first run only (when tickets table is empty)
            rs = st.executeQuery("SELECT COUNT(*) FROM tickets");
            if (rs.getInt(1) == 0) {
                seedDummyData();
            }

        } catch (SQLException e) {
            System.err.println("Warning: seed data error: " + e.getMessage());
        }
    }

    // ── Dummy data (first-run only) ───────────────────────────────────────

    /**
     * Floods the database with 40–50 currently-parked dummy vehicles on the
     * very first run. This method is only called when the {@code tickets}
     * table is empty, so it never runs again after the application has been
     * used even once — even if every one of these dummy vehicles is still
     * sitting in its spot.
     *
     * <p>All generated tickets are inserted with {@code ACTIVE} status and
     * occupy a real, unique spot in {@code parking_spots}. They are never
     * given an exit time or marked paid here — they only become
     * {@code EXITED} if a real user pays and checks them out through the
     * normal application flow. Nothing in this method ever "exits" them.</p>
     */
    private void seedDummyData() {
        System.out.println("🎲 Seeding dummy parked vehicles (first run only)...");

        java.util.Random rng = new java.util.Random(42); // fixed seed → reproducible layout
        int ticketCount = 40 + rng.nextInt(11); // 40–50 inclusive

        // Pools of real spot codes, matching exactly what seedData() inserted
        // into parking_spots (3 floors × 5 motorcycle / 10 compact / 20 standard / 5 large).
        java.util.List<String> motoPool  = buildSpotCodePool("M", 5);
        java.util.List<String> largePool = buildSpotCodePool("L", 5);
        java.util.List<String> carPool   = new java.util.ArrayList<>();
        carPool.addAll(buildSpotCodePool("C", 10));
        carPool.addAll(buildSpotCodePool("S", 20));

        java.util.Collections.shuffle(motoPool,  rng);
        java.util.Collections.shuffle(largePool, rng);
        java.util.Collections.shuffle(carPool,   rng);

        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        String insertTicket = """
            INSERT OR IGNORE INTO tickets
                (ticket_code, license_plate, vehicle_type, spot_code,
                 floor_number, ticket_status, issued_at)
            VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?)
        """;

        String insertCustomer = """
            INSERT OR IGNORE INTO customers
                (license_plate, vehicle_type, full_name, phone)
            VALUES (?, ?, ?, ?)
        """;

        String occupySpot = "UPDATE parking_spots SET is_occupied = 1 WHERE spot_code = ?";

        java.time.format.DateTimeFormatter fmt =
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        com.parking.util.DummyDataGenerator gen =
                com.parking.util.DummyDataGenerator.withSeed(42);

        try (PreparedStatement psTicket   = connection.prepareStatement(insertTicket);
             PreparedStatement psCustomer = connection.prepareStatement(insertCustomer);
             PreparedStatement psSpot     = connection.prepareStatement(occupySpot)) {

            int seeded = 0;

            for (int i = 0; i < ticketCount; i++) {
                // Random vehicle type weighted: 60% car, 25% motorcycle, 15% truck
                com.parking.enums.VehicleType vType;
                int roll = rng.nextInt(100);
                if      (roll < 60) vType = com.parking.enums.VehicleType.CAR;
                else if (roll < 85) vType = com.parking.enums.VehicleType.MOTORCYCLE;
                else                vType = com.parking.enums.VehicleType.TRUCK;

                java.util.List<String> pool = switch (vType) {
                    case MOTORCYCLE -> motoPool;
                    case TRUCK      -> largePool;
                    default         -> carPool;
                };
                if (pool.isEmpty()) continue; // that spot type is full — skip rather than double-park

                // Pop a spot so no two dummy vehicles ever share one
                String spotCode = pool.remove(pool.size() - 1);
                int floor = Character.getNumericValue(spotCode.charAt(1)); // "F0-M01" → '0'

                String plate = gen.nextPlate(vType);
                String name  = gen.nextFullNameLatin();
                String phone = gen.nextPhone();

                // Parked sometime in the last 0–18 hours, so it reads as
                // currently parked rather than ancient history.
                int hoursAgo   = rng.nextInt(18);
                int minutesAgo = rng.nextInt(60);
                java.time.LocalDateTime entryTime = now.minusHours(hoursAgo).minusMinutes(minutesAgo);

                String ticketCode = String.format("TKT-%04d-%04d", i + 1, rng.nextInt(9999));
                String issuedStr  = entryTime.format(fmt);

                psCustomer.setString(1, plate);
                psCustomer.setString(2, vType.name());
                psCustomer.setString(3, name);
                psCustomer.setString(4, phone);
                psCustomer.addBatch();

                psTicket.setString(1, ticketCode);
                psTicket.setString(2, plate);
                psTicket.setString(3, vType.name());
                psTicket.setString(4, spotCode);
                psTicket.setInt(5,    floor);
                psTicket.setString(6, issuedStr);
                psTicket.addBatch();

                psSpot.setString(1, spotCode);
                psSpot.addBatch();

                seeded++;
            }

            psCustomer.executeBatch();
            psTicket.executeBatch();
            psSpot.executeBatch();

            System.out.println("✅ Dummy data seeded: " + seeded
                    + " vehicles currently parked across the lot (all ACTIVE — none will auto-exit).");

        } catch (SQLException e) {
            System.err.println("Warning: dummy data seeding error: " + e.getMessage());
        }
    }

    /**
     * Builds the list of real spot codes for one spot-type prefix across all
     * 3 floors, in the exact "F{floor}-{prefix}{NN}" format used when
     * {@code parking_spots} was originally seeded.
     */
    private java.util.List<String> buildSpotCodePool(String prefix, int perFloorCount) {
        java.util.List<String> pool = new java.util.ArrayList<>();
        for (int floor = 0; floor < 3; floor++) {
            for (int i = 1; i <= perFloorCount; i++) {
                pool.add(String.format("F%d-%s%02d", floor, prefix, i));
            }
        }
        return pool;
    }
}