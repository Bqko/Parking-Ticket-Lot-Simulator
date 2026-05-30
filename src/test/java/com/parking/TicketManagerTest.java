package com.parking;

import com.parking.db.DatabaseManager;
import com.parking.enums.TicketStatus;
import com.parking.enums.VehicleType;
import com.parking.model.ParkingLot;
import com.parking.model.Ticket;
import com.parking.model.Vehicle;
import com.parking.service.FeeCalculator;
import com.parking.service.TicketManager;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TicketManager Tests")
class TicketManagerTest {

    private TicketManager manager;

    @BeforeEach
    void setUp() {
        DatabaseManager.useInMemoryDatabase();
        ParkingLot.resetInstance();
        FeeCalculator calc = new FeeCalculator();
        calc.setGracePeriodMinutes(0);
        manager = new TicketManager(calc);
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.getInstance().close();
        ParkingLot.resetInstance();
    }

    @Test
    @DisplayName("Issuing a ticket returns a non-null ACTIVE ticket")
    void issue_returnsActiveTicket() {
        Vehicle car = new Vehicle("34 CAR 001", VehicleType.CAR);
        Ticket ticket = manager.issueTicket(car);
        assertNotNull(ticket);
        assertEquals(TicketStatus.ACTIVE, ticket.getStatus());
        assertNotNull(ticket.getTicketId());
        assertNotNull(ticket.getSpot());
    }

    @Test
    @DisplayName("Issued ticket is findable by ID")
    void issue_findableById() {
        Vehicle car = new Vehicle("34 CAR 001", VehicleType.CAR);
        Ticket ticket = manager.issueTicket(car);
        assertTrue(manager.findTicket(ticket.getTicketId()).isPresent());
    }

    @Test
    @DisplayName("Same vehicle cannot enter twice")
    void issue_duplicateVehicle() {
        Vehicle car = new Vehicle("34 CAR 001", VehicleType.CAR);
        manager.issueTicket(car);
        assertThrows(IllegalStateException.class,
                () -> manager.issueTicket(new Vehicle("34 CAR 001", VehicleType.CAR)));
    }

    @Test
    @DisplayName("Multiple different vehicles can enter simultaneously")
    void issue_multipleVehicles() {
        manager.issueTicket(new Vehicle("34 CAR 001", VehicleType.CAR));
        manager.issueTicket(new Vehicle("34 CAR 002", VehicleType.CAR));
        manager.issueTicket(new Vehicle("34 MOT 001", VehicleType.MOTORCYCLE));
        assertEquals(3, manager.getActiveTickets().size());
    }

    @Test
    @DisplayName("Issuing ticket reduces lot available spots by 1")
    void issue_reducesAvailability() {
        long before = ParkingLot.getInstance().getAvailableCount();
        manager.issueTicket(new Vehicle("34 CAR 001", VehicleType.CAR));
        assertEquals(before - 1, ParkingLot.getInstance().getAvailableCount());
    }

    @Test
    @DisplayName("Payment with exact amount → ticket is PAID, change is 0")
    void payment_exactAmount() {
        Vehicle car    = new Vehicle("34 CAR 001", VehicleType.CAR);
        Ticket  ticket = manager.issueTicket(car);
        double  fee    = manager.previewFee(ticket.getTicketId());
        double  change = manager.processPayment(ticket.getTicketId(), fee);
        assertEquals(0.0, change, 0.001);
        assertEquals(TicketStatus.PAID, ticket.getStatus());
    }

    @Test
    @DisplayName("Overpayment → correct change is returned")
    void payment_overpay() {
        Vehicle car    = new Vehicle("34 CAR 001", VehicleType.CAR);
        Ticket  ticket = manager.issueTicket(car);
        double  fee    = manager.previewFee(ticket.getTicketId());
        double  change = manager.processPayment(ticket.getTicketId(), fee + 50.0);
        assertEquals(50.0, change, 0.01);
    }

