package com.parking.service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a named time-of-day pricing tier that applies a rate multiplier
 * during a specific hour range.
 *
 * <p>Tiers are managed as a static in-memory list (persisted to DB via
 * AdminScreen). The active tier at any moment is found by matching the
 * entry hour; if no tier matches, a default multiplier of 1.0 is used.</p>
 *
 * <h3>Example tiers</h3>
 * <pre>
 *   Peak       08:00–18:00  ×1.5
 *   Off-Peak   18:00–08:00  ×0.8
 * </pre>
 */
public class PricingTier {

    // ── Static tier registry ──────────────────────────────────────────────
    private static final List<PricingTier> tiers = new ArrayList<>();

    // ── Fields ────────────────────────────────────────────────────────────
    private String name;
    private int    startHour;   // 0–23
    private int    endHour;     // 0–23 (exclusive)
    private double rateMultiplier;

    public PricingTier(String name, int startHour, int endHour, double rateMultiplier) {
        this.name           = name;
        this.startHour      = startHour;
        this.endHour        = endHour;
        this.rateMultiplier = rateMultiplier;
    }

    // ── Static management API (used by AdminScreen) ───────────────────────

    /**
     * Returns an unmodifiable view of all currently configured tiers.
     */
    public static List<PricingTier> getAllTiers() {
        return Collections.unmodifiableList(tiers);
    }

    /**
     * Adds a new tier or updates an existing one with the same name.
     */
    public static void upsertTier(String name, int startHour, int endHour, double multiplier) {
        tiers.removeIf(t -> t.name.equalsIgnoreCase(name));
        tiers.add(new PricingTier(name, startHour, endHour, multiplier));
        tiers.sort((a, b) -> Integer.compare(a.startHour, b.startHour));
    }

    /**
     * Removes the tier with the given name (case-insensitive). No-op if not found.
     */
    public static void removeTier(String name) {
        tiers.removeIf(t -> t.name.equalsIgnoreCase(name));
    }

    /**
     * Clears all tiers and re-seeds with sensible defaults.
     * Called by ParkingApp on startup via {@code PricingTier.installDefaults()}.
     */
    public static void installDefaults() {
        tiers.clear();
        tiers.add(new PricingTier("Standard",  0,  8, 1.0));
        tiers.add(new PricingTier("Peak",       8, 18, 1.5));
        tiers.add(new PricingTier("Evening",   18, 24, 1.2));
    }

    // ── Active tier lookup (used by FeeCalculator) ────────────────────────

    /**
     * Returns the active pricing tier for the given time, or a default
     * 1.0× tier if no configured tier covers that hour.
     */
    public static PricingTier getActiveTier(LocalTime time) {
        int hour = time.getHour();
        for (PricingTier t : tiers) {
            if (t.startHour <= t.endHour) {
                // Normal range: e.g. 08:00–18:00
                if (hour >= t.startHour && hour < t.endHour) return t;
            } else {
                // Wraps midnight: e.g. 22:00–06:00
                if (hour >= t.startHour || hour < t.endHour) return t;
            }
        }
        // No match → neutral multiplier
        return new PricingTier("Default", 0, 24, 1.0);
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public String getName()            { return name; }
    public int    getStartHour()       { return startHour; }
    public int    getEndHour()         { return endHour; }
    public double getRateMultiplier()  { return rateMultiplier; }

    // ── Setters (for upsert) ──────────────────────────────────────────────

    public void setName(String name)                       { this.name = name; }
    public void setStartHour(int startHour)                { this.startHour = startHour; }
    public void setEndHour(int endHour)                    { this.endHour = endHour; }
    public void setRateMultiplier(double rateMultiplier)   { this.rateMultiplier = rateMultiplier; }

    @Override
    public String toString() {
        return String.format("PricingTier{name='%s', %02d:00–%02d:00, ×%.2f}",
                name, startHour, endHour, rateMultiplier);
    }
}