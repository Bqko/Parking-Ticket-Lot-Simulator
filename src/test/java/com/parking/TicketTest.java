package com.parking;

import com.parking.enums.SpotType;
import com.parking.enums.TicketStatus;
import com.parking.enums.VehicleType;
import com.parking.model.ParkingSpot;
import com.parking.model.Ticket;
import com.parking.model.Vehicle;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Ticket Tests")
class TicketTest {

    private Vehicle     car;
    private ParkingSpot spot;
    private Ticket      ticket;

    @BeforeEach
    void setUp() {
        car    = new Vehicle("34 ABC 001", VehicleType.CAR);
        spot   = new ParkingSpot("F0-S01", 0, SpotType.STANDARD);
        spot.park(car);
        ticket = new Ticket(car, spot);
    }

    // ── Construction ──────────────────────────────────────────────────────

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

    // ── Lost ticket ───────────────────────────────────────────────────────

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
}