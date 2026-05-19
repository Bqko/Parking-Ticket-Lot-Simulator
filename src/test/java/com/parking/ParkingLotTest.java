package com.parking;

import com.parking.enums.SpotType;
import com.parking.enums.VehicleType;
import com.parking.model.ParkingLot;
import com.parking.model.ParkingSpot;
import com.parking.model.Vehicle;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ParkingLot Tests")
class ParkingLotTest {

    private ParkingLot lot;

    @BeforeEach
    void setUp() {
        // Reset singleton between tests so each starts fresh
        ParkingLot.resetInstance();
        lot = ParkingLot.getInstance();
    }

    // ── Singleton ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getInstance() always returns the same object")
    void singleton_sameInstance() {
        ParkingLot a = ParkingLot.getInstance();
        ParkingLot b = ParkingLot.getInstance();
        assertSame(a, b);
    }

    // ── Initial state ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Lot is initialised with spots")
    void initial_hasSports() {
        assertTrue(lot.getTotalSpots() > 0);
    }

    @Test
    @DisplayName("All spots start available")
    void initial_allAvailable() {
        assertEquals(lot.getTotalSpots(), lot.getAvailableCount());
        assertEquals(0, lot.getOccupiedCount());
    }

    @Test
    @DisplayName("Occupancy rate starts at 0%")
    void initial_zeroOccupancy() {
        assertEquals(0.0, lot.getOccupancyRate(), 0.001);
    }

    // ── Spot assignment ───────────────────────────────────────────────────

    @Test
    @DisplayName("Assigning a spot reduces available count by 1")
    void assign_reducesAvailable() {
        long before = lot.getAvailableCount();
        Vehicle car = new Vehicle("34 CAR 001", VehicleType.CAR);
        lot.assignSpot(car);
        assertEquals(before - 1, lot.getAvailableCount());
        assertEquals(1, lot.getOccupiedCount());
    }

    @Test
    @DisplayName("Assigned spot is marked as occupied")
    void assign_spotIsOccupied() {
        Vehicle car = new Vehicle("34 CAR 001", VehicleType.CAR);
        ParkingSpot spot = lot.assignSpot(car);
        assertTrue(spot.isOccupied());
        assertEquals(car, spot.getCurrentVehicle());
    }

    @Test
    @DisplayName("Motorcycle gets a MOTORCYCLE spot")
    void assign_motorcycleSpot() {
        Vehicle moto = new Vehicle("34 MOT 001", VehicleType.MOTORCYCLE);
        ParkingSpot spot = lot.assignSpot(moto);
        assertEquals(SpotType.MOTORCYCLE, spot.getSpotType());
    }

    @Test
    @DisplayName("Truck gets a LARGE spot")
    void assign_truckSpot() {
        Vehicle truck = new Vehicle("34 TRK 001", VehicleType.TRUCK);
        ParkingSpot spot = lot.assignSpot(truck);
        assertEquals(SpotType.LARGE, spot.getSpotType());
    }

    // ── Spot release ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Releasing a spot makes it available again")
    void release_spotsBecomesAvailable() {
        Vehicle car = new Vehicle("34 CAR 001", VehicleType.CAR);
        ParkingSpot spot = lot.assignSpot(car);
        long occupiedBefore = lot.getOccupiedCount();

        lot.releaseSpot(spot);

        assertFalse(spot.isOccupied());
        assertEquals(occupiedBefore - 1, lot.getOccupiedCount());
    }

    // ── Availability check ────────────────────────────────────────────────

    @Test
    @DisplayName("hasAvailableSpot returns true initially for all types")
    void available_trueInitially() {
        assertTrue(lot.hasAvailableSpot(VehicleType.CAR));
        assertTrue(lot.hasAvailableSpot(VehicleType.MOTORCYCLE));
        assertTrue(lot.hasAvailableSpot(VehicleType.TRUCK));
    }

    // ── Add / remove spots ────────────────────────────────────────────────

    @Test
    @DisplayName("Adding a new spot increases total count")
    void addSpot_increasesTotal() {
        int before = lot.getTotalSpots();
        lot.addSpot(new ParkingSpot("TEST-01", 0, SpotType.STANDARD));
        assertEquals(before + 1, lot.getTotalSpots());
    }

    @Test
    @DisplayName("Adding duplicate spot ID throws exception")
    void addSpot_duplicateId() {
        ParkingSpot existing = lot.getAllSpots().get(0);
        assertThrows(IllegalArgumentException.class,
                () -> lot.addSpot(new ParkingSpot(existing.getSpotId(), 0, SpotType.STANDARD)));
    }

    @Test
    @DisplayName("Removing a free spot decreases total count")
    void removeSpot_decreasesTotal() {
        ParkingSpot free = lot.getAllSpots().stream()
                .filter(ParkingSpot::isAvailable).findFirst().orElseThrow();
        int before = lot.getTotalSpots();
        lot.removeSpot(free.getSpotId());
        assertEquals(before - 1, lot.getTotalSpots());
    }

    @Test
    @DisplayName("Removing an occupied spot throws exception")
    void removeSpot_occupied() {
        Vehicle car = new Vehicle("34 CAR 001", VehicleType.CAR);
        ParkingSpot spot = lot.assignSpot(car);
        assertThrows(IllegalStateException.class,
                () -> lot.removeSpot(spot.getSpotId()));
    }

    @Test
    @DisplayName("Removing non-existent spot throws exception")
    void removeSpot_notFound() {
        assertThrows(java.util.NoSuchElementException.class,
                () -> lot.removeSpot("DOES-NOT-EXIST"));
    }

    // ── Floor queries ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getSpotsOnFloor returns only spots on that floor")
    void floor_correctSpots() {
        var floor0 = lot.getSpotsOnFloor(0);
        assertFalse(floor0.isEmpty());
        floor0.forEach(s -> assertEquals(0, s.getFloor()));
    }
}