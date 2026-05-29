package com.parking.db;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Handles database operations for Admin Panel accounts.
 */
public class AdminRepository {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Connection conn;

    public AdminRepository() {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

    /**
     * Verifies a username and password.
     *
     * @return the matching admin record, or null when authentication fails.
     */
    public AdminRecord authenticate(String username, String password) {
        if (username == null || password == null) return null;

        String normalizedUsername = username.trim().toLowerCase();
        String sql = """
            SELECT *
            FROM admins
            WHERE username = ? AND password_hash = ? AND is_active = 1
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizedUsername);
            ps.setString(2, hashPassword(password));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                recordLogin(normalizedUsername);
                return new AdminRecord(
                        rs.getInt("admin_id"),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getString("role"),
                        rs.getString("last_login")
                );
            }
        } catch (SQLException e) {
            System.err.println("DB authenticate error: " + e.getMessage());
        }
        return null;
    }

    private void recordLogin(String username) {
        String sql = "UPDATE admins SET last_login = ? WHERE username = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, LocalDateTime.now().format(FMT));
            ps.setString(2, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("DB recordLogin error: " + e.getMessage());
        }
    }

    public static String hashPassword(String plainText) {
        try {
            java.security.MessageDigest md =
                    java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(
                    plainText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static class AdminRecord {
        public final int adminId;
        public final String username;
        public final String displayName;
        public final String role;
        public final String lastLogin;

        public AdminRecord(int adminId, String username, String displayName,
                           String role, String lastLogin) {
            this.adminId = adminId;
            this.username = username;
            this.displayName = displayName != null ? displayName : username;
            this.role = role != null ? role : "ADMIN";
            this.lastLogin = lastLogin != null ? lastLogin : "Never";
        }
    }
}
