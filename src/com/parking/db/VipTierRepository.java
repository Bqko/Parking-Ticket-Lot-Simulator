package com.parking.db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles database operations for the vip_tiers table.
 *
 * <p>VIP tiers are named discount rules (e.g. "Staff 20%", "Monthly Pass 40%")
 * that an operator can select during payment to override the global discount.</p>
 */
public class VipTierRepository {

    private final Connection conn;

    public VipTierRepository() {
        this.conn = DatabaseManager.getInstance().getConnection();
        ensureTable();
    }

    // ── Schema ────────────────────────────────────────────────────────────

    private void ensureTable() {
        String ddl = """
            CREATE TABLE IF NOT EXISTS vip_tiers (
                tier_id          INTEGER PRIMARY KEY AUTOINCREMENT,
                tier_name        TEXT    NOT NULL UNIQUE,
                discount_percent REAL    NOT NULL DEFAULT 0.0,
                description      TEXT    NOT NULL DEFAULT '',
                is_active        INTEGER NOT NULL DEFAULT 1,
                created_at       TEXT    NOT NULL DEFAULT (datetime('now'))
            );
        """;
        try (Statement st = conn.createStatement()) {
            st.execute(ddl);
        } catch (SQLException e) {
            System.err.println("VipTierRepository: table creation error: " + e.getMessage());
        }
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    public void insert(String tierName, double discountPercent, String description) {
        String sql = """
            INSERT INTO vip_tiers (tier_name, discount_percent, description)
            VALUES (?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tierName.trim());
            ps.setDouble(2, discountPercent);
            ps.setString(3, description.trim());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                throw new IllegalArgumentException("A tier named '" + tierName + "' already exists.");
            }
            throw new RuntimeException("Failed to insert VIP tier: " + e.getMessage(), e);
        }
    }

    public void update(int tierId, String tierName, double discountPercent, String description) {
        String sql = """
            UPDATE vip_tiers
            SET tier_name = ?, discount_percent = ?, description = ?
            WHERE tier_id = ?
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tierName.trim());
            ps.setDouble(2, discountPercent);
            ps.setString(3, description.trim());
            ps.setInt(4, tierId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update VIP tier: " + e.getMessage(), e);
        }
    }

    public void delete(int tierId) {
        String sql = "DELETE FROM vip_tiers WHERE tier_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tierId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete VIP tier: " + e.getMessage(), e);
        }
    }

    public void setActive(int tierId, boolean active) {
        String sql = "UPDATE vip_tiers SET is_active = ? WHERE tier_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, tierId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to toggle VIP tier: " + e.getMessage(), e);
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────

    public List<VipTierRecord> findAll() {
        List<VipTierRecord> list = new ArrayList<>();
        String sql = "SELECT * FROM vip_tiers ORDER BY discount_percent DESC";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new VipTierRecord(
                        rs.getInt("tier_id"),
                        rs.getString("tier_name"),
                        rs.getDouble("discount_percent"),
                        rs.getString("description"),
                        rs.getInt("is_active") == 1
                ));
            }
        } catch (SQLException e) {
            System.err.println("DB findAll VipTiers error: " + e.getMessage());
        }
        return list;
    }

    public List<VipTierRecord> findActive() {
        List<VipTierRecord> list = new ArrayList<>();
        String sql = "SELECT * FROM vip_tiers WHERE is_active = 1 ORDER BY discount_percent DESC";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new VipTierRecord(
                        rs.getInt("tier_id"),
                        rs.getString("tier_name"),
                        rs.getDouble("discount_percent"),
                        rs.getString("description"),
                        true
                ));
            }
        } catch (SQLException e) {
            System.err.println("DB findActive VipTiers error: " + e.getMessage());
        }
        return list;
    }

    // ── DTO ───────────────────────────────────────────────────────────────

    public static class VipTierRecord {
        public final int    tierId;
        public final String tierName;
        public final double discountPercent;
        public final String description;
        public final boolean isActive;

        public VipTierRecord(int id, String name, double pct, String desc, boolean active) {
            this.tierId          = id;
            this.tierName        = name;
            this.discountPercent = pct;
            this.description     = desc;
            this.isActive        = active;
        }

        @Override
        public String toString() { return tierName + " (" + discountPercent + "%)"; }
    }
}