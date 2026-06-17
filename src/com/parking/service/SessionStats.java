package com.parking.service;

import com.parking.model.Ticket;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Stateless utility class for computing aggregate statistics over a
 * collection of completed tickets.
 */
public class SessionStats {

    private SessionStats() {}   // utility class

    // ── Aggregates ────────────────────────────────────────────────────────

    /** Sum of all fees charged across the provided tickets. */
    public static double totalRevenue(List<Ticket> tickets) {
        return tickets.stream()
                .mapToDouble(Ticket::getFeeCharged)
                .sum();
    }

    /** Average fee per ticket, or 0 if the list is empty. */
    public static double averageFee(List<Ticket> tickets) {
        if (tickets.isEmpty()) return 0.0;
        return totalRevenue(tickets) / tickets.size();
    }

    /**
     * Returns the entry hour (0–23) with the most tickets, or -1 if empty.
     */
    public static int busiestEntryHour(List<Ticket> tickets) {
        return tickets.stream()
                .filter(t -> t.getIssuedAt() != null)
                .collect(Collectors.groupingBy(
                        t -> t.getIssuedAt().getHour(),
                        Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(-1);
    }

    /**
     * Returns a 24-element array where index i is the total revenue from
     * tickets whose entry hour was i. Used by the admin panel bar chart.
     */
    public static double[] revenueByHour(List<Ticket> tickets) {
        double[] result = new double[24];
        for (Ticket t : tickets) {
            if (t.getIssuedAt() != null) {
                int hour = t.getIssuedAt().getHour();
                result[hour] += t.getFeeCharged();
            }
        }
        return result;
    }

    /**
     * Returns a formatted multi-line summary report for a session.
     * Used by TicketManager.saveSession() and any other callers that
     * need a human-readable snapshot of the session.
     */
    public static String summaryReport(List<Ticket> tickets) {
        double revenue = totalRevenue(tickets);
        double avg     = averageFee(tickets);
        int    busiest = busiestEntryHour(tickets);
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════\n");
        sb.append("         SESSION SUMMARY\n");
        sb.append("═══════════════════════════════════\n");
        sb.append(String.format("  Total tickets  : %d%n",       tickets.size()));
        sb.append(String.format("  Total revenue  : %.2f%n",     revenue));
        sb.append(String.format("  Average fee    : %.2f%n",     avg));
        sb.append(String.format("  Busiest hour   : %s%n",       busiest >= 0 ? busiest + ":00" : "—"));
        sb.append("───────────────────────────────────\n");
        countByVehicleType(tickets).forEach((type, count) ->
                sb.append(String.format("  %-14s : %d%n", type, count)));
        sb.append("═══════════════════════════════════\n");
        return sb.toString();
    }

    /**
     * Returns a map of vehicle type name → count for the given tickets.
     */
    public static Map<String, Long> countByVehicleType(List<Ticket> tickets) {
        return tickets.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getVehicle().getType().getDisplayName(),
                        Collectors.counting()));
    }
}