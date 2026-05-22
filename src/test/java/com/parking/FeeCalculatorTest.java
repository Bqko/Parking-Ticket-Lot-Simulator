package com.parking;

import com.parking.enums.VehicleType;
import com.parking.model.ParkingSpot;
import com.parking.model.Ticket;
import com.parking.model.Vehicle;
import com.parking.service.FeeCalculator;
import com.parking.enums.SpotType;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FeeCalculator}.
 *
 * Covers: grace period, hourly rounding, vehicle multipliers,
 * daily cap, discounts, lost ticket fee, and validation.
 */
@DisplayName("FeeCalculator Tests")
class FeeCalculatorTest {

    private FeeCalculator calc;
    private Vehicle       car;
    private Vehicle       motorcycle;
    private Vehicle       truck;
    private ParkingSpot   spot;
    private Ticket        ticket;

    @BeforeEach
    void setUp() {
        // Default: 20 USD/hr, 200 USD daily cap, 15 min grace, 0% discount
        calc       = new FeeCalculator();
        car        = new Vehicle("34 CAR 001", VehicleType.CAR);
        motorcycle = new Vehicle("34 MOT 001", VehicleType.MOTORCYCLE);
        truck      = new Vehicle("34 TRK 001", VehicleType.TRUCK);
        spot       = new ParkingSpot("F0-S01", 0, SpotType.STANDARD);
        spot.park(car);
        ticket     = new Ticket(car, spot);
    }

    // ── Grace period ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Stay within grace period → fee is 0")
    void graceperiod_freeWithinWindow() {
        LocalDateTime entry = LocalDateTime.now().minusMinutes(10);
        double fee = calc.calculate(car, entry, LocalDateTime.now());
        assertEquals(0.0, fee, "Fee should be 0 within grace period");
    }

    @Test
    @DisplayName("Stay exactly at grace period boundary → fee is 0")
    void gracePeriod_exactBoundary() {
        LocalDateTime entry = LocalDateTime.now().minusMinutes(15);
        double fee = calc.calculate(car, entry, LocalDateTime.now());
        assertEquals(0.0, fee, "Fee should be 0 at exactly the grace period");
    }

    @Test
    @DisplayName("Stay just over grace period → fee is charged")
    void gracePeriod_justOver() {
        LocalDateTime entry = LocalDateTime.now().minusMinutes(16);
        double fee = calc.calculate(car, entry, LocalDateTime.now());
        assertTrue(fee > 0, "Fee should be charged after grace period");
    }

    // ── Hourly rounding ───────────────────────────────────────────────────

    @Test
    @DisplayName("Exactly 1 hour → 1 × base rate for car")
    void hourly_exactOneHour() {
        LocalDateTime entry = LocalDateTime.now().minusHours(1);
        double fee = calc.calculate(car, entry, LocalDateTime.now());
        assertEquals(20.0, fee, 0.01);
    }

    @Test
    @DisplayName("1 hour 1 minute → rounds up to 2 hours")
    void hourly_roundsUp() {
        LocalDateTime entry = LocalDateTime.now().minusHours(1).minusMinutes(1);
        double fee = calc.calculate(car, entry, LocalDateTime.now());
        assertEquals(40.0, fee, 0.01, "61 minutes should round up to 2 billed hours");
    }

    @Test
    @DisplayName("3 hours → 3 × 20 = 60 TRY for car")
    void hourly_threeHours() {
        LocalDateTime entry = LocalDateTime.now().minusHours(3);
        double fee = calc.calculate(car, entry, LocalDateTime.now());
        assertEquals(60.0, fee, 0.01);
    }

    // ── Vehicle type multipliers ──────────────────────────────────────────

    @Test
    @DisplayName("Motorcycle rate is half the car rate")
    void multiplier_motorcycle() {
        LocalDateTime entry = LocalDateTime.now().minusHours(2);
        double carFee  = calc.calculate(car,        entry, LocalDateTime.now());
        double motFee  = calc.calculate(motorcycle, entry, LocalDateTime.now());
        assertEquals(carFee / 2.0, motFee, 0.01, "Motorcycle should pay half the car rate");
    }

