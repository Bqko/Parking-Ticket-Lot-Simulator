package com.parking.service;

import com.parking.db.FeeConfigRepository;
import com.parking.db.TicketRepository;
import com.parking.model.*;
import com.parking.enums.TicketStatus;
import com.parking.util.LicensePlateValidator;

import java.util.*;
import java.util.stream.Collectors;

public class TicketManager {

    private final ParkingLot          parkingLot;
    private final FeeCalculator       feeCalculator;
    private final TicketRepository    ticketRepo;
    private final FeeConfigRepository feeConfigRepo;
    private final Map<String, Ticket> tickets;
    private double totalRevenue;

    public TicketManager() {
        this.parkingLot    = ParkingLot.getInstance();
        this.feeCalculator = new FeeCalculator();
        this.ticketRepo    = new TicketRepository();
        this.feeConfigRepo = new FeeConfigRepository();
        this.tickets       = new LinkedHashMap<>();
        this.totalRevenue  = 0.0;
        restoreFromDatabase();
    }

    public TicketManager(FeeCalculator feeCalculator) {
        this.parkingLot    = ParkingLot.getInstance();
        this.feeCalculator = feeCalculator;
        this.ticketRepo    = new TicketRepository();
        this.feeConfigRepo = new FeeConfigRepository();
        this.tickets       = new LinkedHashMap<>();
        this.totalRevenue  = 0.0;
    }

    // ── Database restore ──────────────────────────────────────────────────

    private void restoreFromDatabase() {
        feeConfigRepo.loadLatest(feeCalculator);
        List<Ticket> all = ticketRepo.findAll();
        for (Ticket t : all) tickets.put(t.getTicketId(), t);
        this.totalRevenue = ticketRepo.totalRevenue();
        if (!all.isEmpty())
            System.out.println("✅ Restored " + all.size() + " tickets from database.");
        ticketRepo.restoreOccupancyFromDb(parkingLot);
    }

