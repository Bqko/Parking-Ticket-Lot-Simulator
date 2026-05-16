package com.parking.service;

import com.parking.model.*;
import com.parking.enums.TicketStatus;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Central service that manages the full ticket lifecycle.
 *
 * <p>Acts as the <b>Facade</b> for the entry/exit flow — consumers
 * (e.g. the UI or console) only need to talk to {@code TicketManager};
 * they don't interact with {@code ParkingLot} or {@code FeeCalculator} directly.</p>
 *
 * <h3>Typical flow</h3>
 * <pre>
 *   TicketManager tm = new TicketManager();
 *
 *   // 1. Vehicle arrives
 *   Vehicle v  = new Vehicle("34 ABC 001", VehicleType.CAR);
 *   Ticket  t  = tm.issueTicket(v);
 *
 *   // 2. Vehicle pays and exits
 *   tm.processPayment(t.getTicketId(), 100.0);
 *   tm.processExit(t.getTicketId());
 * </pre>
 */
public class TicketManager {

    // ── Dependencies ──────────────────────────────────────────────────────
    private final ParkingLot    parkingLot;
    private final FeeCalculator feeCalculator;

    // ── In-memory store ───────────────────────────────────────────────────
    /** Active and recent tickets, keyed by ticket ID. */
    private final Map<String, Ticket> tickets;

    // ── Revenue tracking ──────────────────────────────────────────────────
    private double totalRevenue;

    // ── Constructor ───────────────────────────────────────────────────────

    public TicketManager() {
        this.parkingLot    = ParkingLot.getInstance();
        this.feeCalculator = new FeeCalculator();
        this.tickets       = new LinkedHashMap<>();
        this.totalRevenue  = 0.0;
    }

    /** Constructor for dependency injection (e.g., custom rates in tests). */
    public TicketManager(FeeCalculator feeCalculator) {
        this.parkingLot    = ParkingLot.getInstance();
        this.feeCalculator = feeCalculator;
        this.tickets       = new LinkedHashMap<>();
        this.totalRevenue  = 0.0;
    }

    // ── Entry flow ────────────────────────────────────────────────────────

    /**
     * Issues a new ticket for an arriving vehicle.
     *
     * <ol>
     *   <li>Checks that the vehicle is not already parked.</li>
     *   <li>Asks the lot to assign the nearest available spot.</li>
     *   <li>Creates a {@link Ticket} and stores it.</li>
     * </ol>
     *
     * @param vehicle The arriving vehicle.
     * @return        The newly issued ticket.
     * @throws IllegalStateException if the vehicle is already parked or no spot is available.
     */
    public Ticket issueTicket(Vehicle vehicle) {
        Objects.requireNonNull(vehicle, "Vehicle cannot be null.");

        // Prevent duplicate entry
        boolean alreadyParked = tickets.values().stream()
                .anyMatch(t -> t.getStatus() == TicketStatus.ACTIVE
                        && t.getVehicle().getLicensePlate().equals(vehicle.getLicensePlate()));
        if (alreadyParked) {
            throw new IllegalStateException(
                    "Vehicle " + vehicle.getLicensePlate() + " is already parked.");
        }

        ParkingSpot spot   = parkingLot.assignSpot(vehicle);
        Ticket      ticket = new Ticket(vehicle, spot);
        tickets.put(ticket.getTicketId(), ticket);

        System.out.println("✅ Ticket issued: " + ticket.getTicketId()
                + " | Spot: " + spot.getSpotId());
        return ticket;
    }

    // ── Payment flow ──────────────────────────────────────────────────────

    /**
     * Calculates the fee and processes payment for a ticket.
     *
     * @param ticketId    The unique ticket ID (printed on the physical ticket).
     * @param amountPaid  Money handed over by the customer.
     * @return            The change due to the customer.
     * @throws NoSuchElementException   if no ticket with that ID exists.
     * @throws IllegalArgumentException if the amount paid is less than the fee.
     */
    public double processPayment(String ticketId, double amountPaid) {
        Ticket ticket = getTicketOrThrow(ticketId);
        double fee    = feeCalculator.calculate(ticket);

        ticket.pay(fee, amountPaid);
        totalRevenue += fee;

        System.out.printf("💳 Payment processed | Fee: %.2f | Paid: %.2f | Change: %.2f%n",
                fee, amountPaid, ticket.getChange());
        System.out.println(ticket.toReceiptString());
        return ticket.getChange();
    }

