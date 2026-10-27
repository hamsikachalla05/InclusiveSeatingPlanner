package com.seatingplanner.model;

/**
 * The seating outcome for a single attendee.
 * UNRESOLVED means a mandatory requirement (accessibility or companion) is not satisfied,
 * whether or not the attendee currently holds a seat.
 */
public enum AssignmentStatus {
    ASSIGNED("Assigned"),
    UNASSIGNED("Unassigned"),
    UNRESOLVED("Unresolved");

    private final String label;

    AssignmentStatus(String label) {
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
