package com.parking;

import com.parking.db.DatabaseManager;
import com.parking.enums.SpotType;
import com.parking.enums.VehicleType;
import com.parking.model.ParkingLot;
import com.parking.model.ParkingSpot;
import com.parking.model.Vehicle;
import com.parking.util.DummyDataGenerator;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ParkingLot Tests")
class ParkingLotTest {

    private ParkingLot lot;

    @BeforeEach
    void setUp() {
        DatabaseManager.useInMemoryDatabase();
        ParkingLot.resetInstance();
        lot = ParkingLot.getInstance();
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.getInstance().close();
        ParkingLot.resetInstance();
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
    void initial_hasSpots() {
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

    // ── Assign ────────────────────────────────────────────────────────────

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

    // ── Release ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Releasing a spot makes it available again")
    void release_spotBecomesAvailable() {
        Vehicle car = new Vehicle("34 CAR 001", VehicleType.CAR);
        ParkingSpot spot = lot.assignSpot(car);
        long occupiedBefore = lot.getOccupiedCount();
        lot.releaseSpot(spot);
        assertFalse(spot.isOccupied());
        assertEquals(occupiedBefore - 1, lot.getOccupiedCount());
    }

    // ── Availability ──────────────────────────────────────────────────────

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

    // ── Floor ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getSpotsOnFloor returns only spots on that floor")
    void floor_correctSpots() {
        var floor0 = lot.getSpotsOnFloor(0);
        assertFalse(floor0.isEmpty());
        floor0.forEach(s -> assertEquals(0, s.getFloor()));
    }

    // ── DummyDataGenerator integration ───────────────────────────────────

    @Test
    @DisplayName("Multiple generated vehicles can all be assigned spots")
    void dummy_multipleVehiclesAssigned() {
        List<Vehicle> fleet = DummyDataGenerator.vehicles(5, VehicleType.CAR);
        long before = lot.getAvailableCount();
        for (Vehicle v : fleet) {
            lot.assignSpot(v);
        }
        assertEquals(before - 5, lot.getAvailableCount());
        assertEquals(5, lot.getOccupiedCount());
    }

    @Test
    @DisplayName("Assigning and releasing generated vehicles restores occupancy")
    void dummy_assignAndRelease_restoresCount() {
        long initial = lot.getAvailableCount();
        List<Vehicle> fleet = DummyDataGenerator.vehicles(4, VehicleType.CAR);

        List<ParkingSpot> assigned = new java.util.ArrayList<>();
        for (Vehicle v : fleet) assigned.add(lot.assignSpot(v));

        assertEquals(initial - 4, lot.getAvailableCount());

        for (ParkingSpot s : assigned) lot.releaseSpot(s);

        assertEquals(initial, lot.getAvailableCount());
        assertEquals(0, lot.getOccupiedCount());
    }

    @Test
    @DisplayName("Occupancy rate increases correctly as spots are filled")
    void dummy_occupancyRateTracksAssignments() {
        // Fill 1 car and verify rate increased from 0
        assertEquals(0.0, lot.getOccupancyRate(), 0.001);
        lot.assignSpot(DummyDataGenerator.vehicle(VehicleType.CAR));
        assertTrue(lot.getOccupancyRate() > 0.0, "Occupancy rate should be > 0 after one assignment");
    }

    @Test
    @DisplayName("Mixed vehicle types all get correctly typed spots")
    void dummy_mixedTypesGetCorrectSpots() {
        Vehicle car   = DummyDataGenerator.vehicle(VehicleType.CAR);
        Vehicle moto  = DummyDataGenerator.vehicle(VehicleType.MOTORCYCLE);
        Vehicle truck = DummyDataGenerator.vehicle(VehicleType.TRUCK);

        ParkingSpot carSpot   = lot.assignSpot(car);
        ParkingSpot motoSpot  = lot.assignSpot(moto);
        ParkingSpot truckSpot = lot.assignSpot(truck);

        // Cars go to STANDARD spots, motos to MOTORCYCLE, trucks to LARGE
        assertNotEquals(SpotType.MOTORCYCLE, carSpot.getSpotType());
        assertEquals(SpotType.MOTORCYCLE,    motoSpot.getSpotType());
        assertEquals(SpotType.LARGE,         truckSpot.getSpotType());
    }
}