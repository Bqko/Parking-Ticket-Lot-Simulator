package com.parking.db;

import com.parking.service.FeeCalculator;

import java.sql.*;

/**
 * Persists and restores {@link FeeCalculator} settings in the fee_config table.
 *
 * <p>Every time the admin saves new pricing rules, a new row is inserted
 * with the current timestamp so there's a full history of pricing changes.</p>
 */
public class FeeConfigRepository {

    private final Connection conn;

    public FeeConfigRepository() {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

    // ── Save ──────────────────────────────────────────────────────────────

    /**
     * Inserts the current FeeCalculator settings as a new config row.
     */
    public void save(FeeCalculator calc) {
        String sql = """
            INSERT INTO fee_config
                (base_rate_per_hour, daily_max_rate, lost_ticket_fee,
                 grace_period_minutes, discount_percent, effective_from)
            VALUES (?, ?, ?, ?, ?, datetime('now'));
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, calc.getBaseRatePerHour());
            ps.setDouble(2, calc.getDailyMaxRate());
            ps.setDouble(3, calc.getLostTicketFee());
            ps.setInt(4,    calc.getGracePeriodMinutes());
            ps.setDouble(5, calc.getDiscountPercent());
            ps.executeUpdate();
            System.out.println("💾 Fee config saved to database.");
        } catch (SQLException e) {
            System.err.println("DB save fee config error: " + e.getMessage());
        }
    }

    // ── Load ──────────────────────────────────────────────────────────────

    /**
     * Loads the most recent fee config from the database into
     * the given FeeCalculator instance.
     *
     * @return true if a config was found and applied, false otherwise.
     */
    public boolean loadLatest(FeeCalculator calc) {
        String sql = """
            SELECT * FROM fee_config
            ORDER BY effective_from DESC
            LIMIT 1
        """;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                calc.setBaseRatePerHour(   rs.getDouble("base_rate_per_hour"));
                calc.setDailyMaxRate(      rs.getDouble("daily_max_rate"));
                calc.setLostTicketFee(     rs.getDouble("lost_ticket_fee"));
                calc.setGracePeriodMinutes(rs.getInt(   "grace_period_minutes"));
                calc.setDiscountPercent(   rs.getDouble("discount_percent"));
                System.out.println("✅ Fee config loaded from database.");
                return true;
            }
        } catch (SQLException e) {
            System.err.println("DB load fee config error: " + e.getMessage());
        }
        return false;
    }

    /** Returns how many times pricing has been changed. */
    public int configChangeCount() {
        String sql = "SELECT COUNT(*) FROM fee_config";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.getInt(1);
        } catch (SQLException e) {
            return 0;
        }
    }
}