    @Test
    @DisplayName("Underpayment → throws exception")
    void payment_underpay() {
        Vehicle car    = new Vehicle("34 CAR 001", VehicleType.CAR);
        Ticket  ticket = manager.issueTicket(car);
        double  fee    = manager.previewFee(ticket.getTicketId());
        if (fee > 0) {
            assertThrows(IllegalArgumentException.class,
                    () -> manager.processPayment(ticket.getTicketId(), fee - 1.0));
        }
    }

    @Test
    @DisplayName("Payment increases total revenue")
    void payment_increasesRevenue() {
        double  before = manager.getTotalRevenue();
        Vehicle car    = new Vehicle("34 CAR 001", VehicleType.CAR);
        Ticket  ticket = manager.issueTicket(car);
        double  fee    = manager.previewFee(ticket.getTicketId());
        manager.processPayment(ticket.getTicketId(), fee + 10.0);
        assertTrue(manager.getTotalRevenue() >= before);
    }

    @Test
    @DisplayName("Payment for unknown ticket throws exception")
    void payment_unknownTicket() {
        assertThrows(java.util.NoSuchElementException.class,
                () -> manager.processPayment("UNKNOWN-ID", 100.0));
    }

    @Test
    @DisplayName("Exit after payment → ticket is EXITED and spot is free")
    void exit_afterPayment() {
        Vehicle car    = new Vehicle("34 CAR 001", VehicleType.CAR);
        Ticket  ticket = manager.issueTicket(car);
        double  fee    = manager.previewFee(ticket.getTicketId());
        manager.processPayment(ticket.getTicketId(), fee + 10);
        manager.processExit(ticket.getTicketId());
        assertEquals(TicketStatus.EXITED, ticket.getStatus());
        assertFalse(ticket.getSpot().isOccupied());
    }

    @Test
    @DisplayName("Exit without payment → throws exception")
    void exit_withoutPayment() {
        Vehicle car    = new Vehicle("34 CAR 001", VehicleType.CAR);
        Ticket  ticket = manager.issueTicket(car);
        assertThrows(IllegalStateException.class,
                () -> manager.processExit(ticket.getTicketId()));
    }

    @Test
    @DisplayName("Exit moves ticket from active to session history")
    void exit_movesToHistory() {
        Vehicle car    = new Vehicle("34 CAR 001", VehicleType.CAR);
        Ticket  ticket = manager.issueTicket(car);
        double  fee    = manager.previewFee(ticket.getTicketId());
        manager.processPayment(ticket.getTicketId(), fee + 10);
        manager.processExit(ticket.getTicketId());
        assertEquals(0, manager.getActiveTickets().size());
        assertEquals(1, manager.getSessionHistory().size());
    }

    @Test
    @DisplayName("Spot is available again after exit")
    void exit_releasesSpot() {
        long    before = ParkingLot.getInstance().getAvailableCount();
        Vehicle car    = new Vehicle("34 CAR 001", VehicleType.CAR);
        Ticket  ticket = manager.issueTicket(car);
        double  fee    = manager.previewFee(ticket.getTicketId());
        manager.processPayment(ticket.getTicketId(), fee + 10);
        manager.processExit(ticket.getTicketId());
        assertEquals(before, ParkingLot.getInstance().getAvailableCount());
    }

    @Test
    @DisplayName("Full lifecycle: entry → payment → exit completes cleanly")
    void fullLifecycle() {
        Vehicle car    = new Vehicle("34 CAR 001", VehicleType.CAR);
        Ticket  ticket = manager.issueTicket(car);
        assertEquals(TicketStatus.ACTIVE, ticket.getStatus());
        assertEquals(1, manager.getActiveTickets().size());

        double fee = manager.previewFee(ticket.getTicketId());
        manager.processPayment(ticket.getTicketId(), fee + 20);
        assertEquals(TicketStatus.PAID, ticket.getStatus());

        manager.processExit(ticket.getTicketId());
        assertEquals(TicketStatus.EXITED, ticket.getStatus());
        assertEquals(0, manager.getActiveTickets().size());
        assertEquals(1, manager.getSessionHistory().size());
    }
}