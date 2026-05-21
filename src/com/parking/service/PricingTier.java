package com.parking.service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Defines time-based pricing tiers for the parking lot.
 *
 * <p>The {@link FeeCalculator} checks the active tier at entry time
 * and applies its rate multiplier to the base rate. This allows the
 * admin to set higher rates during peak hours and lower rates at night.</p>
 *
 * <h3>Example setup</h3>
 * <pre>
 *   PricingTier peak    = new PricingTier("Peak",     "07:00", "19:00", 1.5);
 *   PricingTier offPeak = new PricingTier("Off-Peak", "19:00", "07:00", 0.75);
 *   PricingTier.setTiers(List.of(peak, offPeak));
 * </pre>
 */
public class PricingTier {

    // ── Shared tier registry ──────────────────────────────────────────────
    private static final List<PricingTier> tiers = new ArrayList<>();

    // ── Fields ────────────────────────────────────────────────────────────
    private final String    name;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final double    rateMultiplier; // 1.0 = normal, 1.5 = 50% more, 0.75 = 25% less

    // ── Constructor ───────────────────────────────────────────────────────

    /**
     * @param name           Display name, e.g. "Peak Hours"
     * @param startTime      Start time in "HH:mm" format, e.g. "07:00"
     * @param endTime        End time in "HH:mm" format, e.g. "19:00"
     * @param rateMultiplier Multiplier applied to the base rate, e.g. 1.5
     */
    public PricingTier(String name, String startTime, String endTime, double rateMultiplier) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Tier name cannot be blank.");
        if (rateMultiplier <= 0)
            throw new IllegalArgumentException("Rate multiplier must be positive.");

        this.name           = name;
        this.startTime      = LocalTime.parse(startTime);
        this.endTime        = LocalTime.parse(endTime);
        this.rateMultiplier = rateMultiplier;
    }

    // ── Active tier lookup ────────────────────────────────────────────────

    /**
     * Returns the active tier for the given time, or a default 1.0x tier
     * if no tiers are configured or none match.
     */
    public static PricingTier getActiveTier(LocalTime time) {
        if (tiers.isEmpty()) return defaultTier();

        for (PricingTier tier : tiers) {
            if (tier.isActive(time)) return tier;
        }
        return defaultTier();
    }

    /**
     * Convenience method — returns the multiplier active right now.
     */
    public static double getCurrentMultiplier() {
        return getActiveTier(LocalTime.now()).getRateMultiplier();
    }

    /**
     * Returns true if this tier is active at the given time.
     * Handles overnight tiers (e.g. 22:00 → 06:00).
     */
    public boolean isActive(LocalTime time) {
        if (startTime.isBefore(endTime)) {
            // Normal range: e.g. 07:00 → 19:00
            return !time.isBefore(startTime) && time.isBefore(endTime);
        } else {
            // Overnight range: e.g. 22:00 → 06:00
            return !time.isBefore(startTime) || time.isBefore(endTime);
        }
    }

    // ── Tier registry management ──────────────────────────────────────────

    public static void addTier(PricingTier tier) {
        tiers.add(tier);
    }

    public static void setTiers(List<PricingTier> newTiers) {
        tiers.clear();
        tiers.addAll(newTiers);
    }

    public static void clearTiers() {
        tiers.clear();
    }

    public static List<PricingTier> getTiers() {
        return List.copyOf(tiers);
    }

    // ── Default tiers (called at app startup) ─────────────────────────────

    /**
     * Installs a sensible default tier set:
     * Peak (07:00–19:00) at 1.5× and Off-Peak (19:00–07:00) at 0.75×.
     */
    public static void installDefaults() {
        tiers.clear();
        tiers.add(new PricingTier("Peak Hours",    "07:00", "19:00", 1.5));
        tiers.add(new PricingTier("Off-Peak Hours","19:00", "07:00", 0.75));
    }

    private static PricingTier defaultTier() {
        return new PricingTier("Standard", "00:00", "23:59", 1.0);
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public String    getName()           { return name; }
    public LocalTime getStartTime()      { return startTime; }
    public LocalTime getEndTime()        { return endTime; }
    public double    getRateMultiplier() { return rateMultiplier; }

    @Override
    public String toString() {
        return String.format("PricingTier{%s %s–%s ×%.2f}",
                name, startTime, endTime, rateMultiplier);
    }
}