package com.parking;

import com.parking.db.DatabaseManager;
import com.parking.enums.VehicleType;
import com.parking.model.ParkingLot;
import com.parking.model.Vehicle;
import com.parking.util.DummyDataGenerator;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Vehicle Tests")
class VehicleTest {

    @BeforeEach
    void setUp() {
        DatabaseManager.useInMemoryDatabase();
        ParkingLot.resetInstance();
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.getInstance().close();
        ParkingLot.resetInstance();
    }

    // ── Constructor ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Valid vehicle is created with correct fields")
    void constructor_validInput() {
        Vehicle v = new Vehicle("34 ABC 001", VehicleType.CAR);
        assertEquals("34 ABC 001", v.getLicensePlate());
        assertEquals(VehicleType.CAR, v.getType());
        assertNotNull(v.getEntryTime());
    }

    @Test
    @DisplayName("License plate is trimmed and uppercased")
    void constructor_plateNormalized() {
        Vehicle v = new Vehicle("  34 abc 001  ", VehicleType.CAR);
        assertEquals("34 ABC 001", v.getLicensePlate());
    }

    @Test
    @DisplayName("Custom entry time is stored correctly")
    void constructor_customEntryTime() {
        LocalDateTime time = LocalDateTime.of(2025, 6, 1, 10, 0);
        Vehicle v = new Vehicle("34 ABC 001", VehicleType.CAR, time);
        assertEquals(time, v.getEntryTime());
    }

    @Test
    @DisplayName("Null license plate throws exception")
    void constructor_nullPlate() {
        assertThrows(IllegalArgumentException.class,
                () -> new Vehicle(null, VehicleType.CAR));
    }

    @Test
    @DisplayName("Blank license plate throws exception")
    void constructor_blankPlate() {
        assertThrows(IllegalArgumentException.class,
                () -> new Vehicle("   ", VehicleType.CAR));
    }

    @Test
    @DisplayName("Null vehicle type throws exception")
    void constructor_nullType() {
        assertThrows(IllegalArgumentException.class,
                () -> new Vehicle("34 ABC 001", null));
    }

    // ── Rate multipliers ──────────────────────────────────────────────────

    @Test
    @DisplayName("Car rate multiplier is 1.0")
    void multiplier_car() {
        Vehicle v = new Vehicle("34 CAR 001", VehicleType.CAR);
        assertEquals(1.0, v.getRateMultiplier(), 0.001);
    }

    @Test
    @DisplayName("Motorcycle rate multiplier is 0.5")
    void multiplier_motorcycle() {
        Vehicle v = new Vehicle("34 MOT 001", VehicleType.MOTORCYCLE);
        assertEquals(0.5, v.getRateMultiplier(), 0.001);
    }

    @Test
    @DisplayName("Truck rate multiplier is 2.0")
    void multiplier_truck() {
        Vehicle v = new Vehicle("34 TRK 001", VehicleType.TRUCK);
        assertEquals(2.0, v.getRateMultiplier(), 0.001);
    }

    // ── Equality ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Two vehicles with same plate are equal")
    void equality_samePlate() {
        Vehicle v1 = new Vehicle("34 ABC 001", VehicleType.CAR);
        Vehicle v2 = new Vehicle("34 ABC 001", VehicleType.TRUCK);
        assertEquals(v1, v2, "Equality is based on license plate only");
    }

    @Test
    @DisplayName("Two vehicles with different plates are not equal")
    void equality_differentPlates() {
        Vehicle v1 = new Vehicle("34 ABC 001", VehicleType.CAR);
        Vehicle v2 = new Vehicle("34 XYZ 999", VehicleType.CAR);
        assertNotEquals(v1, v2);
    }

    // ── toString ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("toString includes plate and type")
    void toString_containsKeyInfo() {
        Vehicle v = new Vehicle("34 ABC 001", VehicleType.CAR);
        String str = v.toString();
        assertTrue(str.contains("34 ABC 001"));
        assertTrue(str.contains("Car"));
    }

    // ── DummyDataGenerator integration ───────────────────────────────────

    @Test
    @DisplayName("Generated vehicle has non-blank plate and valid type")
    void dummy_generatedVehicleIsValid() {
        Vehicle v = DummyDataGenerator.vehicle();
        assertFalse(v.getLicensePlate().isBlank());
        assertNotNull(v.getType());
        assertNotNull(v.getEntryTime());
    }

    @Test
    @DisplayName("Generated plate matches AB-123-CD format")
    void dummy_plateFormat() {
        for (int i = 0; i < 30; i++) {
            String plate = DummyDataGenerator.plate();
            assertTrue(plate.matches("[A-Z]{2}-\\d{3}-[A-Z]{2}"),
                    "Plate '" + plate + "' does not match AB-123-CD format");
        }
    }

    @Test
    @DisplayName("Generated vehicle with explicit type always has that type")
    void dummy_vehicleWithType() {
        for (VehicleType type : VehicleType.values()) {
            Vehicle v = DummyDataGenerator.vehicle(type);
            assertEquals(type, v.getType());
        }
    }

    @Test
    @DisplayName("Generated vehicle with entry time stores it correctly")
    void dummy_vehicleWithEntryTime() {
        LocalDateTime entry = LocalDateTime.now().minusHours(3);
        Vehicle v = DummyDataGenerator.vehicle(VehicleType.CAR, entry);
        assertEquals(entry, v.getEntryTime());
    }

    @Test
    @DisplayName("Bulk generation produces unique plates")
    void dummy_bulkUniquePlates() {
        List<Vehicle> fleet = DummyDataGenerator.vehicles(25);
        Set<String> plates = fleet.stream()
                .map(Vehicle::getLicensePlate)
                .collect(Collectors.toSet());
        assertEquals(25, plates.size(), "All generated plates should be unique");
    }

    @Test
    @DisplayName("Seeded generator produces reproducible plates")
    void dummy_seededIsReproducible() {
        DummyDataGenerator g1 = DummyDataGenerator.withSeed(42L);
        DummyDataGenerator g2 = DummyDataGenerator.withSeed(42L);
        for (int i = 0; i < 10; i++) {
            assertEquals(g1.nextPlate(), g2.nextPlate(),
                    "Same seed must produce the same plate sequence");
        }
    }
}