package com.parking.service;

import com.parking.model.ParkingLot;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements the <b>Observer</b> pattern for parking lot occupancy events.
 *
 * <p>Components (e.g. the UI dashboard) register as listeners and get
 * notified automatically when the lot reaches certain thresholds —
 * without polling or tight coupling to {@link ParkingLot}.</p>
 *
 * <h3>Usage</h3>
 * <pre>
 *   OccupancyObserver.getInstance().addListener(event -> {
 *       System.out.println("Event: " + event.getType() + " — " + event.getMessage());
 *   });
 *
 *   // Call after every entry/exit:
 *   OccupancyObserver.getInstance().check();
 * </pre>
 */
public class OccupancyObserver {

    // ── Singleton ─────────────────────────────────────────────────────────
    private static OccupancyObserver instance;

    public static OccupancyObserver getInstance() {
        if (instance == null) instance = new OccupancyObserver();
        return instance;
    }

    // ── Event types ───────────────────────────────────────────────────────

    public enum EventType {
        LOT_FULL,           // 100% occupied
        LOT_ALMOST_FULL,    // ≥ 90% occupied
        LOT_BUSY,           // ≥ 75% occupied
        LOT_AVAILABLE,      // dropped back below 75%
        LOT_EMPTY           // 0% occupied
    }

    // ── Event ─────────────────────────────────────────────────────────────

    public static class OccupancyEvent {
        private final EventType type;
        private final double    occupancyRate;
        private final long      availableSpots;
        private final String    message;

        OccupancyEvent(EventType type, double rate, long available) {
            this.type           = type;
            this.occupancyRate  = rate;
            this.availableSpots = available;
            this.message = switch (type) {
                case LOT_FULL        -> "⛔  Lot is FULL. No spots available.";
                case LOT_ALMOST_FULL -> String.format("⚠️  Lot is almost full — only %d spots left (%.0f%% occupied).", available, rate);
                case LOT_BUSY        -> String.format("🟡  Lot is busy — %d spots remaining (%.0f%% occupied).", available, rate);
                case LOT_AVAILABLE   -> String.format("✅  Lot has space — %d spots available (%.0f%% occupied).", available, rate);
                case LOT_EMPTY       -> "🟢  Lot is empty.";
            };
        }

        public EventType getType()           { return type; }
        public double    getOccupancyRate()  { return occupancyRate; }
        public long      getAvailableSpots() { return availableSpots; }
        public String    getMessage()        { return message; }
    }

    // ── Listener interface ────────────────────────────────────────────────

    @FunctionalInterface
    public interface OccupancyListener {
        void onOccupancyEvent(OccupancyEvent event);
    }

    // ── Fields ────────────────────────────────────────────────────────────
    private final List<OccupancyListener> listeners = new ArrayList<>();
    private EventType lastEventType = null; // avoid firing the same event twice

    // ── Registration ──────────────────────────────────────────────────────

    public void addListener(OccupancyListener listener) {
        listeners.add(listener);
    }

    public void removeListener(OccupancyListener listener) {
        listeners.remove(listener);
    }

    public void clearListeners() {
        listeners.clear();
    }

    // ── Check and notify ─────────────────────────────────────────────────

    /**
     * Reads current lot occupancy and fires an event if the threshold
     * category has changed since the last check.
     *
     * Call this after every {@code issueTicket()} and {@code processExit()}.
     */
    public void check() {
        ParkingLot lot  = ParkingLot.getInstance();
        double     rate = lot.getOccupancyRate();
        long       avail = lot.getAvailableCount();

        EventType current = classify(rate);
        if (current == lastEventType) return; // no change — don't re-fire

        lastEventType = current;
        OccupancyEvent event = new OccupancyEvent(current, rate, avail);
        for (OccupancyListener l : listeners) {
            l.onOccupancyEvent(event);
        }
    }

    /**
     * Force-fires an event regardless of whether the state changed.
     * Useful on app startup to set initial state.
     */
    public void forceCheck() {
        lastEventType = null;
        check();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private EventType classify(double rate) {
        if (rate >= 100.0) return EventType.LOT_FULL;
        if (rate >= 90.0)  return EventType.LOT_ALMOST_FULL;
        if (rate >= 75.0)  return EventType.LOT_BUSY;
        if (rate == 0.0)   return EventType.LOT_EMPTY;
        return EventType.LOT_AVAILABLE;
    }
}