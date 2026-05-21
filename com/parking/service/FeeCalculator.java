package com.parking.service;

import com.parking.model.Ticket;
import com.parking.model.Vehicle;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Calculates the parking fee for a given ticket.
 *
 * <p>Implements the <b>Strategy</b> pattern: the calculation algorithm
 * (base rate, multipliers, caps, discounts) is encapsulated here and
 * can be swapped or subclassed without touching other classes.</p>
 *
 * <h3>Pricing rules</h3>
 * <ul>
 *   <li>Fee = ceil(durationHours) × baseRatePerHour × vehicleTypeMultiplier</li>
 *   <li>Any stay under {@code gracePeriodMinutes} is free.</li>
 *   <li>Fee is capped at {@code dailyMaxRate} per 24-hour period.</li>
 *   <li>Discount percentage can be applied (0–100).</li>
 *   <li>Lost ticket → flat {@code lostTicketFee} regardless of duration.</li>
 * </ul>
 */
public class FeeCalculator {

    // ── Configuration (setters allow admin to change at runtime) ──────────
    private double baseRatePerHour;      // Base hourly rate (e.g. 20.0 TRY)
    private double dailyMaxRate;         // Cap per 24-hour period (e.g. 200.0 TRY)
    private double lostTicketFee;        // Flat fee for lost tickets (e.g. 150.0 TRY)
    private int    gracePeriodMinutes;   // Free window after entry (e.g. 15 min)
    private double discountPercent;      // 0.0 = no discount, 50.0 = half price

    // ── Constructor ───────────────────────────────────────────────────────

    /**
     * Creates a calculator with default pricing values.
     */
    public FeeCalculator() {
        this.baseRatePerHour    = 20.0;
        this.dailyMaxRate       = 200.0;
        this.lostTicketFee      = 150.0;
        this.gracePeriodMinutes = 15;
        this.discountPercent    = 0.0;
    }

    /**
     * Creates a calculator with fully custom pricing.
     */
    public FeeCalculator(double baseRatePerHour, double dailyMaxRate,
                         double lostTicketFee, int gracePeriodMinutes,
                         double discountPercent) {
        setBaseRatePerHour(baseRatePerHour);
        setDailyMaxRate(dailyMaxRate);
        setLostTicketFee(lostTicketFee);
        setGracePeriodMinutes(gracePeriodMinutes);
        setDiscountPercent(discountPercent);
    }

    // ── Core calculation ──────────────────────────────────────────────────

    /**
     * Calculates the fee for a ticket using the current pricing rules.
     *
     * @param ticket The active ticket to price.
     * @return       The fee due in local currency, rounded to 2 decimal places.
     */
    public double calculate(Ticket ticket) {
        return calculate(ticket.getVehicle(), ticket.getIssuedAt(), LocalDateTime.now());
    }

    /**
     * Calculates the fee given a vehicle and explicit entry/exit times.
     * Useful for previewing fees or for testing without a real Ticket object.
     *
     * @param vehicle   The vehicle being charged.
     * @param entryTime When the vehicle entered.
     * @param exitTime  When the vehicle is exiting (usually now).
     * @return          Fee in local currency.
     */
    public double calculate(Vehicle vehicle, LocalDateTime entryTime, LocalDateTime exitTime) {
        if (entryTime == null || exitTime == null) {
            throw new IllegalArgumentException("Entry and exit times must not be null.");
        }
        if (exitTime.isBefore(entryTime)) {
            throw new IllegalArgumentException("Exit time cannot be before entry time.");
        }

        Duration duration = Duration.between(entryTime, exitTime);
        long minutesParked = duration.toMinutes();

        // 1. Grace period — free if within the grace window
        if (minutesParked <= gracePeriodMinutes) {
            return 0.0;
        }

        // 2. Convert to hours, rounding up every started hour
        double hoursParked = Math.ceil(minutesParked / 60.0);

        // 3. Base fee = hours × rate × vehicle multiplier
        double fee = hoursParked * baseRatePerHour * vehicle.getRateMultiplier();

        // 4. Apply daily maximum cap (per 24-hour block)
        long fullDays = duration.toDays();
        double cap = (fullDays + 1) * dailyMaxRate;
        fee = Math.min(fee, cap);

        // 5. Apply discount
        if (discountPercent > 0) {
            fee = applyDiscount(fee, discountPercent);
        }

        return round(fee);
    }

    /**
     * Returns the flat fee for a lost ticket.
     * The caller should use this value instead of {@code calculate()} in that case.
     */
    public double getLostTicketFee() {
        return lostTicketFee;
    }

    /**
     * Applies a percentage discount to the given amount.
     *
     * @param amount          Original fee.
     * @param discountPercent Discount to apply (0–100).
     * @return                Discounted fee.
     */
    public double applyDiscount(double amount, double discountPercent) {
        if (discountPercent < 0 || discountPercent > 100) {
            throw new IllegalArgumentException("Discount must be between 0 and 100.");
        }
        return amount * (1.0 - discountPercent / 100.0);
    }

    /**
     * Estimates the fee for an ongoing session without closing the ticket.
     */
    public double preview(Ticket ticket) {
        return calculate(ticket.getVehicle(), ticket.getIssuedAt(), LocalDateTime.now());
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // ── Getters & Setters (admin can update rates at runtime) ─────────────

    public double getBaseRatePerHour()    { return baseRatePerHour; }
    public double getDailyMaxRate()       { return dailyMaxRate; }
    public int    getGracePeriodMinutes() { return gracePeriodMinutes; }
    public double getDiscountPercent()    { return discountPercent; }

    public void setBaseRatePerHour(double rate) {
        if (rate < 0) throw new IllegalArgumentException("Rate cannot be negative.");
        this.baseRatePerHour = rate;
    }
    public void setDailyMaxRate(double cap) {
        if (cap < 0) throw new IllegalArgumentException("Daily max rate cannot be negative.");
        this.dailyMaxRate = cap;
    }
    public void setLostTicketFee(double fee) {
        if (fee < 0) throw new IllegalArgumentException("Lost ticket fee cannot be negative.");
        this.lostTicketFee = fee;
    }
    public void setGracePeriodMinutes(int minutes) {
        if (minutes < 0) throw new IllegalArgumentException("Grace period cannot be negative.");
        this.gracePeriodMinutes = minutes;
    }
    public void setDiscountPercent(double pct) {
        if (pct < 0 || pct > 100) throw new IllegalArgumentException("Discount must be 0–100.");
        this.discountPercent = pct;
    }

    @Override
    public String toString() {
        return String.format(
                "FeeCalculator{baseRate=%.2f, dailyCap=%.2f, grace=%dmin, discount=%.1f%%}",
                baseRatePerHour, dailyMaxRate, gracePeriodMinutes, discountPercent);
    }
}