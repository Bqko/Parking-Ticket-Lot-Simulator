package com.parking.service;

import com.parking.enums.VehicleType;
import com.parking.model.Ticket;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes analytics and statistics from a list of completed tickets.
 *
 * <p>All methods are stateless and take a ticket list as input,
 * so they can be called on any subset (e.g. today's tickets only).</p>
 */
public class SessionStats {

    // ── Revenue ───────────────────────────────────────────────────────────

    /** Total revenue from a list of tickets. */
    public static double totalRevenue(List<Ticket> tickets) {
        return tickets.stream().mapToDouble(Ticket::getFeeCharged).sum();
    }

    /** Revenue broken down by vehicle type. */
    public static Map<VehicleType, Double> revenueByType(List<Ticket> tickets) {
        return tickets.stream().collect(Collectors.groupingBy(
                t -> t.getVehicle().getType(),
                Collectors.summingDouble(Ticket::getFeeCharged)
        ));
    }

    /** Average fee per completed ticket. */
    public static double averageFee(List<Ticket> tickets) {
        if (tickets.isEmpty()) return 0.0;
        return totalRevenue(tickets) / tickets.size();
    }

    /** Highest single fee charged. */
    public static double maxFee(List<Ticket> tickets) {
        return tickets.stream().mapToDouble(Ticket::getFeeCharged).max().orElse(0.0);
    }

    // ── Duration ──────────────────────────────────────────────────────────

    /** Average parking duration in minutes. */
    public static double averageDurationMinutes(List<Ticket> tickets) {
        List<Ticket> completed = withExitTime(tickets);
        if (completed.isEmpty()) return 0.0;
        return completed.stream()
                .mapToLong(t -> ChronoUnit.MINUTES.between(t.getIssuedAt(), t.getExitTime()))
                .average()
                .orElse(0.0);
    }

    /** Longest single parking session in minutes. */
    public static long maxDurationMinutes(List<Ticket> tickets) {
        return withExitTime(tickets).stream()
                .mapToLong(t -> ChronoUnit.MINUTES.between(t.getIssuedAt(), t.getExitTime()))
                .max().orElse(0);
    }

    // ── Volume ────────────────────────────────────────────────────────────

    /** Count of tickets per vehicle type. */
    public static Map<VehicleType, Long> countByType(List<Ticket> tickets) {
        return tickets.stream().collect(Collectors.groupingBy(
                t -> t.getVehicle().getType(),
                Collectors.counting()
        ));
    }

    /** Number of unique vehicles served. */
    public static long uniqueVehicles(List<Ticket> tickets) {
        return tickets.stream()
                .map(t -> t.getVehicle().getLicensePlate())
                .distinct().count();
    }

    // ── Busiest hour ──────────────────────────────────────────────────────

    /**
     * Returns the hour of day (0–23) with the most entries.
     * Useful for identifying peak times.
     */
    public static int busiestEntryHour(List<Ticket> tickets) {
        return tickets.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getIssuedAt().getHour(),
                        Collectors.counting()
                ))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(-1);
    }

    // ── Vehicle history ───────────────────────────────────────────────────

    /**
     * Returns all tickets for a given license plate, newest first.
     * This powers the "vehicle search" feature in the UI.
     */
    public static List<Ticket> historyForPlate(List<Ticket> allTickets, String plate) {
        String normalizedPlate = plate.trim().toUpperCase();
        return allTickets.stream()
                .filter(t -> t.getVehicle().getLicensePlate().equals(normalizedPlate))
                .sorted(Comparator.comparing(Ticket::getIssuedAt).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Returns tickets issued within the last {@code hours} hours.
     */
    public static List<Ticket> recentTickets(List<Ticket> allTickets, int hours) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(hours);
        return allTickets.stream()
                .filter(t -> t.getIssuedAt().isAfter(cutoff))
                .sorted(Comparator.comparing(Ticket::getIssuedAt).reversed())
                .collect(Collectors.toList());
    }

    // ── Summary string ────────────────────────────────────────────────────

    /**
     * Returns a formatted multi-line summary report string.
     */
    public static String summaryReport(List<Ticket> tickets) {
        if (tickets.isEmpty()) return "No completed sessions to report.";

        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════\n");
        sb.append("       SESSION STATISTICS       \n");
        sb.append("═══════════════════════════════\n");
        sb.append(String.format("  Total sessions     : %d%n",   tickets.size()));
        sb.append(String.format("  Unique vehicles    : %d%n",   uniqueVehicles(tickets)));
        sb.append(String.format("  Total revenue      : $%.2f%n",totalRevenue(tickets)));
        sb.append(String.format("  Average fee        : $%.2f%n",averageFee(tickets)));
        sb.append(String.format("  Highest fee        : $%.2f%n",maxFee(tickets)));
        sb.append(String.format("  Avg duration       : %.0f min%n", averageDurationMinutes(tickets)));
        sb.append(String.format("  Longest session    : %d min%n",maxDurationMinutes(tickets)));

        int busiest = busiestEntryHour(tickets);
        if (busiest >= 0) {
            sb.append(String.format("  Busiest entry hour : %02d:00%n", busiest));
        }

        sb.append("───────────────────────────────\n");
        sb.append("  Revenue by type:\n");
        revenueByType(tickets).forEach((type, rev) ->
                sb.append(String.format("    %-12s : $%.2f%n", type.getDisplayName(), rev)));

        sb.append("  Count by type:\n");
        countByType(tickets).forEach((type, count) ->
                sb.append(String.format("    %-12s : %d%n", type.getDisplayName(), count)));

        sb.append("═══════════════════════════════\n");
        return sb.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static List<Ticket> withExitTime(List<Ticket> tickets) {
        return tickets.stream()
                .filter(t -> t.getExitTime() != null)
                .collect(Collectors.toList());
    }
}