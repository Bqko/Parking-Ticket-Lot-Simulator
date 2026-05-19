package com.parking;

import com.parking.enums.VehicleType;
import com.parking.model.Vehicle;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Vehicle Tests")
class VehicleTest {

    // ── Construction ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Valid vehicle is created with correct fields")
    void constructor_validInput() {
        Vehicle v = new Vehicle("34 ABC 001", VehicleType.CAR);
        assertEquals("34 ABC 001", v.getLicensePlate());
        assertEquals(VehicleType.CAR, v.getType());
        assertNotNull(v.getEntryTime());
    }

    @Test
    @DisplayName("License plate is trimmed and uppercased")
    void constructor_plateNormalized() {
        Vehicle v = new Vehicle("  34 abc 001  ", VehicleType.CAR);
        assertEquals("34 ABC 001", v.getLicensePlate());
    }

    @Test
    @DisplayName("Custom entry time is stored correctly")
    void constructor_customEntryTime() {
        LocalDateTime time = LocalDateTime.of(2025, 6, 1, 10, 0);
        Vehicle v = new Vehicle("34 ABC 001", VehicleType.CAR, time);
        assertEquals(time, v.getEntryTime());
    }

    @Test
    @DisplayName("Null license plate throws exception")
    void constructor_nullPlate() {
        assertThrows(IllegalArgumentException.class,
                () -> new Vehicle(null, VehicleType.CAR));
    }

    @Test
    @DisplayName("Blank license plate throws exception")
    void constructor_blankPlate() {
        assertThrows(IllegalArgumentException.class,
                () -> new Vehicle("   ", VehicleType.CAR));
    }

    @Test
    @DisplayName("Null vehicle type throws exception")
    void constructor_nullType() {
        assertThrows(IllegalArgumentException.class,
                () -> new Vehicle("34 ABC 001", null));
    }

    // ── Rate multiplier ───────────────────────────────────────────────────

    @Test
    @DisplayName("Car rate multiplier is 1.0")
    void multiplier_car() {
        Vehicle v = new Vehicle("34 CAR 001", VehicleType.CAR);
        assertEquals(1.0, v.getRateMultiplier(), 0.001);
    }

    @Test
    @DisplayName("Motorcycle rate multiplier is 0.5")
    void multiplier_motorcycle() {
        Vehicle v = new Vehicle("34 MOT 001", VehicleType.MOTORCYCLE);
        assertEquals(0.5, v.getRateMultiplier(), 0.001);
    }

    @Test
    @DisplayName("Truck rate multiplier is 2.0")
    void multiplier_truck() {
        Vehicle v = new Vehicle("34 TRK 001", VehicleType.TRUCK);
        assertEquals(2.0, v.getRateMultiplier(), 0.001);
    }

    // ── Equality ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Two vehicles with same plate are equal")
    void equality_samePlate() {
        Vehicle v1 = new Vehicle("34 ABC 001", VehicleType.CAR);
        Vehicle v2 = new Vehicle("34 ABC 001", VehicleType.TRUCK);
        assertEquals(v1, v2, "Equality is based on license plate only");
    }

    @Test
    @DisplayName("Two vehicles with different plates are not equal")
    void equality_differentPlates() {
        Vehicle v1 = new Vehicle("34 ABC 001", VehicleType.CAR);
        Vehicle v2 = new Vehicle("34 XYZ 999", VehicleType.CAR);
        assertNotEquals(v1, v2);
    }

    @Test
    @DisplayName("toString includes plate and type")
    void toString_containsKeyInfo() {
        Vehicle v = new Vehicle("34 ABC 001", VehicleType.CAR);
        String str = v.toString();
        assertTrue(str.contains("34 ABC 001"));
        assertTrue(str.contains("Car"));
    }
}