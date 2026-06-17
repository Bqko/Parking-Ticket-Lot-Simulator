package com.parking.db;

import java.sql.*;

/**
 * Persists user preferences (theme, window state, etc.) in the
 * {@code user_preferences} table as simple key-value pairs.
 *
 * <h3>Usage</h3>
 * <pre>
 *   PreferencesRepository prefs = new PreferencesRepository();
 *   prefs.set("theme", "MIDNIGHT");
 *   String theme = prefs.get("theme", "DARK");   // "MIDNIGHT"
 * </pre>
 */
public class PreferencesRepository {

    // ── Known keys ────────────────────────────────────────────────────────
    public static final String KEY_THEME = "theme";

    private final Connection conn;

    public PreferencesRepository() {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

    // ── Write ─────────────────────────────────────────────────────────────

    /**
     * Inserts or updates a preference key.
     */
    public void set(String key, String value) {
        String sql = """
            INSERT INTO user_preferences (key, value)
            VALUES (?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value;
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("PreferencesRepository set error: " + e.getMessage());
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────

    /**
     * Returns the stored value for {@code key}, or {@code defaultValue}
     * if the key has never been set.
     */
    public String get(String key, String defaultValue) {
        String sql = "SELECT value FROM user_preferences WHERE key = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("value");
        } catch (SQLException e) {
            System.err.println("PreferencesRepository get error: " + e.getMessage());
        }
        return defaultValue;
    }
}