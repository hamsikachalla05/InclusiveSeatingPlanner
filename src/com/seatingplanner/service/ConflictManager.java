package com.seatingplanner.service;

import com.seatingplanner.model.Conflict;
import com.seatingplanner.model.ConflictCategory;
import com.seatingplanner.model.ConflictSeverity;
import com.seatingplanner.model.Project;
import com.seatingplanner.model.ResolutionStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps the conflict register and its resolution lifecycle.
 * <p>
 * After every change the planner re-runs detection and calls {@link #reconcile}. A known issue
 * keeps its ID; an issue that is no longer detected moves to ADDRESSED (it is never deleted, so
 * the organizer can review it); an addressed issue that reappears is reopened.
 */
public class ConflictManager {

    private final List<Conflict> conflicts = new ArrayList<>();
    private int nextNumber = 1;

    public List<Conflict> getConflicts() {
        return Collections.unmodifiableList(conflicts);
    }

    public int getNextNumber() {
        return nextNumber;
    }

    public Conflict find(String id) {
        for (Conflict c : conflicts) {
            if (c.getId().equals(id)) {
                return c;
            }
        }
        return null;
    }

    public void restore(List<Conflict> saved, int nextNumber) {
        conflicts.clear();
        conflicts.addAll(saved);
        int max = 0;
        for (Conflict c : saved) {
            try {
                max = Math.max(max, Integer.parseInt(c.getId().replaceAll("\\D", "")));
            } catch (NumberFormatException ignored) {
                // non-numeric IDs are tolerated
            }
        }
        this.nextNumber = Math.max(nextNumber, max + 1);
    }

    public void clear() {
        conflicts.clear();
        nextNumber = 1;
    }

    /** Merges a fresh detection result into the register. */
    public void reconcile(List<Conflict> detected) {
        Map<String, Conflict> byKey = new HashMap<>();
        for (Conflict c : conflicts) {
            if (c.isLive()) {
                byKey.put(c.getKey(), c);
            }
        }
        Set<String> seen = new HashSet<>();
        for (Conflict d : detected) {
            seen.add(d.getKey());
            Conflict existing = byKey.get(d.getKey());
            if (existing == null) {
                assignId(d);
                conflicts.add(d);
                continue;
            }
            ConflictSeverity before = existing.getSeverity();
            existing.refreshFrom(d);
            boolean escalated = before == ConflictSeverity.WARNING && d.getSeverity() == ConflictSeverity.ERROR;
            if (existing.getStatus() == ResolutionStatus.ADDRESSED || existing.getStatus() == ResolutionStatus.RESOLVED
                    || (existing.getStatus() == ResolutionStatus.ACKNOWLEDGED && escalated)) {
                existing.setStatus(ResolutionStatus.OPEN);
            }
        }
        Iterator<Conflict> it = conflicts.iterator();
        while (it.hasNext()) {
            Conflict c = it.next();
            if (c.isLive() && !seen.contains(c.getKey())
                    && (c.getStatus() == ResolutionStatus.OPEN || c.getStatus() == ResolutionStatus.ACKNOWLEDGED)) {
                if (isPendingNotice(c)) {
                    it.remove();
                } else {
                    c.setStatus(ResolutionStatus.ADDRESSED);
                }
            }
        }
    }

    /**
     * "Not yet assigned" warnings are routine notices: seating the attendee is itself the
     * validated fix, so they are dropped once they no longer apply instead of cluttering the history.
     */
    private static boolean isPendingNotice(Conflict c) {
        return c.getCategory() == ConflictCategory.ATTENDEE_UNASSIGNED && c.getSeverity() == ConflictSeverity.WARNING;
    }

    /**
     * Records an event-type conflict (e.g. a rejected manual change). The change was blocked so the
     * layout is unaffected; the record starts ADDRESSED and only needs the organizer's review.
     */
    public Conflict record(ConflictCategory category, ConflictSeverity severity, List<String> affected,
                           String description, String suggestion) {
        Conflict c = new Conflict(category, severity, affected, description, suggestion, "EVENT", false);
        assignId(c);
        c.setStatus(ResolutionStatus.ADDRESSED);
        conflicts.add(c);
        return c;
    }

    /**
     * Marks a conflict RESOLVED only after re-validating the current project. Fails (returning an
     * explanation) when the issue is still detected.
     *
     * @return null on success, otherwise the reason the conflict cannot be marked resolved
     */
    public String markResolved(String conflictId, Project project, ConstraintValidator validator) {
        Conflict c = find(conflictId);
        if (c == null) {
            return "Conflict " + conflictId + " no longer exists.";
        }
        if (c.getStatus() == ResolutionStatus.RESOLVED) {
            return null;
        }
        if (c.isLive()) {
            reconcile(validator.detect(project));
            if (c.getStatus() == ResolutionStatus.OPEN || c.getStatus() == ResolutionStatus.ACKNOWLEDGED) {
                return "Re-validation still detects this issue:\n\n" + c.formatMessage()
                        + (c.getSuggestion().isEmpty() ? "" : "\n\nSuggested fix: " + c.getSuggestion());
            }
        }
        c.setStatus(ResolutionStatus.RESOLVED);
        return null;
    }

    /** Accepts a still-present WARNING. Errors can never be acknowledged away. */
    public String acknowledge(String conflictId) {
        Conflict c = find(conflictId);
        if (c == null) {
            return "Conflict " + conflictId + " no longer exists.";
        }
        if (c.getSeverity() == ConflictSeverity.ERROR) {
            return "Errors concern mandatory requirements and cannot be acknowledged. Fix the underlying issue instead.";
        }
        if (c.getStatus() != ResolutionStatus.OPEN) {
            return "Only open warnings can be acknowledged.";
        }
        c.setStatus(ResolutionStatus.ACKNOWLEDGED);
        return null;
    }

    /** Confirms every ADDRESSED conflict as RESOLVED (each was already validated as gone). */
    public int confirmAllAddressed(Project project, ConstraintValidator validator) {
        reconcile(validator.detect(project));
        int count = 0;
        for (Conflict c : conflicts) {
            if (c.getStatus() == ResolutionStatus.ADDRESSED) {
                c.setStatus(ResolutionStatus.RESOLVED);
                count++;
            }
        }
        return count;
    }

    /** Removes RESOLVED records from the register. */
    public int purgeResolved() {
        int before = conflicts.size();
        conflicts.removeIf(c -> c.getStatus() == ResolutionStatus.RESOLVED);
        return before - conflicts.size();
    }

    public long count(ConflictSeverity severity, ResolutionStatus status) {
        return conflicts.stream().filter(c -> c.getSeverity() == severity && c.getStatus() == status).count();
    }

    /** Conflicts still present in the layout (open, or acknowledged warnings). */
    public long countUnresolved() {
        return conflicts.stream().filter(c -> c.getStatus() == ResolutionStatus.OPEN
                || c.getStatus() == ResolutionStatus.ACKNOWLEDGED).count();
    }

    public long countOpenErrors() {
        return count(ConflictSeverity.ERROR, ResolutionStatus.OPEN);
    }

    public List<Conflict> openConflictsFor(String attendeeId) {
        List<Conflict> list = new ArrayList<>();
        for (Conflict c : conflicts) {
            if ((c.isOpen() || c.getStatus() == ResolutionStatus.ACKNOWLEDGED) && c.getAffectedAttendees().contains(attendeeId)) {
                list.add(c);
            }
        }
        return list;
    }

    private void assignId(Conflict c) {
        c.setId(String.format("C%03d", nextNumber++));
    }
}
