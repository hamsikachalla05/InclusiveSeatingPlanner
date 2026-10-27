package com.seatingplanner.model;

/**
 * Broad zones of the hall. The hall is split into thirds by row:
 * the first third is FRONT, the last third is BACK and the rest is MIDDLE.
 * ANY is only used as an attendee preference ("no preference").
 */
public enum SeatingArea {
    ANY("No preference"),
    FRONT("Front"),
    MIDDLE("Middle"),
    BACK("Back");

    private final String label;

    SeatingArea(String label) {
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
