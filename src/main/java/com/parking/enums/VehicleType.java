package com.parking.enums;

/**
 * Represents the type of vehicle entering the parking lot.
 * Each type has a different hourly rate multiplier.
 */
public enum VehicleType {
    MOTORCYCLE(0.5, "Motorcycle"),
    CAR(1.0,        "Car"),
    TRUCK(2.0,      "Truck");

    private final double rateMultiplier;
    private final String displayName;

    VehicleType(double rateMultiplier, String displayName) {
        this.rateMultiplier = rateMultiplier;
        this.displayName    = displayName;
    }

    public double getRateMultiplier() { return rateMultiplier; }
    public String getDisplayName()    { return displayName; }
}