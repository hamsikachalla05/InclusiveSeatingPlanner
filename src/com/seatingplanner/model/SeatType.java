package com.seatingplanner.model;

/** The administrative type of a seat in the hall. */
public enum SeatType {
    STANDARD("Standard"),
    ACCESSIBLE("Accessible"),
    RESERVED("Reserved");

    private final String label;

    SeatType(String label) {
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
