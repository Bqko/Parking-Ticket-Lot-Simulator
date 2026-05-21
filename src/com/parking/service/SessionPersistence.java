package com.parking.service;

import com.parking.enums.SpotType;
import com.parking.enums.TicketStatus;
import com.parking.enums.VehicleType;
import com.parking.model.ParkingSpot;
import com.parking.model.Ticket;
import com.parking.model.Vehicle;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Saves and loads the ticket session to/from a JSON file.
 *
 * <p>Uses only the Java standard library — no external JSON library needed.
 * The JSON is hand-written and hand-parsed, keeping the project dependency-free.</p>
 *
 * <p>Call {@link #save(List, double)} when the app closes and
 * {@link #load()} when it starts to restore the previous session.</p>
 *
 * <h3>File location</h3>
 * Saved to {@code parking_session.json} in the working directory
 * (the project root in IntelliJ).
 */
public class SessionPersistence {

    private static final String FILE_NAME  = "parking_session.json";
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // ── Save ──────────────────────────────────────────────────────────────

    /**
     * Serializes all tickets and the total revenue to JSON and writes to disk.
     *
     * @param tickets      All tickets (active + history) from TicketManager.
     * @param totalRevenue Total revenue for the session.
     */
    public static void save(List<Ticket> tickets, double totalRevenue) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"savedAt\": \"").append(LocalDateTime.now().format(FMT)).append("\",\n");
        json.append("  \"totalRevenue\": ").append(String.format("%.2f", totalRevenue)).append(",\n");
        json.append("  \"tickets\": [\n");

        for (int i = 0; i < tickets.size(); i++) {
            Ticket t = tickets.get(i);
            json.append("    {\n");
            json.append("      \"ticketId\": \"").append(esc(t.getTicketId())).append("\",\n");
            json.append("      \"status\": \"").append(t.getStatus().name()).append("\",\n");
            json.append("      \"feeCharged\": ").append(String.format("%.2f", t.getFeeCharged())).append(",\n");
            json.append("      \"amountPaid\": ").append(String.format("%.2f", t.getAmountPaid())).append(",\n");
            json.append("      \"issuedAt\": \"").append(t.getIssuedAt().format(FMT)).append("\",\n");
            json.append("      \"exitTime\": ").append(
                    t.getExitTime() != null ? "\"" + t.getExitTime().format(FMT) + "\"" : "null"
            ).append(",\n");

            // Vehicle
            Vehicle v = t.getVehicle();
            json.append("      \"vehicle\": {\n");
            json.append("        \"licensePlate\": \"").append(esc(v.getLicensePlate())).append("\",\n");
            json.append("        \"type\": \"").append(v.getType().name()).append("\",\n");
            json.append("        \"entryTime\": \"").append(v.getEntryTime().format(FMT)).append("\"\n");
            json.append("      },\n");

            // Spot
            ParkingSpot s = t.getSpot();
            json.append("      \"spot\": {\n");
            json.append("        \"spotId\": \"").append(esc(s.getSpotId())).append("\",\n");
            json.append("        \"floor\": ").append(s.getFloor()).append(",\n");
            json.append("        \"spotType\": \"").append(s.getSpotType().name()).append("\"\n");
            json.append("      }\n");

            json.append("    }").append(i < tickets.size() - 1 ? "," : "").append("\n");
        }

        json.append("  ]\n");
        json.append("}\n");

        try {
            Files.writeString(Path.of(FILE_NAME), json.toString());
            System.out.println("💾 Session saved to " + FILE_NAME
                    + " (" + tickets.size() + " tickets)");
        } catch (IOException e) {
            System.err.println("⚠️  Could not save session: " + e.getMessage());
        }
    }

    // ── Load ──────────────────────────────────────────────────────────────

    /**
     * Loads a previously saved session from disk.
     *
     * @return {@link LoadResult} with the restored tickets and revenue,
     *         or an empty result if no file exists.
     */
    public static LoadResult load() {
        Path path = Path.of(FILE_NAME);
        if (!Files.exists(path)) {
            System.out.println("ℹ️  No saved session found — starting fresh.");
            return LoadResult.empty();
        }

        try {
            String json = Files.readString(path);
            return parse(json);
        } catch (Exception e) {
            System.err.println("⚠️  Could not load session: " + e.getMessage());
            return LoadResult.empty();
        }
    }

    /**
     * Deletes the saved session file.
     */
    public static void deleteSave() {
        try { Files.deleteIfExists(Path.of(FILE_NAME)); }
        catch (IOException ignored) {}
    }

    public static boolean hasSave() {
        return Files.exists(Path.of(FILE_NAME));
    }

    // ── Parser ────────────────────────────────────────────────────────────

    private static LoadResult parse(String json) {
        List<Ticket>   tickets      = new ArrayList<>();
        double         totalRevenue = 0.0;

        // Extract totalRevenue
        String revStr = extractValue(json, "totalRevenue");
        if (revStr != null) {
            try { totalRevenue = Double.parseDouble(revStr.trim()); }
            catch (NumberFormatException ignored) {}
        }

        // Extract each ticket block
        int start = json.indexOf("\"tickets\"");
        if (start < 0) return new LoadResult(tickets, totalRevenue);

        // Split on ticket objects
        String ticketsSection = json.substring(start);
        String[] blocks = ticketsSection.split("\\{");

        for (String block : blocks) {
            if (!block.contains("ticketId")) continue;
            try {
                Ticket t = parseTicket("{" + block.replaceAll(",?\\s*\\}[^}]*$", "}"));
                if (t != null) tickets.add(t);
            } catch (Exception ignored) {}
        }

        System.out.println("📂 Session loaded: " + tickets.size() + " tickets restored.");
        return new LoadResult(tickets, totalRevenue);
    }

    private static Ticket parseTicket(String block) {
        try {
            // Vehicle block
            int vStart = block.indexOf("\"vehicle\"");
            int vEnd   = block.indexOf("}", vStart) + 1;
            String vBlock = block.substring(vStart, vEnd);

            String plate     = extractString(vBlock, "licensePlate");
            String typeStr   = extractString(vBlock, "type");
            String entryStr  = extractString(vBlock, "entryTime");

            if (plate == null || typeStr == null || entryStr == null) return null;

            VehicleType vehicleType = VehicleType.valueOf(typeStr);
            LocalDateTime entryTime = LocalDateTime.parse(entryStr, FMT);
            Vehicle vehicle = new Vehicle(plate, vehicleType, entryTime);

            // Spot block
            int sStart = block.indexOf("\"spot\"");
            int sEnd   = block.indexOf("}", sStart) + 1;
            String sBlock = block.substring(sStart, sEnd);

            String spotId    = extractString(sBlock, "spotId");
            String floorStr  = extractValue(sBlock,  "floor");
            String spotType  = extractString(sBlock, "spotType");

            if (spotId == null || floorStr == null || spotType == null) return null;

            int         floor = Integer.parseInt(floorStr.trim());
            ParkingSpot spot  = new ParkingSpot(spotId, floor, SpotType.valueOf(spotType));

            // Ticket fields
            String issuedStr  = extractString(block, "issuedAt");
            String statusStr  = extractString(block, "status");
            String feeStr     = extractValue(block,  "feeCharged");
            String paidStr    = extractValue(block,  "amountPaid");
            String exitStr    = extractString(block, "exitTime");

            if (issuedStr == null || statusStr == null) return null;

            // Reconstruct ticket using reflection on issuedAt
            // (We create the vehicle with the original entryTime already set above)
            Ticket ticket = new Ticket(vehicle, spot);

            // Restore state based on status
            TicketStatus status = TicketStatus.valueOf(statusStr);
            double fee  = feeStr  != null ? Double.parseDouble(feeStr.trim())  : 0.0;
            double paid = paidStr != null ? Double.parseDouble(paidStr.trim()) : 0.0;

            if (status == TicketStatus.PAID || status == TicketStatus.EXITED) {
                if (paid >= fee) ticket.pay(fee, paid);
            }
            if (status == TicketStatus.EXITED) {
                ticket.closeOnExit();
            }
            if (status == TicketStatus.LOST) {
                ticket.markLost();
            }

            return ticket;

        } catch (Exception e) {
            return null; // skip malformed ticket
        }
    }

    // ── JSON helpers (no external library) ───────────────────────────────

    /** Extracts a string value (with quotes) for the given key. */
    private static String extractString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colon = json.indexOf(":", idx) + 1;
        // Skip whitespace
        while (colon < json.length() && Character.isWhitespace(json.charAt(colon))) colon++;
        if (colon >= json.length()) return null;
        if (json.charAt(colon) == '"') {
            int start = colon + 1;
            int end = json.indexOf("\"", start);
            return end > start ? json.substring(start, end) : null;
        }
        if (json.startsWith("null", colon)) return null;
        return null;
    }

    /** Extracts a raw (unquoted) value for the given key. */
    private static String extractValue(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colon = json.indexOf(":", idx) + 1;
        while (colon < json.length() && Character.isWhitespace(json.charAt(colon))) colon++;
        int end = colon;
        while (end < json.length() && ",\n}".indexOf(json.charAt(end)) < 0) end++;
        return json.substring(colon, end).trim();
    }

    /** Escapes special characters for JSON string values. */
    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ── LoadResult ────────────────────────────────────────────────────────

    public static class LoadResult {
        private final List<Ticket> tickets;
        private final double       totalRevenue;
        private final boolean      hasData;

        LoadResult(List<Ticket> tickets, double revenue) {
            this.tickets      = tickets;
            this.totalRevenue = revenue;
            this.hasData      = !tickets.isEmpty();
        }

        static LoadResult empty() {
            return new LoadResult(new ArrayList<>(), 0.0);
        }

        public List<Ticket> getTickets()      { return tickets; }
        public double       getTotalRevenue() { return totalRevenue; }
        public boolean      hasData()         { return hasData; }
    }
}