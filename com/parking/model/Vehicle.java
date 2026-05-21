package com.parking.model;

import com.parking.enums.VehicleType;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a vehicle that enters the parking lot.
 *
 * <p>Encapsulates the vehicle's identity (license plate, type)
 * and the exact moment it arrived. Immutable after construction
 * so that audit records stay consistent.</p>
 *
 * <p>Responsibilities (Single Responsibility Principle):
 * storing vehicle data only — fee calculation and spot assignment
 * are handled by dedicated service classes.</p>
 */
public class Vehicle {

    // ── Fields ────────────────────────────────────────────────────────────
    private final String        licensePlate;   // Unique identifier, e.g. "34 ABC 001"
    private final VehicleType   type;           // CAR, MOTORCYCLE, or TRUCK
    private final LocalDateTime entryTime;      // Timestamp when vehicle entered

    // ── Constructors ──────────────────────────────────────────────────────

    /**
     * Full constructor — use when entry time is already known (e.g. loaded from file).
     */
    public Vehicle(String licensePlate, VehicleType type, LocalDateTime entryTime) {
        if (licensePlate == null || licensePlate.isBlank()) {
            throw new IllegalArgumentException("License plate cannot be null or blank.");
        }
        if (type == null) {
            throw new IllegalArgumentException("Vehicle type cannot be null.");
        }
        this.licensePlate = licensePlate.trim().toUpperCase();
        this.type         = type;
        this.entryTime    = (entryTime != null) ? entryTime : LocalDateTime.now();
    }

    /**
     * Convenience constructor — entry time defaults to now.
     */
    public Vehicle(String licensePlate, VehicleType type) {
        this(licensePlate, type, LocalDateTime.now());
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public String        getLicensePlate() { return licensePlate; }
    public VehicleType   getType()         { return type; }
    public LocalDateTime getEntryTime()    { return entryTime; }

    // ── Business helpers ──────────────────────────────────────────────────

    /**
     * Returns the rate multiplier for this vehicle type.
     * Delegates to the enum so callers don't need to import it directly.
     */
    public double getRateMultiplier() {
        return type.getRateMultiplier();
    }

    // ── Object overrides ──────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vehicle)) return false;
        Vehicle v = (Vehicle) o;
        return Objects.equals(licensePlate, v.licensePlate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(licensePlate);
    }

    @Override
    public String toString() {
        return String.format("Vehicle{plate='%s', type=%s, entry=%s}",
                licensePlate, type.getDisplayName(), entryTime);
    }
}