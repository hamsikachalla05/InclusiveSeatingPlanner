package com.seatingplanner.model;

/** ERROR = a mandatory requirement is violated or unmet; WARNING = a preference was relaxed. */
public enum ConflictSeverity {
    ERROR("Error"),
    WARNING("Warning");

    private final String label;

    ConflictSeverity(String label) {
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
