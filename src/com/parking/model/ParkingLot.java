package com.parking.model;

import com.parking.enums.SpotType;
import com.parking.enums.VehicleType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents the entire parking lot facility.
 *
 * <p>Implements the <b>Singleton</b> pattern so that only one
 * {@code ParkingLot} instance manages the shared state of all spots.
 * Spot allocation is synchronized to prevent race conditions when
 * multiple entry gates assign spots concurrently.</p>
 *
 * <h3>Spot-type mapping</h3>
 * <ul>
 *   <li>MOTORCYCLE  → MOTORCYCLE spots</li>
 *   <li>CAR         → COMPACT or STANDARD spots</li>
 *   <li>TRUCK       → LARGE spots (or STANDARD if none available)</li>
 * </ul>
 */
public class ParkingLot {

    // ── Singleton ─────────────────────────────────────────────────────────
    private static volatile ParkingLot instance;

    /** Returns the single shared instance of the parking lot. */
    public static ParkingLot getInstance() {
        if (instance == null) {
            synchronized (ParkingLot.class) {
                if (instance == null) {
                    instance = new ParkingLot("Main Parking Lot", 3);
                }
            }
        }
        return instance;
    }

    /**
     * Resets the singleton — useful for unit tests only.
     * Do NOT call in production code.
     */
    public static void resetInstance() {
        synchronized (ParkingLot.class) {
            instance = null;
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────
    private final String            name;
    private final int               totalFloors;
    private final List<ParkingSpot> spots;          // All spots in the lot

    // ── Constructor (private — use getInstance()) ─────────────────────────
    private ParkingLot(String name, int totalFloors) {
        this.name        = name;
        this.totalFloors = totalFloors;
        this.spots       = new ArrayList<>();
        initializeSpots();
    }

    /**
     * Populates the lot with a default layout.
     * Customize floor/spot counts here to match your building.
     */
    private void initializeSpots() {
        for (int floor = 0; floor < totalFloors; floor++) {
            // Each floor: 5 motorcycle, 10 compact, 20 standard, 5 large spots
            addSpotsToFloor(floor, "M",  5,  SpotType.MOTORCYCLE);
            addSpotsToFloor(floor, "C", 10,  SpotType.COMPACT);
            addSpotsToFloor(floor, "S", 20,  SpotType.STANDARD);
            addSpotsToFloor(floor, "L",  5,  SpotType.LARGE);
        }
    }

    private void addSpotsToFloor(int floor, String prefix, int count, SpotType type) {
        for (int i = 1; i <= count; i++) {
            String id = String.format("F%d-%s%02d", floor, prefix, i);
            spots.add(new ParkingSpot(id, floor, type));
        }
    }

    // ── Spot assignment ───────────────────────────────────────────────────

    /**
     * Finds and occupies the nearest available spot suitable for {@code vehicle}.
     * Spots are searched from the lowest floor upward (nearest to the entrance).
     *
     * @param vehicle The vehicle needing a spot.
     * @return        The assigned {@link ParkingSpot}.
     * @throws IllegalStateException if no suitable spot is available.
     */
    public synchronized ParkingSpot assignSpot(Vehicle vehicle) {
        SpotType preferred = preferredSpotType(vehicle.getType());

        // 1st pass: look for the preferred spot type
        Optional<ParkingSpot> spot = spots.stream()
                .filter(s -> s.isAvailable() && s.getSpotType() == preferred)
                .findFirst();

        // 2nd pass (trucks only): fall back to STANDARD if no LARGE available
        if (spot.isEmpty() && vehicle.getType() == VehicleType.TRUCK) {
            spot = spots.stream()
                    .filter(s -> s.isAvailable() && s.getSpotType() == SpotType.STANDARD)
                    .findFirst();
        }

        ParkingSpot assigned = spot.orElseThrow(() ->
                new IllegalStateException("No available spots for vehicle type: "
                        + vehicle.getType().getDisplayName()));

        assigned.park(vehicle);
        return assigned;
    }

    /**
     * Frees the spot previously assigned to a vehicle.
     *
     * @param spot The spot to vacate.
     */
    public synchronized void releaseSpot(ParkingSpot spot) {
        Objects.requireNonNull(spot, "Spot cannot be null.");
        spot.vacate();
    }

    // ── Queries ───────────────────────────────────────────────────────────

    /** Total number of spots in the lot across all floors. */
    public int getTotalSpots() { return spots.size(); }

    /** Number of currently occupied spots. */
    public long getOccupiedCount() {
        return spots.stream().filter(ParkingSpot::isOccupied).count();
    }

    /** Number of currently available spots. */
    public long getAvailableCount() {
        return spots.stream().filter(ParkingSpot::isAvailable).count();
    }

    /** Returns true if there is at least one available spot for the given vehicle type. */
    public boolean hasAvailableSpot(VehicleType type) {
        SpotType preferred = preferredSpotType(type);
        boolean hasPreferred = spots.stream()
                .anyMatch(s -> s.isAvailable() && s.getSpotType() == preferred);
        if (hasPreferred) return true;
        // Trucks can also use standard spots
        if (type == VehicleType.TRUCK) {
            return spots.stream()
                    .anyMatch(s -> s.isAvailable() && s.getSpotType() == SpotType.STANDARD);
        }
        return false;
    }

    /** Returns an unmodifiable view of all spots on a given floor. */
    public List<ParkingSpot> getSpotsOnFloor(int floor) {
        return spots.stream()
                .filter(s -> s.getFloor() == floor)
                .collect(Collectors.toUnmodifiableList());
    }

    /** Returns an unmodifiable view of all spots. */
    public List<ParkingSpot> getAllSpots() {
        return Collections.unmodifiableList(spots);
    }

    /** Occupancy percentage (0–100). */
    public double getOccupancyRate() {
        if (spots.isEmpty()) return 0.0;
        return (getOccupiedCount() * 100.0) / getTotalSpots();
    }

    // ── Admin: add/remove spots ───────────────────────────────────────────

    /**
     * Adds a new spot to the lot at runtime (admin operation).
     */
    public synchronized void addSpot(ParkingSpot spot) {
        Objects.requireNonNull(spot, "Spot cannot be null.");
        boolean duplicate = spots.stream().anyMatch(s -> s.getSpotId().equals(spot.getSpotId()));
        if (duplicate) {
            throw new IllegalArgumentException("A spot with ID '" + spot.getSpotId() + "' already exists.");
        }
        spots.add(spot);
    }

    /**
     * Removes a spot from the lot (admin operation).
     * Cannot remove an occupied spot.
     */
    public synchronized void removeSpot(String spotId) {
        ParkingSpot spot = findSpotById(spotId)
                .orElseThrow(() -> new NoSuchElementException("Spot not found: " + spotId));
        if (spot.isOccupied()) {
            throw new IllegalStateException("Cannot remove an occupied spot: " + spotId);
        }
        spots.remove(spot);
    }

    /** Finds a spot by its ID. */
    public Optional<ParkingSpot> findSpotById(String spotId) {
        return spots.stream().filter(s -> s.getSpotId().equals(spotId)).findFirst();
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public String getName()        { return name; }
    public int    getTotalFloors() { return totalFloors; }

    // ── Private helpers ───────────────────────────────────────────────────

    /** Maps a vehicle type to its preferred spot type. */
    private SpotType preferredSpotType(VehicleType vehicleType) {
        return switch (vehicleType) {
            case MOTORCYCLE -> SpotType.MOTORCYCLE;
            case CAR        -> SpotType.STANDARD;
            case TRUCK      -> SpotType.LARGE;
        };
    }

    // ── Status report ─────────────────────────────────────────────────────

    /** Prints a formatted occupancy summary to the console. */
    public void printStatus() {
        System.out.println("╔══════════════════════════════════╗");
        System.out.printf( "║  %-32s║%n", name);
        System.out.println("╠══════════════════════════════════╣");
        System.out.printf( "║  Total spots   : %-14d║%n", getTotalSpots());
        System.out.printf( "║  Occupied      : %-14d║%n", getOccupiedCount());
        System.out.printf( "║  Available     : %-14d║%n", getAvailableCount());
        System.out.printf( "║  Occupancy     : %13.1f%%║%n", getOccupancyRate());
        System.out.println("╚══════════════════════════════════╝");
    }

    @Override
    public String toString() {
        return String.format("ParkingLot{name='%s', floors=%d, spots=%d, available=%d}",
                name, totalFloors, getTotalSpots(), getAvailableCount());
    }
}