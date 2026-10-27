package com.seatingplanner.model;

import java.util.Objects;

/**
 * A registered attendee. Mandatory requirements (accessibility need, companion) are kept
 * separate from optional preferences (group, preferred area/row) so the allocator can treat
 * the former as hard constraints and the latter as soft ones.
 */
public class Attendee {

    private final String id;
    private String fullName;
    private String groupId = "";
    private AccessibilityNeed accessibilityNeed = AccessibilityNeed.NONE;
    private boolean requiresCompanion;
    private String companionId = "";
    private SeatingArea preferredArea = SeatingArea.ANY;
    private String preferredRow = "";
    private String notes = "";

    public Attendee(String id, String fullName) {
        String normalized = Ids.normalize(id);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Attendee ID is required.");
        }
        this.id = normalized;
        setFullName(fullName);
    }

    /** Returns an independent copy (used by edit dialogs so cancelled edits leave no trace). */
    public Attendee copy() {
        Attendee c = new Attendee(id, fullName);
        c.groupId = groupId;
        c.accessibilityNeed = accessibilityNeed;
        c.requiresCompanion = requiresCompanion;
        c.companionId = companionId;
        c.preferredArea = preferredArea;
        c.preferredRow = preferredRow;
        c.notes = notes;
        return c;
    }

    public String getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name is required.");
        }
        this.fullName = fullName.trim();
    }

    public String getGroupId() {
        return groupId;
    }

    public boolean hasGroup() {
        return !groupId.isEmpty();
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId == null ? "" : groupId.trim();
    }

    public AccessibilityNeed getAccessibilityNeed() {
        return accessibilityNeed;
    }

    public void setAccessibilityNeed(AccessibilityNeed need) {
        this.accessibilityNeed = Objects.requireNonNull(need);
    }

    public boolean hasAccessibilityNeed() {
        return accessibilityNeed != AccessibilityNeed.NONE;
    }

    public boolean isRequiresCompanion() {
        return requiresCompanion;
    }

    public void setRequiresCompanion(boolean requiresCompanion) {
        this.requiresCompanion = requiresCompanion;
    }

    /** ID of the registered attendee who must sit directly beside this attendee. */
    public String getCompanionId() {
        return companionId;
    }

    public void setCompanionId(String companionId) {
        this.companionId = Ids.normalize(companionId);
    }

    public SeatingArea getPreferredArea() {
        return preferredArea;
    }

    public void setPreferredArea(SeatingArea preferredArea) {
        this.preferredArea = preferredArea == null ? SeatingArea.ANY : preferredArea;
    }

    /** Preferred row label (e.g. "C"); takes precedence over the preferred area when set. */
    public String getPreferredRow() {
        return preferredRow;
    }

    public void setPreferredRow(String preferredRow) {
        this.preferredRow = Ids.normalize(preferredRow);
    }

    public boolean hasPreference() {
        return !preferredRow.isEmpty() || preferredArea != SeatingArea.ANY;
    }

    public String describePreference() {
        if (!preferredRow.isEmpty()) {
            return "Row " + preferredRow;
        }
        return preferredArea == SeatingArea.ANY ? "" : preferredArea.getLabel() + " section";
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes == null ? "" : notes.trim();
    }

    /** "Ada Lovelace" -> "Ada L." for compact seat labels. */
    public String getShortName() {
        String[] parts = fullName.split("\\s+");
        if (parts.length == 1) {
            return parts[0];
        }
        return parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }

    @Override
    public String toString() {
        return id + " - " + fullName;
    }
}
