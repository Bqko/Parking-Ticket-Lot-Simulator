package com.parking.db;

import com.parking.enums.SpotType;
import com.parking.enums.TicketStatus;
import com.parking.enums.VehicleType;
import com.parking.model.ParkingSpot;
import com.parking.model.Ticket;
import com.parking.model.Vehicle;
import com.parking.model.ParkingLot;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Handles all database operations for {@link Ticket} objects.
 *
 * <p>Follows the Repository pattern — the rest of the app never
 * writes SQL directly, it calls methods on this class.</p>
 */
public class TicketRepository {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Connection conn;

    public TicketRepository() {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

    // ── Insert ────────────────────────────────────────────────────────────

    /**
     * Inserts a newly issued ticket into the database.
     * Also upserts the customer record and marks the spot as occupied.
     */
    public void insert(Ticket ticket) {
        String sql = """
            INSERT INTO tickets
                (ticket_code, license_plate, vehicle_type, spot_code,
                 floor_number, ticket_status, issued_at,
                 fee_charged, amount_paid, change_given)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(ticket_code) DO NOTHING;
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ticket.getTicketId());
            ps.setString(2, ticket.getVehicle().getLicensePlate());
            ps.setString(3, ticket.getVehicle().getType().name());
            ps.setString(4, ticket.getSpot().getSpotId());
            ps.setInt(5,    ticket.getSpot().getFloor());
            ps.setString(6, ticket.getStatus().name());
            ps.setString(7, ticket.getIssuedAt().format(FMT));
            ps.setDouble(8, ticket.getFeeCharged());
            ps.setDouble(9, ticket.getAmountPaid());
            ps.setDouble(10, ticket.getChange());
            ps.executeUpdate();

            // Mark spot as occupied in DB
            updateSpotOccupied(ticket.getSpot().getSpotId(), true);

            // Upsert customer
            upsertCustomer(ticket.getVehicle());

        } catch (SQLException e) {
            System.err.println("DB insert ticket error: " + e.getMessage());
        }
    }

    // ── Update ────────────────────────────────────────────────────────────

    /**
     * Updates an existing ticket's status, fee, payment, and exit time.
     * Call this after pay() and closeOnExit().
     */
    public void update(Ticket ticket) {
        String sql = """
            UPDATE tickets
            SET ticket_status = ?,
                fee_charged   = ?,
                amount_paid   = ?,
                change_given  = ?,
                exit_time     = ?
            WHERE ticket_code = ?;
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ticket.getStatus().name());
            ps.setDouble(2, ticket.getFeeCharged());
            ps.setDouble(3, ticket.getAmountPaid());
            ps.setDouble(4, ticket.getChange());
            ps.setString(5, ticket.getExitTime() != null
                    ? ticket.getExitTime().format(FMT) : null);
            ps.setString(6, ticket.getTicketId());
            ps.executeUpdate();

            // If exited, free the spot
            if (ticket.getStatus() == TicketStatus.EXITED
                    || ticket.getStatus() == TicketStatus.LOST) {
                updateSpotOccupied(ticket.getSpot().getSpotId(), false);
            }

        } catch (SQLException e) {
            System.err.println("DB update ticket error: " + e.getMessage());
        }
    }

    // ── Insert payment record ─────────────────────────────────────────────

    /**
     * Inserts a payment record into the payments table.
     */
    public void insertPayment(Ticket ticket) {
        String sql = """
            INSERT INTO payments
                (ticket_code, amount, fee_total, change_returned, is_paid, paid_at)
            VALUES (?, ?, ?, ?, ?, datetime('now'));
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ticket.getTicketId());
            ps.setDouble(2, ticket.getAmountPaid());
            ps.setDouble(3, ticket.getFeeCharged());
            ps.setDouble(4, ticket.getChange());
            ps.setInt(5, ticket.getAmountPaid() >= ticket.getFeeCharged() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("DB insert payment error: " + e.getMessage());
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────

    /** Find a single ticket by its code. */
    public Optional<Ticket> findByCode(String ticketCode) {
        String sql = "SELECT * FROM tickets WHERE ticket_code = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ticketCode);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("DB findByCode error: " + e.getMessage());
        }
        return Optional.empty();
    }

    /** All tickets with ACTIVE status. */
    public List<Ticket> findActive() {
        return findByStatus("ACTIVE");
    }

    /** All tickets with EXITED status (session history). */
    public List<Ticket> findHistory() {
        return findByStatus("EXITED");
    }

    /** All tickets regardless of status. */
    public List<Ticket> findAll() {
        String sql = "SELECT * FROM tickets ORDER BY issued_at DESC";
        return queryList(sql);
    }

    /** All tickets for a given license plate, newest first. */
    public List<Ticket> findByPlate(String licensePlate) {
        String sql = """
            SELECT * FROM tickets
            WHERE license_plate = ?
            ORDER BY issued_at DESC
        """;
        List<Ticket> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, licensePlate.trim().toUpperCase());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) results.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("DB findByPlate error: " + e.getMessage());
        }
        return results;
    }

    /** Total revenue from all EXITED tickets. */
    public double totalRevenue() {
        String sql = "SELECT COALESCE(SUM(fee_charged), 0) FROM tickets WHERE ticket_status = 'EXITED'";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.getDouble(1);
        } catch (SQLException e) {
            System.err.println("DB totalRevenue error: " + e.getMessage());
            return 0.0;
        }
    }

    /** Count of tickets per status. */
    public int countByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM tickets WHERE ticket_status = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ResultSet rs = ps.executeQuery();
            return rs.getInt(1);
        } catch (SQLException e) {
            return 0;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private List<Ticket> findByStatus(String status) {
        String sql = "SELECT * FROM tickets WHERE ticket_status = ? ORDER BY issued_at DESC";
        List<Ticket> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) results.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("DB findByStatus error: " + e.getMessage());
        }
        return results;
    }

    private List<Ticket> queryList(String sql) {
        List<Ticket> results = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) results.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("DB queryList error: " + e.getMessage());
        }
        return results;
    }

    /**
     * Maps a ResultSet row to a Ticket object.
     */
    private Ticket mapRow(ResultSet rs) throws SQLException {
        String ticketCode = rs.getString("ticket_code");
        String plate = rs.getString("license_plate");
        String typeStr = rs.getString("vehicle_type");
        String issuedStr = rs.getString("issued_at");
        String spotCode = rs.getString("spot_code");
        int floor = rs.getInt("floor_number");
        String statusStr = rs.getString("ticket_status");
        double fee = rs.getDouble("fee_charged");
        double paid = rs.getDouble("amount_paid");
        String exitStr = rs.getString("exit_time");

        VehicleType type = VehicleType.valueOf(typeStr);
        LocalDateTime issued = LocalDateTime.parse(issuedStr, FMT);

        Vehicle vehicle = new Vehicle(plate, type, issued);
        ParkingSpot spot = ParkingLot.getInstance()
                .findSpotById(spotCode)
                .orElse(new ParkingSpot(spotCode, floor, SpotType.STANDARD));
        Ticket ticket = new Ticket(vehicle, spot);
        ticket.overrideTicketId(ticketCode);
        ticket.overrideIssuedAt(issued);

        ticket.overrideTicketId(ticketCode);

        // Restore paid/exited state
        TicketStatus status = TicketStatus.valueOf(statusStr);
        if (status == TicketStatus.PAID || status == TicketStatus.EXITED) {
            if (paid >= fee) ticket.pay(fee, paid);
        }
        if (status == TicketStatus.EXITED) {
            ticket.closeOnExit();
        }
        if (status == TicketStatus.LOST) {
            ticket.markLost(fee, paid);
        }

        return ticket;
    }

    private void updateSpotOccupied(String spotCode, boolean occupied) {
        String sql = "UPDATE parking_spots SET is_occupied = ? WHERE spot_code = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, occupied ? 1 : 0);
            ps.setString(2, spotCode);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("DB updateSpot error: " + e.getMessage());
        }
    }

    /**
     * Reads all ACTIVE tickets and marks their spots occupied
     * in the in-memory ParkingLot. Call once on startup.
     */
    public void restoreOccupancyFromDb(ParkingLot lot) {
        String sql = "SELECT spot_code FROM tickets WHERE ticket_status = 'ACTIVE'";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            int count = 0;
            while (rs.next()) {
                String spotCode = rs.getString("spot_code");
                lot.findSpotById(spotCode).ifPresent(spot -> spot.setOccupied(true));
                count++;
            }
            System.out.println("✅ Occupancy restored: " + count + " active spots.");
        } catch (SQLException e) {
            System.err.println("DB restoreOccupancy error: " + e.getMessage());
        }
    }

    private void upsertCustomer(Vehicle vehicle) {
        String sql = """
            INSERT INTO customers (license_plate, vehicle_type)
            VALUES (?, ?)
            ON CONFLICT(license_plate) DO UPDATE SET vehicle_type = excluded.vehicle_type;
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vehicle.getLicensePlate());
            ps.setString(2, vehicle.getType().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("DB upsertCustomer error: " + e.getMessage());
        }
    }
}