    private void exportTicketsToTxt() {
        String fileName = "tickets_log.txt";
        try (java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.FileWriter(fileName))) {
            writer.write("=== ALL TICKETS IN DATABASE ===");
            writer.newLine();
            writer.write("Generated: " + java.time.LocalDateTime.now());
            writer.newLine();
            writer.write("================================");
            writer.newLine();

            List<Ticket> all = ticketRepo.findAll();
            if (all.isEmpty()) {
                writer.write("No tickets found.");
                writer.newLine();
            } else {
                for (Ticket t : all) {
                    writer.write("Ticket ID  : " + t.getTicketId());          writer.newLine();
                    writer.write("Plate      : " + t.getVehicle().getLicensePlate()); writer.newLine();
                    writer.write("Type       : " + t.getVehicle().getType().getDisplayName()); writer.newLine();
                    writer.write("Spot       : " + t.getSpot().getSpotId());  writer.newLine();
                    writer.write("Status     : " + t.getStatus());            writer.newLine();
                    writer.write("Entry      : " + t.getIssuedAt());          writer.newLine();
                    writer.write("Exit       : " + (t.getExitTime() != null ? t.getExitTime() : "Still parked")); writer.newLine();
                    writer.write("Fee        : " + String.format("%.2f", t.getFeeCharged())); writer.newLine();
                    writer.write("Amount paid: " + String.format("%.2f", t.getAmountPaid())); writer.newLine();
                    writer.write("--------------------------------"); writer.newLine();
                }
            }

            writer.write("Total tickets: " + all.size()); writer.newLine();
            writer.write("Total revenue: " + String.format("%.2f", ticketRepo.totalRevenue())); writer.newLine();

            System.out.println("📄 Tickets exported to " + fileName);
        } catch (java.io.IOException e) {
            System.err.println("Could not write tickets log: " + e.getMessage());
        }
    }

    public void saveSession() {
        exportTicketsToTxt();
        System.out.println("💾 Session persisted to database.");
    }

    // ── Entry ─────────────────────────────────────────────────────────────

    public Ticket issueTicket(Vehicle vehicle) {
        Objects.requireNonNull(vehicle, "Vehicle cannot be null.");

        LicensePlateValidator.ValidationResult v =
                LicensePlateValidator.validate(vehicle.getLicensePlate());
        if (!v.isValid()) throw new IllegalArgumentException(v.getError());

        boolean alreadyParked = tickets.values().stream()
                .anyMatch(t -> t.getStatus() == TicketStatus.ACTIVE
                        && t.getVehicle().getLicensePlate().equals(vehicle.getLicensePlate()));
        if (alreadyParked)
            throw new IllegalStateException("Vehicle " + vehicle.getLicensePlate() + " is already parked.");

        ParkingSpot spot   = parkingLot.assignSpot(vehicle);
        Ticket      ticket = new Ticket(vehicle, spot);
        tickets.put(ticket.getTicketId(), ticket);
        ticketRepo.insert(ticket);
        OccupancyObserver.getInstance().check();

        System.out.println("✅ Ticket issued: " + ticket.getTicketId() + " | Spot: " + spot.getSpotId());
        return ticket;
    }

    // ── Payment ───────────────────────────────────────────────────────────

    public double processPayment(String ticketId, double amountPaid) {
        Ticket ticket = getTicketOrThrow(ticketId);
        double fee    = feeCalculator.calculate(ticket);
        ticket.pay(fee, amountPaid);
        totalRevenue += fee;
        ticketRepo.update(ticket);
        ticketRepo.insertPayment(ticket);
        System.out.printf("💳 Payment | Fee: %.2f | Paid: %.2f | Change: %.2f%n",
                fee, amountPaid, ticket.getChange());
        return ticket.getChange();
    }

    public double processLostTicket(String ticketId, double amountPaid) {
        Ticket ticket  = getTicketOrThrow(ticketId);
        double flatFee = feeCalculator.getLostTicketFee();

        ticket.markLost(flatFee, amountPaid);
        parkingLot.releaseSpot(ticket.getSpot());
        totalRevenue += flatFee;
        ticketRepo.update(ticket);
        ticketRepo.insertPayment(ticket);
        OccupancyObserver.getInstance().check();
        System.out.printf("⚠️  Lost ticket fee: %.2f%n", flatFee);
        return ticket.getChange();
    }

    // ── Exit ──────────────────────────────────────────────────────────────

    public void processExit(String ticketId) {
        Ticket ticket = getTicketOrThrow(ticketId);
        if (!ticket.isPaid())
            throw new IllegalStateException("Ticket " + ticketId + " has not been paid.");
        parkingLot.releaseSpot(ticket.getSpot());
        ticket.closeOnExit();
        ticketRepo.update(ticket);
        OccupancyObserver.getInstance().check();
        System.out.println("🚗 Exit: " + ticketId + " | Spot " + ticket.getSpot().getSpotId() + " free.");
    }

    // ── Admin ─────────────────────────────────────────────────────────────

    public void saveFeeConfig() {
        feeConfigRepo.save(feeCalculator);
    }

    // ── Fee preview ───────────────────────────────────────────────────────

    public double previewFee(String ticketId) {
        return feeCalculator.preview(getTicketOrThrow(ticketId));
    }

    // ── Queries ───────────────────────────────────────────────────────────

    public Optional<Ticket> findTicket(String ticketId) {
        if (tickets.containsKey(ticketId)) return Optional.of(tickets.get(ticketId));
        Optional<Ticket> fromDb = ticketRepo.findByCode(ticketId);
        fromDb.ifPresent(t -> tickets.put(t.getTicketId(), t));
        return fromDb;
    }

    public List<Ticket> findByPlate(String licensePlate) {
        return ticketRepo.findByPlate(licensePlate);
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

    public List<Ticket> getAllTickets() {
        return Collections.unmodifiableList(new ArrayList<>(tickets.values()));
    }

    public String getStatsReport()          { return SessionStats.summaryReport(getSessionHistory()); }
    public double getTotalRevenue()         { return totalRevenue; }
    public FeeCalculator getFeeCalculator() { return feeCalculator; }
    public void printSessionReport()        { System.out.println(getStatsReport()); parkingLot.printStatus(); }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Ticket getTicketOrThrow(String ticketId) {
        return findTicket(ticketId)
                .orElseThrow(() -> new NoSuchElementException("Ticket not found: " + ticketId));
    }
}
