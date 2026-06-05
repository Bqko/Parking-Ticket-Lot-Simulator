package com.parking;

import com.parking.db.DatabaseManager;
import com.parking.enums.VehicleType;
import com.parking.model.ParkingLot;
import com.parking.model.Vehicle;
import com.parking.util.DummyDataGenerator;
import org.junit.jupiter.api.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DummyDataGenerator}.
 *
 * Also serves as a usage reference — each test shows a typical
 * pattern you can copy into your other test classes.
 */
@DisplayName("DummyDataGenerator Tests")
class DummyDataGeneratorTest {

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

    // ── License plates ────────────────────────────────────────────────────

    @Test
    @DisplayName("plate() returns a non-blank string")
    void plate_notBlank() {
        assertFalse(DummyDataGenerator.plate().isBlank());
    }

    @Test
    @DisplayName("plate() matches Georgian format ABC-123")
    void plate_formatIsValid() {
        for (int i = 0; i < 50; i++) {
            String plate = DummyDataGenerator.plate();
            assertTrue(plate.matches("[A-Z]{2}-\\d{3}-[A-Z]{2}"),
                    "Plate '" + plate + "' does not match AB-123-CD format");
        }
    }

    @Test
    @DisplayName("plates(n) returns n unique plates")
    void plates_uniqueList() {
        List<String> plates = DummyDataGenerator.plates(20);
        assertEquals(20, plates.size());
        assertEquals(20, new HashSet<>(plates).size(), "All plates should be unique");
    }

    // ── Names ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("firstName() returns a non-blank string")
    void firstName_notBlank() {
        assertFalse(DummyDataGenerator.firstName().isBlank());
    }

    @Test
    @DisplayName("lastName() returns a non-blank string")
    void lastName_notBlank() {
        assertFalse(DummyDataGenerator.lastName().isBlank());
    }

    @Test
    @DisplayName("fullName() contains a space (first + last)")
    void fullName_hasTwoParts() {
        String name = DummyDataGenerator.fullName();
        assertTrue(name.contains(" "), "Full name should have at least two parts: '" + name + "'");
    }

    @Test
    @DisplayName("fullNameLatin() contains only ASCII letters and a space")
    void fullNameLatin_isAscii() {
        for (int i = 0; i < 20; i++) {
            String name = DummyDataGenerator.fullNameLatin();
            assertTrue(name.matches("[A-Za-z]+ [A-Za-z]+"),
                    "Latin name '" + name + "' should contain only letters and one space");
        }
    }

    @Test
    @DisplayName("Generator produces variety in names (not always the same)")
    void fullName_hasVariety() {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < 30; i++) names.add(DummyDataGenerator.fullName());
        assertTrue(names.size() > 5, "Expected a variety of names, got: " + names.size());
    }

    // ── Phone numbers ─────────────────────────────────────────────────────

    @Test
    @DisplayName("phone() starts with +995")
    void phone_startsWithCountryCode() {
        assertTrue(DummyDataGenerator.phone().startsWith("+995"));
    }

    @Test
    @DisplayName("phone() matches +995 5XX XXX XXX format")
    void phone_formatIsValid() {
        for (int i = 0; i < 50; i++) {
            String phone = DummyDataGenerator.phone();
            assertTrue(phone.matches("\\+995 \\d{3} \\d{3} \\d{3}"),
                    "Phone '" + phone + "' does not match expected format");
        }
    }

    @Test
    @DisplayName("phones(n) returns exactly n items")
    void phones_correctCount() {
        assertEquals(15, DummyDataGenerator.phones(15).size());
    }

    // ── Vehicles ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("vehicle() returns a non-null Vehicle with valid plate")
    void vehicle_notNull() {
        Vehicle v = DummyDataGenerator.vehicle();
        assertNotNull(v);
        assertFalse(v.getLicensePlate().isBlank());
        assertNotNull(v.getType());
    }

    @Test
    @DisplayName("vehicle(CAR) always produces a CAR")
    void vehicle_withTypeIsCar() {
        for (int i = 0; i < 10; i++) {
            assertEquals(VehicleType.CAR, DummyDataGenerator.vehicle(VehicleType.CAR).getType());
        }
    }

    @Test
    @DisplayName("vehicle(MOTORCYCLE) always produces a MOTORCYCLE")
    void vehicle_withTypeIsMotorcycle() {
        assertEquals(VehicleType.MOTORCYCLE,
                DummyDataGenerator.vehicle(VehicleType.MOTORCYCLE).getType());
    }

    @Test
    @DisplayName("vehicles(n) returns n vehicles with unique plates")
    void vehicles_uniquePlates() {
        List<Vehicle> fleet = DummyDataGenerator.vehicles(10);
        assertEquals(10, fleet.size());
        Set<String> plates = new HashSet<>();
        fleet.forEach(v -> plates.add(v.getLicensePlate()));
        assertEquals(10, plates.size(), "All vehicle plates should be unique");
    }

    @Test
    @DisplayName("vehicles(n, CAR) returns n CARs")
    void vehicles_allSameType() {
        List<Vehicle> cars = DummyDataGenerator.vehicles(5, VehicleType.CAR);
        assertEquals(5, cars.size());
        cars.forEach(v -> assertEquals(VehicleType.CAR, v.getType()));
    }

    // ── Seeded / reproducible ─────────────────────────────────────────────

    @Test
    @DisplayName("Same seed produces identical plates")
    void seed_reproduciblePlates() {
        DummyDataGenerator g1 = DummyDataGenerator.withSeed(42L);
        DummyDataGenerator g2 = DummyDataGenerator.withSeed(42L);
        for (int i = 0; i < 10; i++) {
            assertEquals(g1.nextPlate(), g2.nextPlate(),
                    "Same seed should produce the same sequence");
        }
    }

    @Test
    @DisplayName("Same seed produces identical phone numbers")
    void seed_reproduciblePhones() {
        DummyDataGenerator g1 = DummyDataGenerator.withSeed(99L);
        DummyDataGenerator g2 = DummyDataGenerator.withSeed(99L);
        for (int i = 0; i < 10; i++) {
            assertEquals(g1.nextPhone(), g2.nextPhone());
        }
    }

    @Test
    @DisplayName("Same seed produces identical full names")
    void seed_reproducibleNames() {
        DummyDataGenerator g1 = DummyDataGenerator.withSeed(7L);
        DummyDataGenerator g2 = DummyDataGenerator.withSeed(7L);
        for (int i = 0; i < 10; i++) {
            assertEquals(g1.nextFullNameLatin(), g2.nextFullNameLatin());
        }
    }

    // ── Integration: use generator in a realistic test scenario ──────────

    @Test
    @DisplayName("Generated vehicles are accepted by TicketManager (smoke test)")
    void integration_vehiclesAreValidForTicketing() {
        // Shows the typical pattern: generate a fleet and issue tickets
        List<Vehicle> fleet = DummyDataGenerator.vehicles(3, VehicleType.CAR);
        com.parking.service.FeeCalculator calc = new com.parking.service.FeeCalculator();
        calc.setGracePeriodMinutes(0);
        com.parking.service.TicketManager manager = new com.parking.service.TicketManager(calc);

        for (Vehicle v : fleet) {
            assertDoesNotThrow(() -> manager.issueTicket(v),
                    "issueTicket should accept vehicle with plate: " + v.getLicensePlate());
        }

        assertEquals(3, manager.getActiveTickets().size());
    }
}