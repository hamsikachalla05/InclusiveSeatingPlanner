package com.seatingplanner.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A detected or recorded seating problem.
 * <p>
 * "Live" conflicts are produced by re-validating the current layout; their {@code key}
 * identifies the same underlying issue across re-validations so its status can be tracked.
 * Non-live conflicts record events such as a rejected manual change.
 */
public class Conflict {

    private String id = "";
    private final ConflictCategory category;
    private ConflictSeverity severity;
    private List<String> affectedAttendees;
    private String description;
    private String suggestion;
    private ResolutionStatus status = ResolutionStatus.OPEN;
    private final String key;
    private final boolean live;
    private LocalDateTime detectedAt = LocalDateTime.now();
    private LocalDateTime updatedAt = detectedAt;

    public Conflict(ConflictCategory category, ConflictSeverity severity, List<String> affectedAttendees,
                    String description, String suggestion, String key, boolean live) {
        this.category = category;
        this.severity = severity;
        this.affectedAttendees = List.copyOf(affectedAttendees);
        this.description = description;
        this.suggestion = suggestion == null ? "" : suggestion;
        this.key = key;
        this.live = live;
    }

    /** Copies the detail fields of a fresh detection of the same issue into this conflict. */
    public void refreshFrom(Conflict detected) {
        this.severity = detected.severity;
        this.affectedAttendees = detected.affectedAttendees;
        this.description = detected.description;
        this.suggestion = detected.suggestion;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ConflictCategory getCategory() {
        return category;
    }

    public ConflictSeverity getSeverity() {
        return severity;
    }

    public List<String> getAffectedAttendees() {
        return affectedAttendees;
    }

    /** The attendee the conflict is primarily about (first affected), or "" for hall-wide issues. */
    public String getPrimaryAttendee() {
        return affectedAttendees.isEmpty() ? "" : affectedAttendees.get(0);
    }

    public String getDescription() {
        return description;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public ResolutionStatus getStatus() {
        return status;
    }

    public void setStatus(ResolutionStatus status) {
        if (this.status != status) {
            this.status = status;
            this.updatedAt = LocalDateTime.now();
        }
    }

    public boolean isOpen() {
        return status == ResolutionStatus.OPEN;
    }

    public String getKey() {
        return key;
    }

    public boolean isLive() {
        return live;
    }

    public LocalDateTime getDetectedAt() {
        return detectedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setTimestamps(LocalDateTime detectedAt, LocalDateTime updatedAt) {
        this.detectedAt = detectedAt;
        this.updatedAt = updatedAt;
    }

    /** e.g. "ERROR: No accessible seat is available for attendee A014. ..." */
    public String formatMessage() {
        return severity.name() + ": " + description;
    }
}
