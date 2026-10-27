package com.seatingplanner.model;

/**
 * Seating accommodations that are treated as HARD constraints.
 * Only the accommodation needed for planning is stored - never a diagnosis
 * or other medical detail.
 */
public enum AccessibilityNeed {
    NONE("None", "No seating accommodation required"),
    ACCESSIBLE_SEAT("Accessible seat", "Needs a designated accessible seating space (e.g. wheelchair space)"),
    AISLE_SEAT("Aisle / row-end seat", "Needs a seat beside an aisle or at the end of a row for easy access"),
    FRONT_SECTION("Front-section seat", "Needs a seat in the front section (e.g. for sightline or hearing access)");

    private final String label;
    private final String description;

    AccessibilityNeed(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    /** Lower numbers are scarcer resources and are allocated first. */
    public int allocationPriority() {
        return switch (this) {
            case ACCESSIBLE_SEAT -> 0;
            case FRONT_SECTION -> 1;
            case AISLE_SEAT -> 2;
            case NONE -> 3;
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
