package com.parking.model;

import com.parking.enums.SpotType;
import java.util.Objects;

/**
 * Represents a single physical parking spot in the lot.
 *
 * <p>Tracks the spot's identifier, floor, type, and current
 * occupancy. Thread-safety is left to the {@code ParkingLot}
 * layer which synchronizes spot allocation.</p>
 */
public class ParkingSpot {

    // ── Fields ────────────────────────────────────────────────────────────
    private final String    spotId;     // e.g. "A-01", "B-12"
    private final int       floor;      // 0 = ground, 1 = first, etc.
    private final SpotType  spotType;   // MOTORCYCLE, COMPACT, STANDARD, LARGE
    private boolean         occupied;
    private Vehicle         currentVehicle; // null when free

    // ── Constructor ───────────────────────────────────────────────────────

    public ParkingSpot(String spotId, int floor, SpotType spotType) {
        if (spotId == null || spotId.isBlank()) {
            throw new IllegalArgumentException("Spot ID cannot be null or blank.");
        }
        this.spotId    = spotId.toUpperCase();
        this.floor     = floor;
        this.spotType  = spotType;
        this.occupied  = false;
        this.currentVehicle = null;
    }

    // ── Occupancy control ─────────────────────────────────────────────────

    /**
     * Marks the spot as occupied by the given vehicle.
     *
     * @throws IllegalStateException if the spot is already occupied.
     */
    public void park(Vehicle vehicle) {
        if (occupied) {
            throw new IllegalStateException("Spot " + spotId + " is already occupied.");
        }
        this.currentVehicle = Objects.requireNonNull(vehicle, "Vehicle cannot be null.");
        this.occupied = true;
    }

    /**
     * Frees the spot when a vehicle exits.
     *
     * @throws IllegalStateException if the spot is already free.
     */
    public void vacate() {
        if (!occupied) {
            throw new IllegalStateException("Spot " + spotId + " is already vacant.");
        }
        this.currentVehicle = null;
        this.occupied = false;
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public String    getSpotId()         { return spotId; }
    public int       getFloor()          { return floor; }
    public SpotType  getSpotType()       { return spotType; }
    public boolean   isOccupied()        { return occupied; }
    public boolean   isAvailable()       { return !occupied; }
    public Vehicle   getCurrentVehicle() { return currentVehicle; }

    // ── Object overrides ──────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ParkingSpot)) return false;
        ParkingSpot s = (ParkingSpot) o;
        return Objects.equals(spotId, s.spotId);
    }

    @Override
    public int hashCode() { return Objects.hash(spotId); }

    @Override
    public String toString() {
        return String.format("ParkingSpot{id='%s', floor=%d, type=%s, occupied=%b}",
                spotId, floor, spotType, occupied);
    }
}