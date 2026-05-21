package com.parking.service;

import com.parking.db.TicketRepository;
import com.parking.model.*;
import com.parking.enums.TicketStatus;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Central service that manages the full ticket lifecycle.
 * All state changes are persisted to SQLite via TicketRepository.
 */
public class TicketManager {

    // ── Dependencies ──────────────────────────────────────────────────────
    private final ParkingLot      parkingLot;
    private final FeeCalculator   feeCalculator;
    private final TicketRepository repo;

    // ── In-memory store ───────────────────────────────────────────────────
    private final Map<String, Ticket> tickets;

    // ── Revenue tracking ──────────────────────────────────────────────────
    private double totalRevenue;

    // ── Constructor ───────────────────────────────────────────────────────

    public TicketManager() {
        this.parkingLot    = ParkingLot.getInstance();
        this.feeCalculator = new FeeCalculator();
        this.tickets       = new LinkedHashMap<>();
        this.repo          = new TicketRepository();
        this.totalRevenue  = loadPersistedRevenue();
        loadPersistedFeeConfig();
    }

    public TicketManager(FeeCalculator feeCalculator) {
        this.parkingLot    = ParkingLot.getInstance();
        this.feeCalculator = feeCalculator;
        this.tickets       = new LinkedHashMap<>();
        this.repo          = new TicketRepository();
        this.totalRevenue  = loadPersistedRevenue();
    }

    // ── Entry flow ────────────────────────────────────────────────────────

    public Ticket issueTicket(Vehicle vehicle) {
        Objects.requireNonNull(vehicle, "Vehicle cannot be null.");

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

        // Persist to DB
        try {
            int customerDbId = repo.upsertCustomer(vehicle);
            int spotDbId     = repo.occupySpot(spot);
            repo.insertTicket(ticket, customerDbId, spotDbId);
        } catch (SQLException e) {
            System.err.println("⚠ DB error on issueTicket: " + e.getMessage());
        }

        System.out.println("✅ Ticket issued: " + ticket.getTicketId()
                + " | Spot: " + spot.getSpotId());
        return ticket;
    }

    // ── Payment flow ──────────────────────────────────────────────────────

    public double processPayment(String ticketId, double amountPaid) {
        Ticket ticket = getTicketOrThrow(ticketId);
        double fee    = feeCalculator.calculate(ticket);

        ticket.pay(fee, amountPaid);
        totalRevenue += fee;

        // Persist payment + update ticket row
        try {
            repo.updateTicketOnPayment(ticket);
            repo.insertPayment(ticket);
        } catch (SQLException e) {
            System.err.println("⚠ DB error on processPayment: " + e.getMessage());
        }

        System.out.printf("💳 Payment processed | Fee: %.2f | Paid: %.2f | Change: %.2f%n",
                fee, amountPaid, ticket.getChange());
        return ticket.getChange();
    }

    public double processLostTicket(String ticketId, double amountPaid) {
        Ticket ticket  = getTicketOrThrow(ticketId);
        double flatFee = feeCalculator.getLostTicketFee();

        ticket.markLost();
        totalRevenue += flatFee;

        // Persist lost status
        try {
            repo.updateTicketLost(ticketId);
        } catch (SQLException e) {
            System.err.println("⚠ DB error on processLostTicket: " + e.getMessage());
        }

        System.out.printf("⚠️  Lost ticket fee: %.2f%n", flatFee);
        return Math.max(0, amountPaid - flatFee);
    }

    // ── Exit flow ─────────────────────────────────────────────────────────

    public void processExit(String ticketId) {
        Ticket ticket = getTicketOrThrow(ticketId);

        if (!ticket.isPaid()) {
            throw new IllegalStateException(
                    "Ticket " + ticketId + " has not been paid. Please pay before exiting.");
        }

        parkingLot.releaseSpot(ticket.getSpot());
        ticket.closeOnExit();

        // Persist exit + release spot
        try {
            repo.updateTicketOnExit(ticket);
            repo.releaseSpot(ticket.getSpot());
        } catch (SQLException e) {
            System.err.println("⚠ DB error on processExit: " + e.getMessage());
        }

        System.out.println("🚗 Exit recorded for ticket: " + ticketId
                + " | Spot " + ticket.getSpot().getSpotId() + " is now free.");
    }