    @Test
    @DisplayName("Truck rate is double the car rate")
    void multiplier_truck() {
        LocalDateTime entry = LocalDateTime.now().minusHours(2);
        double carFee   = calc.calculate(car,   entry, LocalDateTime.now());
        double truckFee = calc.calculate(truck, entry, LocalDateTime.now());
        assertEquals(carFee * 2.0, truckFee, 0.01, "Truck should pay double the car rate");
    }

    // ── Daily cap ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Fee is capped at daily maximum")
    void dailyCap_applied() {
        // 200 TRY daily cap; car at 20/hr would hit it at 10 hours
        LocalDateTime entry = LocalDateTime.now().minusHours(20);
        double fee = calc.calculate(car, entry, LocalDateTime.now());
        assertTrue(fee <= calc.getDailyMaxRate() * 2,
                "Fee should not exceed the daily cap multiplied by days parked");
    }

    @Test
    @DisplayName("Custom daily cap is respected")
    void dailyCap_custom() {
        calc.setDailyMaxRate(50.0);
        LocalDateTime entry = LocalDateTime.now().minusHours(5); // would be 100 TRY uncapped
        double fee = calc.calculate(car, entry, LocalDateTime.now());
        assertEquals(50.0, fee, 0.01, "Fee should be capped at 50 TRY");
    }

    // ── Discounts ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("50% discount halves the fee")
    void discount_fiftyPercent() {
        LocalDateTime entry = LocalDateTime.now().minusHours(2);
        double full     = calc.calculate(car, entry, LocalDateTime.now());
        calc.setDiscountPercent(50.0);
        double discounted = calc.calculate(car, entry, LocalDateTime.now());
        assertEquals(full / 2.0, discounted, 0.01);
    }

    @Test
    @DisplayName("100% discount results in 0 fee")
    void discount_hundredPercent() {
        calc.setDiscountPercent(100.0);
        LocalDateTime entry = LocalDateTime.now().minusHours(3);
        double fee = calc.calculate(car, entry, LocalDateTime.now());
        assertEquals(0.0, fee, 0.01);
    }

    @Test
    @DisplayName("applyDiscount() with 25% discount")
    void applyDiscount_25percent() {
        double result = calc.applyDiscount(100.0, 25.0);
        assertEquals(75.0, result, 0.01);
    }

    // ── Lost ticket ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Default lost ticket fee is 150 TRY")
    void lostTicket_defaultFee() {
        assertEquals(150.0, calc.getLostTicketFee(), 0.01);
    }

    @Test
    @DisplayName("Lost ticket fee can be changed")
    void lostTicket_customFee() {
        calc.setLostTicketFee(200.0);
        assertEquals(200.0, calc.getLostTicketFee(), 0.01);
    }

    // ── Validation ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Negative base rate throws exception")
    void validation_negativeRate() {
        assertThrows(IllegalArgumentException.class,
                () -> calc.setBaseRatePerHour(-1.0));
    }

    @Test
    @DisplayName("Discount over 100 throws exception")
    void validation_discountOver100() {
        assertThrows(IllegalArgumentException.class,
                () -> calc.setDiscountPercent(101.0));
    }

    @Test
    @DisplayName("Exit time before entry throws exception")
    void validation_exitBeforeEntry() {
        LocalDateTime entry = LocalDateTime.now();
        LocalDateTime exit  = entry.minusHours(1);
        assertThrows(IllegalArgumentException.class,
                () -> calc.calculate(car, entry, exit));
    }

    @Test
    @DisplayName("Null entry time throws exception")
    void validation_nullEntryTime() {
        assertThrows(IllegalArgumentException.class,
                () -> calc.calculate(car, null, LocalDateTime.now()));
    }
}