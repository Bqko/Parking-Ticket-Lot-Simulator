package com.parking.db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles database operations for the customers table.
 *
 * <p>A customer record is automatically created when a vehicle
 * enters for the first time. Name and phone can be updated later.</p>
 */
public class CustomerRepository {

    private final Connection conn;

    public CustomerRepository() {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

    // ── Customer record ───────────────────────────────────────────────────

    /** Returns customer info for a given license plate, or null if not found. */
    public CustomerRecord findByPlate(String licensePlate) {
        String sql = "SELECT * FROM customers WHERE license_plate = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, licensePlate.trim().toUpperCase());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new CustomerRecord(
                        rs.getInt("customer_id"),
                        rs.getString("full_name"),
                        rs.getString("phone"),
                        rs.getString("license_plate"),
                        rs.getString("vehicle_type"),
                        rs.getString("created_at")
                );
            }
        } catch (SQLException e) {
            System.err.println("DB findByPlate error: " + e.getMessage());
        }
        return null;
    }

    /** Updates the name and phone for a customer by plate. */
    public void updateCustomerInfo(String licensePlate, String fullName, String phone) {
        String sql = """
            UPDATE customers
            SET full_name = ?, phone = ?
            WHERE license_plate = ?
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setString(2, phone);
            ps.setString(3, licensePlate.trim().toUpperCase());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("DB updateCustomer error: " + e.getMessage());
        }
    }

    /** Returns all customers ordered by most recently created. */
    public List<CustomerRecord> findAll() {
        List<CustomerRecord> list = new ArrayList<>();
        String sql = "SELECT * FROM customers ORDER BY created_at DESC";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new CustomerRecord(
                        rs.getInt("customer_id"),
                        rs.getString("full_name"),
                        rs.getString("phone"),
                        rs.getString("license_plate"),
                        rs.getString("vehicle_type"),
                        rs.getString("created_at")
                ));
            }
        } catch (SQLException e) {
            System.err.println("DB findAllCustomers error: " + e.getMessage());
        }
        return list;
    }

    /** Total number of unique customers ever served. */
    public int totalCustomers() {
        String sql = "SELECT COUNT(*) FROM customers";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.getInt(1);
        } catch (SQLException e) {
            return 0;
        }
    }

    // ── CustomerRecord DTO ────────────────────────────────────────────────

    public static class CustomerRecord {
        public final int    customerId;
        public final String fullName;
        public final String phone;
        public final String licensePlate;
        public final String vehicleType;
        public final String createdAt;

        public CustomerRecord(int id, String name, String phone,
                              String plate, String type, String createdAt) {
            this.customerId   = id;
            this.fullName     = name != null ? name : "Guest";
            this.phone        = phone != null ? phone : "";
            this.licensePlate = plate;
            this.vehicleType  = type;
            this.createdAt    = createdAt;
        }

        @Override
        public String toString() {
            return String.format("Customer{id=%d, name='%s', plate='%s', type=%s}",
                    customerId, fullName, licensePlate, vehicleType);
        }
    }
}