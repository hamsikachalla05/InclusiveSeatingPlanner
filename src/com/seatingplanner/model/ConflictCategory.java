package com.seatingplanner.model;

/** Kinds of conflict the planner can detect or record. */
public enum ConflictCategory {
    CAPACITY_EXCEEDED("Capacity exceeded", true),
    INSUFFICIENT_ACCESSIBLE("Insufficient accessible seats", true),
    ACCESSIBILITY_UNMET("Accessibility requirement unmet", true),
    COMPANION_NO_ADJACENT("No adjacent companion seat", true),
    COMPANION_INVALID("Invalid companion reference", true),
    GROUP_SPLIT("Group split", false),
    PREFERENCE_UNMET("Seating preference unmet", false),
    RESERVED_BLOCKED("Reserved seat blocking request", false),
    INVALID_MANUAL_CHANGE("Invalid manual change", false),
    ATTENDEE_UNASSIGNED("Attendee unassigned", false);

    private final String label;
    private final boolean mandatory;

    ConflictCategory(String label, boolean mandatory) {
        this.label = label;
        this.mandatory = mandatory;
    }

    public String getLabel() {
        return label;
    }

    /** True when the category concerns a hard constraint (accessibility, companion, capacity). */
    public boolean isMandatory() {
        return mandatory;
    }

    @Override
    public String toString() {
        return label;
    }
}
