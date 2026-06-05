package com.parking;

import com.parking.db.DatabaseManager;
import com.parking.enums.TicketStatus;
import com.parking.enums.VehicleType;
import com.parking.model.ParkingLot;
import com.parking.model.Ticket;
import com.parking.model.Vehicle;
import com.parking.service.FeeCalculator;
import com.parking.service.TicketManager;
import com.parking.util.DummyDataGenerator;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress / load tests for the Parking Lot Ticket Simulator.
 *
 * <p>These tests flood the lot with large numbers of vehicles to verify
 * correctness under load — occupancy tracking, duplicate rejection,
 * revenue accuracy, spot exhaustion, and full recovery after mass exit.</p>
 *
 * <p>All tests use an in-memory SQLite database and reset state between runs.</p>
 */
@DisplayName("Stress / Load Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StressTest {

    private TicketManager manager;
    private ParkingLot    lot;

    @BeforeEach
    void setUp() {
        DatabaseManager.useInMemoryDatabase();
        ParkingLot.resetInstance();
        FeeCalculator calc = new FeeCalculator();
        calc.setGracePeriodMinutes(0); // charge from minute 1 so revenue tests are deterministic
        manager = new TicketManager(calc);
        lot     = ParkingLot.getInstance();
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.getInstance().close();
        ParkingLot.resetInstance();
    }

    // ── Bulk entry ────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("50 unique vehicles can all be issued tickets without errors")
    void bulk_50VehiclesAllIssued() {
        List<Vehicle> fleet = DummyDataGenerator.vehicles(50);
        List<Ticket>  issued = new ArrayList<>();

        for (Vehicle v : fleet) {
            assertDoesNotThrow(() -> issued.add(manager.issueTicket(v)),
                    "Should issue ticket for: " + v.getLicensePlate());
        }

        assertEquals(50, issued.size(), "All 50 vehicles should have tickets");
        assertEquals(50, manager.getActiveTickets().size());
        issued.forEach(t -> assertEquals(TicketStatus.ACTIVE, t.getStatus()));
    }

    @Test
    @Order(2)
    @DisplayName("All 50 issued tickets have unique ticket IDs")
    void bulk_allTicketIdsUnique() {
        List<Vehicle> fleet = DummyDataGenerator.vehicles(50);
        List<String>  ids   = new ArrayList<>();

        for (Vehicle v : fleet) {
            ids.add(manager.issueTicket(v).getTicketId());
        }

        long uniqueCount = ids.stream().distinct().count();
        assertEquals(50, uniqueCount, "Every ticket must have a unique ID");
    }

    @Test
    @Order(3)
    @DisplayName("Occupancy count matches number of issued tickets")
    void bulk_occupancyTracksIssuedTickets() {
        long initial = lot.getAvailableCount();
        List<Vehicle> fleet = DummyDataGenerator.vehicles(30, VehicleType.CAR);

        for (Vehicle v : fleet) manager.issueTicket(v);

        assertEquals(30, lot.getOccupiedCount(),
                "Occupied count should equal number of issued tickets");
        assertEquals(initial - 30, lot.getAvailableCount(),
                "Available count should decrease by exactly 30");
    }

    // ── Duplicate rejection ───────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("Re-entering a still-parked vehicle is rejected")
    void duplicate_sameVehicleRejected() {
        List<Vehicle> fleet = DummyDataGenerator.vehicles(10);
        for (Vehicle v : fleet) manager.issueTicket(v);

        // Try to re-enter every vehicle that is still active
        for (Vehicle v : fleet) {
            assertThrows(IllegalStateException.class,
                    () -> manager.issueTicket(new Vehicle(v.getLicensePlate(), v.getType())),
                    "Duplicate entry should be rejected for: " + v.getLicensePlate());
        }
    }

    @Test
    @Order(5)
    @DisplayName("Same plate can re-enter after it has exited")
    void duplicate_allowedAfterExit() {
        Vehicle v = DummyDataGenerator.vehicle(VehicleType.CAR);
        Ticket  t = manager.issueTicket(v);

        double fee = manager.previewFee(t.getTicketId());
        manager.processPayment(t.getTicketId(), fee + 10);
        manager.processExit(t.getTicketId());

        // Same plate, new entry — should succeed
        Vehicle returning = new Vehicle(v.getLicensePlate(), VehicleType.CAR);
        assertDoesNotThrow(() -> manager.issueTicket(returning),
                "Same plate should be accepted after it has exited");
    }

    // ── Full lot ──────────────────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("Lot correctly reports full when all spots are taken")
    void fullLot_reportedCorrectly() {
        int total = lot.getTotalSpots();

        // Fill every spot — use a mix that covers all spot types
        List<Vehicle> fleet = new ArrayList<>();
        fleet.addAll(DummyDataGenerator.vehicles(
                lot.getSpotsOnFloor(0).stream()
                        .filter(s -> s.getSpotType().name().equals("MOTORCYCLE")).toList().size(),
                VehicleType.MOTORCYCLE));
        fleet.addAll(DummyDataGenerator.vehicles(
                lot.getSpotsOnFloor(0).stream()
                        .filter(s -> s.getSpotType().name().equals("LARGE")).toList().size(),
                VehicleType.TRUCK));

        // Fill remaining with cars
        int remaining = total - fleet.size();
        fleet.addAll(DummyDataGenerator.vehicles(remaining, VehicleType.CAR));

        // Ensure fleet is large enough; issue up to capacity
        int issued = 0;
        for (Vehicle v : fleet) {
            try {
                manager.issueTicket(v);
                issued++;
            } catch (Exception ignored) { /* lot full or spot mismatch — stop */ }
            if (issued >= total) break;
        }

        assertEquals(0, lot.getAvailableCount(),   "Lot should have 0 available spots");
        assertEquals(total, lot.getOccupiedCount(), "Occupied count should equal total spots");
        assertEquals(100.0, lot.getOccupancyRate(), 0.001, "Occupancy rate should be 100%");
    }

    @Test
    @Order(7)
    @DisplayName("Extra vehicle is rejected when lot is full")
    void fullLot_extraVehicleRejected() {
        int total = lot.getTotalSpots();

        // Pack only cars so we know exactly how many fit
        List<Vehicle> fleet = DummyDataGenerator.vehicles(total + 10, VehicleType.CAR);
        int filled = 0;
        for (Vehicle v : fleet) {
            try { manager.issueTicket(v); filled++; }
            catch (Exception ignored) { break; }
        }

        // At least one vehicle after the lot was full should fail
        boolean rejected = false;
        for (int i = filled; i < fleet.size(); i++) {
            try {
                manager.issueTicket(fleet.get(i));
            } catch (Exception e) {
                rejected = true;
                break;
            }
        }
        assertTrue(rejected, "A vehicle should be rejected when the lot is full");
    }

    // ── Mass payment & exit ───────────────────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("50 vehicles can all pay and exit without errors")
    void mass_50VehiclesPayAndExit() {
        List<Vehicle> fleet   = DummyDataGenerator.vehicles(50);
        List<Ticket>  tickets = new ArrayList<>();
        for (Vehicle v : fleet) tickets.add(manager.issueTicket(v));

        for (Ticket t : tickets) {
            double fee = manager.previewFee(t.getTicketId());
            assertDoesNotThrow(() -> manager.processPayment(t.getTicketId(), fee + 10));
            assertDoesNotThrow(() -> manager.processExit(t.getTicketId()));
        }

        assertEquals(0,  manager.getActiveTickets().size(),  "No tickets should remain active");
        assertEquals(50, manager.getSessionHistory().size(), "All 50 should be in history");
    }

    @Test
    @Order(9)
    @DisplayName("All spots are freed after 50 vehicles exit")
    void mass_spotsFullyRestoredAfterExit() {
        long initial = lot.getAvailableCount();

        List<Vehicle> fleet   = DummyDataGenerator.vehicles(50);
        List<Ticket>  tickets = new ArrayList<>();
        for (Vehicle v : fleet) tickets.add(manager.issueTicket(v));

        assertEquals(initial - 50, lot.getAvailableCount(), "50 spots should be occupied mid-test");

        for (Ticket t : tickets) {
            double fee = manager.previewFee(t.getTicketId());
            manager.processPayment(t.getTicketId(), fee + 1);
            manager.processExit(t.getTicketId());
        }

        assertEquals(initial, lot.getAvailableCount(),
                "All spots must be available again after 50 exits");
        assertEquals(0, lot.getOccupiedCount());
    }

    // ── Revenue accuracy ──────────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("Total revenue equals sum of all individual fees")
    void revenue_sumMatchesTotalRevenue() {
        List<Vehicle> fleet  = DummyDataGenerator.vehicles(20);
        double        sumFees = 0;

        for (Vehicle v : fleet) {
            Ticket t   = manager.issueTicket(v);
            double fee = manager.previewFee(t.getTicketId());
            sumFees += fee;
            manager.processPayment(t.getTicketId(), fee);
            manager.processExit(t.getTicketId());
        }

        assertEquals(sumFees, manager.getTotalRevenue(), 0.01,
                "Total revenue must equal the sum of all charged fees");
    }

    @Test
    @Order(11)
    @DisplayName("Revenue never decreases during a session")
    void revenue_neverDecreases() {
        List<Vehicle> fleet = DummyDataGenerator.vehicles(15);
        double last = manager.getTotalRevenue();

        for (Vehicle v : fleet) {
            Ticket t   = manager.issueTicket(v);
            double fee = manager.previewFee(t.getTicketId());
            manager.processPayment(t.getTicketId(), fee + 5);
            manager.processExit(t.getTicketId());

            double current = manager.getTotalRevenue();
            assertTrue(current >= last,
                    "Revenue decreased unexpectedly from " + last + " to " + current);
            last = current;
        }
    }

    // ── Mixed types ───────────────────────────────────────────────────────

    @Test
    @Order(12)
    @DisplayName("Mixed fleet of cars, motorcycles, and trucks all processed correctly")
    void mixed_allTypesHandledCorrectly() {
        List<Vehicle> cars   = DummyDataGenerator.vehicles(20, VehicleType.CAR);
        List<Vehicle> motos  = DummyDataGenerator.vehicles(15, VehicleType.MOTORCYCLE);
        List<Vehicle> trucks = DummyDataGenerator.vehicles(10, VehicleType.TRUCK);

        List<Vehicle> all = new ArrayList<>();
        all.addAll(cars);
        all.addAll(motos);
        all.addAll(trucks);

        List<Ticket> tickets = new ArrayList<>();
        for (Vehicle v : all) tickets.add(manager.issueTicket(v));

        assertEquals(45, manager.getActiveTickets().size(),
                "All 45 mixed-type vehicles should be active");

        // Pay and exit all
        for (Ticket t : tickets) {
            double fee = manager.previewFee(t.getTicketId());
            manager.processPayment(t.getTicketId(), fee + 1);
            manager.processExit(t.getTicketId());
        }

        assertEquals(0,  manager.getActiveTickets().size());
        assertEquals(45, manager.getSessionHistory().size());
    }

    // ── Seeded reproducibility ────────────────────────────────────────────

    @Test
    @Order(13)
    @DisplayName("Seeded stress run produces the same plate sequence every time")
    void seeded_reproduciblePlateSequence() {
        DummyDataGenerator g1 = DummyDataGenerator.withSeed(2024L);
        DummyDataGenerator g2 = DummyDataGenerator.withSeed(2024L);

        for (int i = 0; i < 50; i++) {
            assertEquals(g1.nextPlate(), g2.nextPlate(),
                    "Plate at index " + i + " must be identical for the same seed");
        }
    }

    // ── Concurrent-style sequential wave ─────────────────────────────────

    @Test
    @Order(14)
    @DisplayName("Two waves of 25 vehicles: first wave exits before second wave enters")
    void wave_twoWavesNoCollision() {
        // Wave 1 — enter
        List<Vehicle> wave1   = DummyDataGenerator.vehicles(25, VehicleType.CAR);
        List<Ticket>  tickets1 = new ArrayList<>();
        for (Vehicle v : wave1) tickets1.add(manager.issueTicket(v));
        assertEquals(25, manager.getActiveTickets().size());

        // Wave 1 — pay and exit
        for (Ticket t : tickets1) {
            double fee = manager.previewFee(t.getTicketId());
            manager.processPayment(t.getTicketId(), fee + 1);
            manager.processExit(t.getTicketId());
        }
        assertEquals(0, manager.getActiveTickets().size());

        // Wave 2 — enter (different plates, different generator call)
        List<Vehicle> wave2   = DummyDataGenerator.vehicles(25, VehicleType.CAR);
        List<Ticket>  tickets2 = new ArrayList<>();
        for (Vehicle v : wave2) tickets2.add(manager.issueTicket(v));
        assertEquals(25, manager.getActiveTickets().size());

        // History should contain all 25 from wave 1
        assertEquals(25, manager.getSessionHistory().size());

        // Pay and exit wave 2
        for (Ticket t : tickets2) {
            double fee = manager.previewFee(t.getTicketId());
            manager.processPayment(t.getTicketId(), fee + 1);
            manager.processExit(t.getTicketId());
        }
        assertEquals(50, manager.getSessionHistory().size(),
                "Total history should contain all 50 vehicles across both waves");
    }
}