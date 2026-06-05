package com.parking;

import com.parking.db.DatabaseManager;
import com.parking.enums.SpotType;
import com.parking.enums.TicketStatus;
import com.parking.enums.VehicleType;
import com.parking.model.ParkingLot;
import com.parking.model.ParkingSpot;
import com.parking.model.Ticket;
import com.parking.model.Vehicle;
import com.parking.util.DummyDataGenerator;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Ticket Tests")
class TicketTest {

    private Vehicle     car;
    private ParkingSpot spot;
    private Ticket      ticket;

    @BeforeEach
    void setUp() {
        DatabaseManager.useInMemoryDatabase();
        ParkingLot.resetInstance();
        car    = new Vehicle("34 ABC 001", VehicleType.CAR);
        spot   = new ParkingSpot("F0-S01", 0, SpotType.STANDARD);
        spot.park(car);
        ticket = new Ticket(car, spot);
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.getInstance().close();
        ParkingLot.resetInstance();
    }

    // ── Constructor ───────────────────────────────────────────────────────

    @Test
    @DisplayName("New ticket starts as ACTIVE")
    void constructor_statusIsActive() {
        assertEquals(TicketStatus.ACTIVE, ticket.getStatus());
    }

    @Test
    @DisplayName("New ticket has non-null ticket ID")
    void constructor_hasTicketId() {
        assertNotNull(ticket.getTicketId());
        assertFalse(ticket.getTicketId().isBlank());
    }

    @Test
    @DisplayName("New ticket links correct vehicle and spot")
    void constructor_linksVehicleAndSpot() {
        assertEquals(car,  ticket.getVehicle());
        assertEquals(spot, ticket.getSpot());
    }

    @Test
    @DisplayName("New ticket has zero fee and zero paid")
    void constructor_zeroFeeAndPaid() {
        assertEquals(0.0, ticket.getFeeCharged(), 0.001);
        assertEquals(0.0, ticket.getAmountPaid(),  0.001);
    }

    @Test
    @DisplayName("Null vehicle throws exception")
    void constructor_nullVehicle() {
        assertThrows(NullPointerException.class,
                () -> new Ticket(null, spot));
    }

    @Test
    @DisplayName("Null spot throws exception")
    void constructor_nullSpot() {
        assertThrows(NullPointerException.class,
                () -> new Ticket(car, null));
    }

    // ── Payment ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Paying correct amount → status becomes PAID")
    void pay_exactAmount() {
        ticket.pay(50.0, 50.0);
        assertEquals(TicketStatus.PAID, ticket.getStatus());
        assertEquals(50.0, ticket.getFeeCharged(), 0.001);
        assertEquals(0.0,  ticket.getChange(),     0.001);
    }

    @Test
    @DisplayName("Paying more than fee → change is correct")
    void pay_overpay() {
        ticket.pay(30.0, 50.0);
        assertEquals(20.0, ticket.getChange(), 0.001);
    }

    @Test
    @DisplayName("Paying less than fee → throws exception")
    void pay_underpay() {
        assertThrows(IllegalArgumentException.class,
                () -> ticket.pay(50.0, 30.0));
    }

    @Test
    @DisplayName("Paying an already PAID ticket → throws exception")
    void pay_alreadyPaid() {
        ticket.pay(20.0, 20.0);
        assertThrows(IllegalStateException.class,
                () -> ticket.pay(20.0, 20.0));
    }

    @Test
    @DisplayName("isPaid() returns true after payment")
    void isPaid_afterPayment() {
        assertFalse(ticket.isPaid());
        ticket.pay(20.0, 20.0);
        assertTrue(ticket.isPaid());
    }

    // ── Exit ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Closing after payment → status becomes EXITED")
    void closeOnExit_afterPay() {
        ticket.pay(20.0, 20.0);
        ticket.closeOnExit();
        assertEquals(TicketStatus.EXITED, ticket.getStatus());
        assertNotNull(ticket.getExitTime());
    }

    @Test
    @DisplayName("Closing without payment → throws exception")
    void closeOnExit_withoutPay() {
        assertThrows(IllegalStateException.class,
                () -> ticket.closeOnExit());
    }

    // ── Lost ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Marking active ticket as lost → status becomes LOST")
    void markLost_fromActive() {
        ticket.markLost();
        assertEquals(TicketStatus.LOST, ticket.getStatus());
    }

    @Test
    @DisplayName("Marking paid ticket as lost → throws exception")
    void markLost_afterPay() {
        ticket.pay(20.0, 20.0);
        assertThrows(IllegalStateException.class,
                () -> ticket.markLost());
    }

    // ── Receipt ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Receipt string contains ticket ID and plate")
    void receipt_containsKeyInfo() {
        ticket.pay(50.0, 50.0);
        String receipt = ticket.toReceiptString();
        assertTrue(receipt.contains(ticket.getTicketId()));
        assertTrue(receipt.contains("34 ABC 001"));
    }

    // ── DummyDataGenerator integration ───────────────────────────────────

    @Test
    @DisplayName("Ticket created from a generated vehicle is valid")
    void dummy_ticketFromGeneratedVehicle() {
        Vehicle v = DummyDataGenerator.vehicle(VehicleType.CAR);
        ParkingSpot s = new ParkingSpot("F0-S99", 0, SpotType.STANDARD);
        s.park(v);
        Ticket t = new Ticket(v, s);

        assertNotNull(t.getTicketId());
        assertEquals(TicketStatus.ACTIVE, t.getStatus());
        assertEquals(v.getLicensePlate(), t.getVehicle().getLicensePlate());
    }

    @Test
    @DisplayName("Each generated ticket gets a unique ticket ID")
    void dummy_uniqueTicketIds() {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (int i = 0; i < 20; i++) {
            Vehicle v = DummyDataGenerator.vehicle(VehicleType.CAR);
            ParkingSpot s = new ParkingSpot("F0-S" + i, 0, SpotType.STANDARD);
            s.park(v);
            ids.add(new Ticket(v, s).getTicketId());
        }
        assertEquals(20, ids.size(), "Every ticket should have a unique ID");
    }

    @Test
    @DisplayName("Full lifecycle with generated vehicle completes cleanly")
    void dummy_fullLifecycle() {
        Vehicle v = DummyDataGenerator.vehicle(VehicleType.MOTORCYCLE);
        ParkingSpot s = new ParkingSpot("F0-S98", 0, SpotType.MOTORCYCLE);
        s.park(v);
        Ticket t = new Ticket(v, s);

        assertEquals(TicketStatus.ACTIVE, t.getStatus());
        t.pay(15.0, 20.0);
        assertEquals(TicketStatus.PAID, t.getStatus());
        assertEquals(5.0, t.getChange(), 0.001);
        t.closeOnExit();
        assertEquals(TicketStatus.EXITED, t.getStatus());
        assertNotNull(t.getExitTime());
    }
}