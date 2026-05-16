package com.parking.model;

import com.parking.enums.TicketStatus;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a parking ticket issued when a vehicle enters the lot.
 *
 * <p>A ticket links a {@link Vehicle} to a {@link ParkingSpot} and records
 * the full lifecycle of a parking session: entry, payment, and exit.
 * All timestamps and the fee are set by service classes, not by this class
 * itself (Single Responsibility Principle).</p>
 */
public class Ticket {

    // ── Fields ────────────────────────────────────────────────────────────
    private final String         ticketId;       // UUID, printed on physical ticket
    private final Vehicle        vehicle;        // The parked vehicle
    private final ParkingSpot    spot;           // Assigned spot
    private final LocalDateTime  issuedAt;       // When ticket was created

    private LocalDateTime        exitTime;       // Set on exit
    private double               feeCharged;     // Calculated fee in local currency
    private double               amountPaid;     // What the customer handed over
    private TicketStatus         status;         // ACTIVE → PAID → EXITED

    // ── Constructor ───────────────────────────────────────────────────────

    /**
     * Creates a fresh ticket with a random UUID.
     * Status starts as ACTIVE; fee and exit time are unset.
     */
    public Ticket(Vehicle vehicle, ParkingSpot spot) {
        this.ticketId    = UUID.randomUUID().toString().toUpperCase().replace("-", "").substring(0, 10);
        this.vehicle     = Objects.requireNonNull(vehicle, "Vehicle cannot be null.");
        this.spot        = Objects.requireNonNull(spot,    "Spot cannot be null.");
        this.issuedAt    = LocalDateTime.now();
        this.status      = TicketStatus.ACTIVE;
        this.feeCharged  = 0.0;
        this.amountPaid  = 0.0;
    }

    // ── Payment / exit flow ───────────────────────────────────────────────

    /**
     * Records payment for this ticket.
     *
     * @param fee    Total fee calculated by {@code FeeCalculator}.
     * @param amount Amount of money actually paid by the customer.
     * @throws IllegalStateException    if the ticket is not ACTIVE.
     * @throws IllegalArgumentException if the paid amount is less than the fee.
     */
    public void pay(double fee, double amount) {
        if (status != TicketStatus.ACTIVE) {
            throw new IllegalStateException("Cannot pay a ticket that is not ACTIVE. Current status: " + status);
        }
        if (amount < fee) {
            throw new IllegalArgumentException(
                    String.format("Insufficient payment. Fee=%.2f, Paid=%.2f", fee, amount));
        }
        this.feeCharged = fee;
        this.amountPaid = amount;
        this.status     = TicketStatus.PAID;
    }

    /**
     * Closes the ticket when the vehicle physically exits the lot.
     *
     * @throws IllegalStateException if the ticket has not been paid yet.
     */
    public void closeOnExit() {
        if (status != TicketStatus.PAID) {
            throw new IllegalStateException("Vehicle cannot exit without paying first.");
        }
        this.exitTime = LocalDateTime.now();
        this.status   = TicketStatus.EXITED;
    }

    /**
     * Marks the ticket as lost. A flat lost-ticket fee is applied
     * by the caller (e.g. TicketManager) before calling this.
     */
    public void markLost() {
        if (status != TicketStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE tickets can be marked lost.");
        }
        this.status = TicketStatus.LOST;
    }

    /**
     * Returns the change due to the customer (amount paid − fee charged).
     */
    public double getChange() {
        return Math.max(0, amountPaid - feeCharged);
    }

    /**
     * Returns true if this ticket has been paid and the vehicle may exit.
     */
    public boolean isPaid() {
        return status == TicketStatus.PAID || status == TicketStatus.EXITED;
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public String        getTicketId()   { return ticketId; }
    public Vehicle       getVehicle()    { return vehicle; }
    public ParkingSpot   getSpot()       { return spot; }
    public LocalDateTime getIssuedAt()   { return issuedAt; }
    public LocalDateTime getExitTime()   { return exitTime; }
    public double        getFeeCharged() { return feeCharged; }
    public double        getAmountPaid() { return amountPaid; }
    public TicketStatus  getStatus()     { return status; }

    // ── Object overrides ──────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Ticket)) return false;
        Ticket t = (Ticket) o;
        return Objects.equals(ticketId, t.ticketId);
    }

    @Override
    public int hashCode() { return Objects.hash(ticketId); }

    @Override
    public String toString() {
        return String.format(
                "Ticket{id='%s', plate='%s', spot='%s', status=%s, fee=%.2f}",
                ticketId, vehicle.getLicensePlate(), spot.getSpotId(), status, feeCharged);
    }

    /**
     * Returns a formatted receipt string suitable for printing to the console.
     */
    public String toReceiptString() {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════╗\n");
        sb.append("║      PARKING LOT RECEIPT     ║\n");
        sb.append("╠══════════════════════════════╣\n");
        sb.append(String.format("║ Ticket ID : %-17s║\n", ticketId));
        sb.append(String.format("║ Plate     : %-17s║\n", vehicle.getLicensePlate()));
        sb.append(String.format("║ Type      : %-17s║\n", vehicle.getType().getDisplayName()));
        sb.append(String.format("║ Spot      : %-17s║\n", spot.getSpotId()));
        sb.append(String.format("║ Entry     : %-17s║\n", issuedAt.toLocalTime()));
        if (exitTime != null) {
            sb.append(String.format("║ Exit      : %-17s║\n", exitTime.toLocalTime()));
        }
        sb.append("╠══════════════════════════════╣\n");
        sb.append(String.format("║ Fee       : %10.2f TRY   ║\n", feeCharged));
        sb.append(String.format("║ Paid      : %10.2f TRY   ║\n", amountPaid));
        sb.append(String.format("║ Change    : %10.2f TRY   ║\n", getChange()));
        sb.append("╠══════════════════════════════╣\n");
        sb.append(String.format("║ Status    : %-17s║\n", status));
        sb.append("╚══════════════════════════════╝\n");
        return sb.toString();
    }
}