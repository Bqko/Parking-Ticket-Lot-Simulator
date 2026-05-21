package com.parking.db;

import com.parking.enums.TicketStatus;
import com.parking.enums.VehicleType;
import com.parking.model.*;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Handles all database operations for tickets, customers, payments, and spots.
 * Called by TicketManager to persist every state change.
 */
public class TicketRepository {

    private final Connection conn;

    public TicketRepository() {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

    // ── Customer ──────────────────────────────────────────────────────────

    /**
     * Inserts or returns existing customer by license plate.
     * Returns the customer_id.
     */
    public int upsertCustomer(Vehicle vehicle) throws SQLException {
        // Check if already exists
        String findSql = "SELECT customer_id FROM customer WHERE license_plate = ?";
        try (PreparedStatement ps = conn.prepareStatement(findSql)) {
            ps.setString(1, vehicle.getLicensePlate());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("customer_id");
        }

        // Insert new customer
        String insertSql = """
            INSERT INTO customer (full_name, phone, license_plate, vehicle_type_id)
            VALUES ('', '', ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, vehicle.getLicensePlate());
            ps.setInt(2, vehicleTypeToId(vehicle.getType()));
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        }
        throw new SQLException("Failed to insert customer for plate: " + vehicle.getLicensePlate());
    }

    // ── Parking Spot ──────────────────────────────────────────────────────

    /**
     * Inserts the spot if it doesn't exist, then marks it as occupied.
     * Returns the DB spot_id (rowid).
     */
    public int occupySpot(ParkingSpot spot) throws SQLException {
        // Ensure the spot row exists
        String findSql = "SELECT spot_id FROM parking_spot WHERE spot_code = ?";
        try (PreparedStatement ps = conn.prepareStatement(findSql)) {
            ps.setString(1, spot.getSpotId());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int id = rs.getInt("spot_id");
                updateSpotOccupied(id, true);
                return id;
            }
        }

        // Insert new spot row
        String insertSql = """
            INSERT INTO parking_spot (spot_code, floor_number, spot_type_id, is_occupied)
            VALUES (?, ?, ?, 1)
            """;
        try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, spot.getSpotId());
            ps.setInt(2, spot.getFloor());
            ps.setInt(3, spotTypeToId(spot.getSpotType().name()));
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                int id = keys.getInt(1);
                updateSpotsCapacityTable(spot.getSpotType().name(), true);
                return id;
            }
        }
        throw new SQLException("Failed to insert parking spot: " + spot.getSpotId());
    }

    /** Marks a spot as free in the DB. */
    public void releaseSpot(ParkingSpot spot) throws SQLException {
        String findSql = "SELECT spot_id FROM parking_spot WHERE spot_code = ?";
        try (PreparedStatement ps = conn.prepareStatement(findSql)) {
            ps.setString(1, spot.getSpotId());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int id = rs.getInt("spot_id");
                updateSpotOccupied(id, false);
                updateSpotsCapacityTable(spot.getSpotType().name(), false);
            }
        }
    }

    private void updateSpotOccupied(int spotDbId, boolean occupied) throws SQLException {
        String sql = "UPDATE parking_spot SET is_occupied = ? WHERE spot_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, occupied ? 1 : 0);
            ps.setInt(2, spotDbId);
            ps.executeUpdate();
        }
    }

    private void updateSpotsCapacityTable(String spotTypeName, boolean occupying) throws SQLException {
        String sql = occupying
                ? "UPDATE spots SET current_occupied = current_occupied + 1, available_number = available_number - 1 WHERE name = ?"
                : "UPDATE spots SET current_occupied = current_occupied - 1, available_number = available_number + 1 WHERE name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, spotTypeName);
            ps.executeUpdate();
        }
    }

    // ── Ticket ────────────────────────────────────────────────────────────

    /**
     * Inserts a new ticket row and returns the generated ticket DB id.
     */
    public int insertTicket(Ticket ticket, int customerDbId, int spotDbId) throws SQLException {
        String sql = """
            INSERT INTO ticket (ticket_code, customer_id, spot_id, ticket_status, issued_at,
                                fee_charged, amount_paid, change)
            VALUES (?, ?, ?, 'ACTIVE', ?, 0, 0, 0)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, ticket.getTicketId());
            ps.setInt(2, customerDbId);
            ps.setInt(3, spotDbId);
            ps.setString(4, ticket.getIssuedAt().toString());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        }
        throw new SQLException("Failed to insert ticket: " + ticket.getTicketId());
    }

    /**
     * Updates ticket status, fee, amount paid, change, and exit time after payment.
     */
    public void updateTicketOnPayment(Ticket ticket) throws SQLException {
        String sql = """
            UPDATE ticket SET
                ticket_status = ?,
                fee_charged   = ?,
                amount_paid   = ?,
                change        = ?
            WHERE ticket_code = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ticket.getStatus().name());
            ps.setDouble(2, ticket.getFeeCharged());
            ps.setDouble(3, ticket.getAmountPaid());
            ps.setDouble(4, ticket.getChange());
            ps.setString(5, ticket.getTicketId());
            ps.executeUpdate();
        }
    }

    /**
     * Updates ticket status to EXITED and sets exit_time.
     */
    public void updateTicketOnExit(Ticket ticket) throws SQLException {
        String sql = """
            UPDATE ticket SET
                ticket_status = 'EXITED',
                exit_time     = ?
            WHERE ticket_code = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ticket.getExitTime() != null
                    ? ticket.getExitTime().toString()
                    : LocalDateTime.now().toString());
            ps.setString(2, ticket.getTicketId());
            ps.executeUpdate();
        }
    }

    /**
     * Marks ticket as LOST in the DB.
     */
    public void updateTicketLost(String ticketCode) throws SQLException {
        String sql = "UPDATE ticket SET ticket_status = 'LOST' WHERE ticket_code = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ticketCode);
            ps.executeUpdate();
        }
    }

    // ── Payment ───────────────────────────────────────────────────────────

    /**
     * Inserts a payment record linked to the ticket's DB id.
     */
    public void insertPayment(Ticket ticket) throws SQLException {
        // Look up DB ticket_id from ticket_code
        int dbTicketId = getDbTicketId(ticket.getTicketId());
        if (dbTicketId == -1) return;

        String sql = """
            INSERT INTO payment (ticket_id, amount, fee_total, change_returned, is_paid)
            VALUES (?, ?, ?, ?, 1)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, dbTicketId);
            ps.setDouble(2, ticket.getAmountPaid());
            ps.setDouble(3, ticket.getFeeCharged());
            ps.setDouble(4, ticket.getChange());
            ps.executeUpdate();
        }
    }

    private int getDbTicketId(String ticketCode) throws SQLException {
        String sql = "SELECT ticket_id FROM ticket WHERE ticket_code = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ticketCode);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("ticket_id");
        }
        return -1;
    }

    // ── Fee Config ────────────────────────────────────────────────────────

    /**
     * Saves the current fee configuration as a new row (keeps history).
     */
    public void saveFeeConfig(double baseRate, double dailyMax, double lostFee,
                              int gracePeriod, double discount) throws SQLException {
        String sql = """
            INSERT INTO fee_config
                (base_rate_per_hour, daily_max_rate, lost_ticket_fee, grace_period_minutes, discount_percent)
            VALUES (?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, baseRate);
            ps.setDouble(2, dailyMax);
            ps.setDouble(3, lostFee);
            ps.setInt(4, gracePeriod);
            ps.setDouble(5, discount);
            ps.executeUpdate();
            System.out.println("💾 Fee config saved to DB.");
        }
    }

    /**
     * Loads the most recently saved fee config. Returns null if none exists.
     */
    public double[] loadLatestFeeConfig() throws SQLException {
        String sql = """
            SELECT base_rate_per_hour, daily_max_rate, lost_ticket_fee,
                   grace_period_minutes, discount_percent
            FROM fee_config
            ORDER BY effective_from DESC
            LIMIT 1
            """;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                return new double[]{
                        rs.getDouble("base_rate_per_hour"),
                        rs.getDouble("daily_max_rate"),
                        rs.getDouble("lost_ticket_fee"),
                        rs.getDouble("grace_period_minutes"),
                        rs.getDouble("discount_percent")
                };
            }
        }
        return null; // no config saved yet
    }

    // ── Session history (for dashboard / CSV export) ──────────────────────

    /**
     * Returns total revenue from all EXITED tickets in the DB.
     */
    public double loadTotalRevenue() throws SQLException {
        String sql = "SELECT COALESCE(SUM(fee_charged), 0) FROM ticket WHERE ticket_status = 'EXITED'";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) return rs.getDouble(1);
        }
        return 0.0;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private int vehicleTypeToId(VehicleType type) {
        return switch (type) {
            case MOTORCYCLE -> 1;
            case CAR        -> 2;
            case TRUCK      -> 3;
        };
    }

    private int spotTypeToId(String spotTypeName) throws SQLException {
        String sql = "SELECT spot_type_id FROM spots WHERE name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, spotTypeName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("spot_type_id");
        }
        return 1; // default fallback
    }
}
