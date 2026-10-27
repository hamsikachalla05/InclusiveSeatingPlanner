package com.seatingplanner.model;

/**
 * Lifecycle of a conflict.
 * <ul>
 *   <li>OPEN - the issue is still present in the current layout.</li>
 *   <li>ADDRESSED - re-validation no longer detects the issue; awaiting organizer review.</li>
 *   <li>RESOLVED - the organizer confirmed an addressed conflict.</li>
 *   <li>ACKNOWLEDGED - the organizer accepted a WARNING that is still present.</li>
 * </ul>
 */
public enum ResolutionStatus {
    OPEN("Open"),
    ADDRESSED("Addressed - awaiting review"),
    RESOLVED("Resolved"),
    ACKNOWLEDGED("Acknowledged");

    private final String label;

    ResolutionStatus(String label) {
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