    // ── Fee preview ───────────────────────────────────────────────────────

    public double previewFee(String ticketId) {
        return feeCalculator.preview(getTicketOrThrow(ticketId));
    }

    // ── Queries ───────────────────────────────────────────────────────────

    public Optional<Ticket> findTicket(String ticketId) {
        return Optional.ofNullable(tickets.get(ticketId));
    }

    public List<Ticket> getActiveTickets() {
        return tickets.values().stream()
                .filter(t -> t.getStatus() == TicketStatus.ACTIVE)
                .collect(Collectors.toUnmodifiableList());
    }

    public List<Ticket> getSessionHistory() {
        return tickets.values().stream()
                .filter(t -> t.getStatus() == TicketStatus.EXITED)
                .collect(Collectors.toUnmodifiableList());
    }

    public double getTotalRevenue() { return totalRevenue; }

    public FeeCalculator getFeeCalculator() { return feeCalculator; }

    // ── Admin: save fee config ────────────────────────────────────────────

    /**
     * Called by AdminScreen after the operator saves new pricing.
     * Persists the config so it survives app restarts.
     */
    public void persistFeeConfig() {
        try {
            repo.saveFeeConfig(
                    feeCalculator.getBaseRatePerHour(),
                    feeCalculator.getDailyMaxRate(),
                    feeCalculator.getLostTicketFee(),
                    feeCalculator.getGracePeriodMinutes(),
                    feeCalculator.getDiscountPercent()
            );
        } catch (SQLException e) {
            System.err.println("⚠ DB error saving fee config: " + e.getMessage());
        }
    }

    // ── Session report ────────────────────────────────────────────────────

    public void printSessionReport() {
        List<Ticket> history = getSessionHistory();
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║         SESSION REPORT               ║");
        System.out.println("╠══════════════════════════════════════╣");
        System.out.printf( "║  Total transactions : %-14d║%n", history.size());
        System.out.printf( "║  Currently parked   : %-14d║%n", getActiveTickets().size());
        System.out.printf( "║  Total revenue      : %-10.2f USD ║%n", totalRevenue);
        System.out.println("╠══════════════════════════════════════╣");
        parkingLot.printStatus();
        System.out.println("╚══════════════════════════════════════╝");
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private Ticket getTicketOrThrow(String ticketId) {
        return Optional.ofNullable(tickets.get(ticketId))
                .orElseThrow(() -> new NoSuchElementException("Ticket not found: " + ticketId));
    }

    /** Loads total revenue from DB on startup so the dashboard is correct after restarts. */
    private double loadPersistedRevenue() {
        try {
            return repo.loadTotalRevenue();
        } catch (SQLException e) {
            System.err.println("⚠ Could not load persisted revenue: " + e.getMessage());
            return 0.0;
        }
    }

    /** Loads the last saved fee config from DB and applies it to FeeCalculator. */
    private void loadPersistedFeeConfig() {
        try {
            double[] cfg = repo.loadLatestFeeConfig();
            if (cfg != null) {
                feeCalculator.setBaseRatePerHour(cfg[0]);
                feeCalculator.setDailyMaxRate(cfg[1]);
                feeCalculator.setLostTicketFee(cfg[2]);
                feeCalculator.setGracePeriodMinutes((int) cfg[3]);
                feeCalculator.setDiscountPercent(cfg[4]);
                System.out.println("✅ Fee config loaded from DB.");
            }
        } catch (SQLException e) {
            System.err.println("⚠ Could not load fee config: " + e.getMessage());
        }
    }
}