    /**
     * Handles a lost ticket: charges a flat fee and allows exit.
     *
     * @param ticketId   The ID reported by the customer.
     * @param amountPaid Money handed over.
     * @return           Change due.
     */
    public double processLostTicket(String ticketId, double amountPaid) {
        Ticket ticket   = getTicketOrThrow(ticketId);
        double flatFee  = feeCalculator.getLostTicketFee();

        ticket.markLost();
        // Re-open as ACTIVE briefly so pay() can transition to PAID
        // (We re-use the pay() guard — in a real system you'd have a dedicated path)
        // Instead, we track this separately:
        totalRevenue += flatFee;

        System.out.printf("⚠️  Lost ticket fee: %.2f TRY%n", flatFee);
        return Math.max(0, amountPaid - flatFee);
    }

    // ── Exit flow ─────────────────────────────────────────────────────────

    /**
     * Validates payment and processes the physical exit of the vehicle.
     * Releases the parking spot so the next vehicle can use it.
     *
     * @param ticketId The ticket ID scanned at the exit gate.
     * @throws IllegalStateException if the ticket has not been paid yet.
     */
    public void processExit(String ticketId) {
        Ticket ticket = getTicketOrThrow(ticketId);

        if (!ticket.isPaid()) {
            throw new IllegalStateException(
                    "Ticket " + ticketId + " has not been paid. Please pay before exiting.");
        }

        parkingLot.releaseSpot(ticket.getSpot());
        ticket.closeOnExit();

        System.out.println("🚗 Exit recorded for ticket: " + ticketId
                + " | Spot " + ticket.getSpot().getSpotId() + " is now free.");
    }

    // ── Fee preview ───────────────────────────────────────────────────────

    /**
     * Returns the current estimated fee for an active ticket without paying.
     * Useful for display kiosks showing "Your current fee is: X TRY".
     */
    public double previewFee(String ticketId) {
        return feeCalculator.preview(getTicketOrThrow(ticketId));
    }

    // ── Queries ───────────────────────────────────────────────────────────

    /** Returns the ticket with the given ID, or empty if not found. */
    public Optional<Ticket> findTicket(String ticketId) {
        return Optional.ofNullable(tickets.get(ticketId));
    }

    /** Returns all tickets that are currently ACTIVE (vehicle still parked). */
    public List<Ticket> getActiveTickets() {
        return tickets.values().stream()
                .filter(t -> t.getStatus() == TicketStatus.ACTIVE)
                .collect(Collectors.toUnmodifiableList());
    }

    /** Returns all completed (EXITED) tickets — the session history. */
    public List<Ticket> getSessionHistory() {
        return tickets.values().stream()
                .filter(t -> t.getStatus() == TicketStatus.EXITED)
                .collect(Collectors.toUnmodifiableList());
    }

    /** Total revenue collected in this session. */
    public double getTotalRevenue() { return totalRevenue; }

    /** Access to the fee calculator for admin configuration. */
    public FeeCalculator getFeeCalculator() { return feeCalculator; }

    // ── Report ────────────────────────────────────────────────────────────

    /** Prints a summary report of the current session. */
    public void printSessionReport() {
        List<Ticket> history = getSessionHistory();
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║         SESSION REPORT               ║");
        System.out.println("╠══════════════════════════════════════╣");
        System.out.printf( "║  Total transactions : %-14d║%n", history.size());
        System.out.printf( "║  Currently parked   : %-14d║%n", getActiveTickets().size());
        System.out.printf( "║  Total revenue      : %-10.2f TRY ║%n", totalRevenue);
        System.out.println("╠══════════════════════════════════════╣");
        parkingLot.printStatus();
        System.out.println("╚══════════════════════════════════════╝");
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private Ticket getTicketOrThrow(String ticketId) {
        return Optional.ofNullable(tickets.get(ticketId))
                .orElseThrow(() -> new NoSuchElementException("Ticket not found: " + ticketId));
    }
}