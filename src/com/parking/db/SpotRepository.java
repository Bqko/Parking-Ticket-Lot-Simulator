package com.parking.db;

import com.parking.enums.SpotType;
import com.parking.model.ParkingSpot;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles database persistence for the parking_spots table.
 *
 * <p>ParkingLot manages spots in memory; SpotRepository keeps the
 * database in sync so spots survive restarts.</p>
 */
public class SpotRepository {

    private final Connection conn;

    public SpotRepository() {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

    // ── Insert ────────────────────────────────────────────────────────────

    /**
     * Persists a newly added spot to the database.
     *
     * @throws IllegalArgumentException if a spot with the same code already exists.
     */
    public void insertSpot(ParkingSpot spot) {
        String sql = """
            INSERT INTO parking_spots (spot_code, floor_number, spot_type, is_occupied)
            VALUES (?, ?, ?, 0)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, spot.getSpotId());
            ps.setInt(2, spot.getFloor());
            ps.setString(3, spot.getSpotType().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                throw new IllegalArgumentException(
                        "Spot '" + spot.getSpotId() + "' already exists in the database.");
            }
            throw new RuntimeException("Failed to insert spot: " + e.getMessage(), e);
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────

    /**
     * Removes a spot from the database by its spot code.
     */
    public void deleteSpot(String spotCode) {
        String sql = "DELETE FROM parking_spots WHERE spot_code = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, spotCode);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete spot: " + e.getMessage(), e);
        }
    }

    // ── Load ──────────────────────────────────────────────────────────────

    /**
     * Loads all spots from the database. Used on app startup to restore
     * the lot to its persisted state.
     */
    public List<ParkingSpot> loadAll() {
        List<ParkingSpot> list = new ArrayList<>();
        String sql = "SELECT spot_code, floor_number, spot_type, is_occupied FROM parking_spots ORDER BY spot_code";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                SpotType type = SpotType.valueOf(rs.getString("spot_type"));
                ParkingSpot spot = new ParkingSpot(
                        rs.getString("spot_code"),
                        rs.getInt("floor_number"),
                        type
                );
                list.add(spot);
            }
        } catch (SQLException e) {
            System.err.println("DB loadAll spots error: " + e.getMessage());
        }
        return list;
    }

    // ── Count ─────────────────────────────────────────────────────────────

    public int totalSpots() {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM parking_spots")) {
            return rs.getInt(1);
        } catch (SQLException e) {
            return 0;
        }
    }
}