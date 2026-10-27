package com.seatingplanner.model;

/** Whether a seat is free, taken, or held back as a reservation. */
public enum OccupancyStatus {
    AVAILABLE("Available"),
    OCCUPIED("Occupied"),
    HELD("Held (reserved)");

    private final String label;

    OccupancyStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
