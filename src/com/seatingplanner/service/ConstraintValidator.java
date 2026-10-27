package com.seatingplanner.service;

import com.seatingplanner.model.AccessibilityNeed;
import com.seatingplanner.model.Attendee;
import com.seatingplanner.model.Conflict;
import com.seatingplanner.model.ConflictCategory;
import com.seatingplanner.model.ConflictSeverity;
import com.seatingplanner.model.EventHall;
import com.seatingplanner.model.Project;
import com.seatingplanner.model.Seat;
import com.seatingplanner.model.SeatType;
import com.seatingplanner.model.SeatingArea;
import com.seatingplanner.model.SeatingAssignment;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Single source of truth for seating rules.
 * <p>
 * HARD constraints (never violated silently): seat type/reservation suitability, accessibility
 * needs and required companion adjacency. SOFT constraints (may be relaxed but always reported):
 * group proximity and preferred area/row.
 * <p>
 * All conflict detection is derived purely from the current state, so the same project always
 * yields the same conflicts - including after it is saved and reloaded.
 */
public class ConstraintValidator {

    /** Kinds of hard-constraint violation for a seated attendee. */
    public enum ViolationKind { SEAT_UNSUITABLE, COMPANION_INVALID, COMPANION_NOT_SEATED, COMPANION_NOT_ADJACENT }

    /** A hard-constraint violation affecting one seated attendee. */
    public record Violation(String attendeeId, ViolationKind kind, String message) {
        public String key() {
            return attendeeId + ":" + kind;
        }
    }

    // ------------------------------------------------------------------ seat-level rules

    /**
     * Returns null when the seat satisfies every mandatory requirement of the attendee,
     * otherwise a human-readable explanation of why it does not.
     */
    public String unsuitableReason(Attendee a, Seat seat, EventHall hall) {
        if (seat.getType() == SeatType.RESERVED && !seat.isReservedFor(a.getId())) {
            String holder = seat.getReservedFor().isEmpty() ? "" : " for " + seat.getReservedFor();
            return "Seat " + seat.getId() + " is reserved" + holder + ".";
        }
        switch (a.getAccessibilityNeed()) {
            case ACCESSIBLE_SEAT -> {
                if (seat.getType() != SeatType.ACCESSIBLE) {
                    return a.getId() + " requires an accessible seat, but " + seat.getId() + " is a "
                            + seat.getType().getLabel().toLowerCase() + " seat.";
                }
            }
            case AISLE_SEAT -> {
                if (!hall.isAisleSeat(seat)) {
                    return a.getId() + " requires an aisle or row-end seat, but " + seat.getId() + " is not beside an aisle.";
                }
            }
            case FRONT_SECTION -> {
                if (hall.areaOf(seat.getRow()) != SeatingArea.FRONT) {
                    return a.getId() + " requires a front-section seat (rows A-" + EventHall.rowLabel(hall.getSectionRows())
                            + "), but " + seat.getId() + " is in row " + seat.getRowLabel() + ".";
                }
            }
            case NONE -> {
                // no seat-type requirement
            }
        }
        return null;
    }

    public boolean isSuitable(Attendee a, Seat seat, EventHall hall) {
        return unsuitableReason(a, seat, hall) == null;
    }

    /** True when a seat is a scarce accessible seat being given to someone who does not need it. */
    public boolean wastesAccessibleSeat(Attendee a, Seat seat) {
        return seat.getType() == SeatType.ACCESSIBLE && a.getAccessibilityNeed() != AccessibilityNeed.ACCESSIBLE_SEAT;
    }

    /** Soft constraint: preferred row (if given) or preferred area. */
    public boolean preferenceSatisfied(Attendee a, Seat seat, EventHall hall) {
        if (!a.getPreferredRow().isEmpty()) {
            return seat.getRowLabel().equals(a.getPreferredRow());
        }
        return a.getPreferredArea() == SeatingArea.ANY || hall.areaOf(seat.getRow()) == a.getPreferredArea();
    }

    // ------------------------------------------------------------------ companion rules

    /** The valid companion attendee, or null when none is required or the reference is invalid. */
    public Attendee resolveCompanion(Project p, Attendee a) {
        if (!a.isRequiresCompanion() || a.getCompanionId().isEmpty() || a.getCompanionId().equals(a.getId())) {
            return null;
        }
        return p.getAttendee(a.getCompanionId());
    }

    /** Explains an invalid or missing companion reference, or returns null when it is valid. */
    public String companionReferenceProblem(Project p, Attendee a) {
        if (!a.isRequiresCompanion()) {
            return null;
        }
        if (a.getCompanionId().isEmpty()) {
            return "Attendee " + a.getId() + " requires a companion, but no companion attendee ID is recorded.";
        }
        if (a.getCompanionId().equals(a.getId())) {
            return "Attendee " + a.getId() + " lists themself as their own companion.";
        }
        if (p.getAttendee(a.getCompanionId()) == null) {
            return "Attendee " + a.getId() + " references companion " + a.getCompanionId() + ", who is not registered.";
        }
        return null;
    }

    /** Attendees who require {@code companion} to sit beside them. */
    public List<Attendee> dependentsOf(Project p, String companionId) {
        List<Attendee> list = new ArrayList<>();
        for (Attendee a : p.getAttendees()) {
            Attendee c = resolveCompanion(p, a);
            if (c != null && c.getId().equals(companionId)) {
                list.add(a);
            }
        }
        return list;
    }

    /** The attendee plus everyone linked to them by a companion requirement in either direction. */
    public Set<String> companionClosure(Project p, String attendeeId) {
        Set<String> ids = new LinkedHashSet<>();
        ids.add(attendeeId);
        Attendee a = p.getAttendee(attendeeId);
        if (a != null) {
            Attendee c = resolveCompanion(p, a);
            if (c != null) {
                ids.add(c.getId());
            }
        }
        for (Attendee d : dependentsOf(p, attendeeId)) {
            ids.add(d.getId());
        }
        return ids;
    }

    // ------------------------------------------------------------------ hard violations

    /** Hard-constraint violations of a seated attendee (an unseated attendee violates nothing). */
    public List<Violation> violationsFor(Project p, Attendee a) {
        List<Violation> out = new ArrayList<>();
        SeatingAssignment asg = p.getAssignment();
        if (asg == null) {
            return out;
        }
        Seat seat = asg.getSeatOf(a.getId());
        if (seat == null) {
            return out;
        }
        String reason = unsuitableReason(a, seat, p.getHall());
        if (reason != null) {
            out.add(new Violation(a.getId(), ViolationKind.SEAT_UNSUITABLE, reason));
        }
        if (a.isRequiresCompanion()) {
            String refProblem = companionReferenceProblem(p, a);
            if (refProblem != null) {
                out.add(new Violation(a.getId(), ViolationKind.COMPANION_INVALID, refProblem));
            } else {
                Attendee c = resolveCompanion(p, a);
                Seat cs = asg.getSeatOf(c.getId());
                if (cs == null) {
                    out.add(new Violation(a.getId(), ViolationKind.COMPANION_NOT_SEATED,
                            a.getId() + " requires companion " + c.getId() + " beside them, but " + c.getId() + " is not seated."));
                } else if (!p.getHall().areAdjacent(seat, cs)) {
                    out.add(new Violation(a.getId(), ViolationKind.COMPANION_NOT_ADJACENT,
                            a.getId() + " (seat " + seat.getId() + ") requires companion " + c.getId()
                                    + " directly beside them, but " + c.getId() + " is in seat " + cs.getId() + "."));
                }
            }
        }
        return out;
    }

    public List<Violation> violationsFor(Project p, Set<String> attendeeIds) {
        List<Violation> out = new ArrayList<>();
        for (String id : attendeeIds) {
            Attendee a = p.getAttendee(id);
            if (a != null) {
                out.addAll(violationsFor(p, a));
            }
        }
        return out;
    }

    /**
     * Releases assignments that can no longer be valid: seats that became unsuitable (e.g. after a
     * hall change or loading an edited file), pairs that are seated apart, and entries for attendees
     * who are no longer registered. Returns the IDs that were released.
     */
    public List<String> releaseInvalidAssignments(Project p) {
        List<String> released = new ArrayList<>();
        SeatingAssignment asg = p.getAssignment();
        if (asg == null) {
            return released;
        }
        for (String id : new ArrayList<>(asg.asMap().keySet())) {
            Attendee a = p.getAttendee(id);
            if (a == null) {
                asg.unassign(id);
                released.add(id);
            } else if (unsuitableReason(a, asg.getSeatOf(id), p.getHall()) != null) {
                asg.unassign(id);
                released.add(id);
            }
        }
        for (Attendee a : p.getAttendees()) {
            Attendee c = resolveCompanion(p, a);
            if (c == null) {
                continue;
            }
            Seat sa = asg.getSeatOf(a.getId());
            Seat sc = asg.getSeatOf(c.getId());
            if (sa != null && sc != null && !p.getHall().areAdjacent(sa, sc)) {
                asg.unassign(a.getId());
                asg.unassign(c.getId());
                released.add(a.getId());
                released.add(c.getId());
            }
        }
        return released;
    }

    // ------------------------------------------------------------------ capacity helpers

    /** Seats an attendee could ever be given: non-reserved seats plus seats reserved for a registered attendee. */
    public int assignableCapacity(Project p) {
        if (!p.hasHall()) {
            return 0;
        }
        int count = 0;
        for (Seat s : p.getHall().getSeats()) {
            if (s.getType() != SeatType.RESERVED || p.containsAttendee(s.getReservedFor())) {
                count++;
            }
        }
        return count;
    }

    public boolean hasFreeSuitableSeat(Project p, Attendee a) {
        for (Seat s : p.getHall().getSeats()) {
            if (!s.isOccupied() && isSuitable(a, s, p.getHall())) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code a} (unseated) and companion {@code c} can still be placed side by side. */
    public boolean hasFreeCompanionPair(Project p, Attendee a, Attendee c) {
        EventHall hall = p.getHall();
        Seat cs = p.getAssignment().getSeatOf(c.getId());
        if (cs != null) {
            for (Seat n : hall.rowNeighbours(cs)) {
                if (!n.isOccupied() && isSuitable(a, n, hall)) {
                    return true;
                }
            }
            return false;
        }
        for (Seat s : hall.getSeats()) {
            if (s.isOccupied() || !isSuitable(a, s, hall)) {
                continue;
            }
            for (Seat n : hall.rowNeighbours(s)) {
                if (!n.isOccupied() && isSuitable(c, n, hall)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A free seat beside {@code anchor} that suits {@code a}, or null. */
    public Seat freeSuitableNeighbour(Project p, Attendee a, Seat anchor) {
        for (Seat n : p.getHall().rowNeighbours(anchor)) {
            if (!n.isOccupied() && isSuitable(a, n, p.getHall())) {
                return n;
            }
        }
        return null;
    }

    /** Number of separate "near" clusters (row neighbours or front/back neighbours) the seats form. */
    public int clusterCount(EventHall hall, List<Seat> seats) {
        Set<Seat> unvisited = new HashSet<>(seats);
        int clusters = 0;
        while (!unvisited.isEmpty()) {
            clusters++;
            Seat start = unvisited.iterator().next();
            Deque<Seat> queue = new ArrayDeque<>();
            queue.add(start);
            unvisited.remove(start);
            while (!queue.isEmpty()) {
                Seat cur = queue.poll();
                for (Seat other : new ArrayList<>(unvisited)) {
                    if (hall.areNear(cur, other)) {
                        unvisited.remove(other);
                        queue.add(other);
                    }
                }
            }
        }
        return clusters;
    }

    // ------------------------------------------------------------------ conflict detection

    /**
     * Detects every live conflict in the project. Conflicts returned here have no ID yet;
     * {@link ConflictManager} matches them to previously known conflicts by key.
     */
    public List<Conflict> detect(Project p) {
        List<Conflict> out = new ArrayList<>();
        List<Attendee> all = p.getAttendees();
        if (!p.hasHall()) {
            if (!all.isEmpty()) {
                out.add(live(ConflictCategory.CAPACITY_EXCEEDED, ConflictSeverity.ERROR, ids(all),
                        "No event hall has been configured, so " + all.size() + " registered attendee(s) cannot be seated.",
                        "Create the hall layout on the Hall Setup screen.", "NO_HALL"));
            }
            return out;
        }
        EventHall hall = p.getHall();
        SeatingAssignment asg = p.getAssignment();
        Set<String> mandatoryFailures = new HashSet<>();
        List<String> unseated = new ArrayList<>();

        for (Attendee a : all) {
            Seat seat = asg.getSeatOf(a.getId());
            String refProblem = companionReferenceProblem(p, a);
            if (refProblem != null) {
                mandatoryFailures.add(a.getId());
                out.add(live(ConflictCategory.COMPANION_INVALID, ConflictSeverity.ERROR, List.of(a.getId()), refProblem,
                        "Edit attendee " + a.getId() + " and select a registered companion (register the companion first), "
                                + "or untick 'Requires companion' if it is no longer needed.",
                        "COMP_REF:" + a.getId()));
            }
            Attendee companion = resolveCompanion(p, a);
            if (seat != null) {
                detectSeatedIssues(p, a, seat, companion, out, mandatoryFailures);
            } else {
                unseated.add(a.getId());
                detectUnseatedIssues(p, a, companion, refProblem != null, out, mandatoryFailures);
            }
        }

        int assignable = assignableCapacity(p);
        if (all.size() > assignable) {
            int reserved = hall.countSeats(SeatType.RESERVED);
            out.add(live(ConflictCategory.CAPACITY_EXCEEDED, ConflictSeverity.ERROR, unseated,
                    all.size() + " attendees are registered but the hall has only " + assignable + " assignable seats"
                            + (reserved > 0 ? " (" + reserved + " seat(s) are reserved)" : "") + ". At least "
                            + (all.size() - assignable) + " attendee(s) cannot be seated.",
                    "Add rows or seats on the Hall Setup screen, release reserved seats, or reduce registrations.",
                    "CAPACITY"));
        }

        detectGroupIssues(p, out, mandatoryFailures);
        return out;
    }

    private void detectSeatedIssues(Project p, Attendee a, Seat seat, Attendee companion,
                                    List<Conflict> out, Set<String> mandatoryFailures) {
        EventHall hall = p.getHall();
        SeatingAssignment asg = p.getAssignment();
        String reason = unsuitableReason(a, seat, hall);
        if (reason != null) {
            mandatoryFailures.add(a.getId());
            ConflictCategory cat = seat.getType() == SeatType.RESERVED
                    ? ConflictCategory.RESERVED_BLOCKED : ConflictCategory.ACCESSIBILITY_UNMET;
            out.add(live(cat, ConflictSeverity.ERROR, List.of(a.getId()),
                    "Attendee " + a.getId() + " is seated in an unsuitable seat: " + reason,
                    "Move " + a.getId() + " to a seat that meets their requirement.", "SEAT_UNSUITABLE:" + a.getId()));
        }
        if (companion != null) {
            Seat cs = asg.getSeatOf(companion.getId());
            if (cs == null || !hall.areAdjacent(seat, cs)) {
                mandatoryFailures.add(a.getId());
                Seat free = freeSuitableNeighbour(p, companion, seat);
                String desc = cs == null
                        ? "Attendee " + a.getId() + " (seat " + seat.getId() + ") requires companion " + companion.getId()
                          + ", but the companion is not seated beside them."
                        : "Attendee " + a.getId() + " (seat " + seat.getId() + ") requires companion " + companion.getId()
                          + " directly beside them, but the companion is in seat " + cs.getId() + ".";
                String fix = free != null
                        ? "Assign " + companion.getId() + " to seat " + free.getId() + " (directly beside " + a.getId() + ")."
                        : "Move the pair to two adjacent free seats (no aisle between them), or free a seat beside " + seat.getId() + ".";
                out.add(live(ConflictCategory.COMPANION_NO_ADJACENT, ConflictSeverity.ERROR,
                        List.of(a.getId(), companion.getId()), desc, fix, "COMP_SEAT:" + a.getId()));
            }
        }
        if (!preferenceSatisfied(a, seat, hall)) {
            String blockedRow = reservedBlockedRow(p, a);
            if (blockedRow != null) {
                out.add(live(ConflictCategory.RESERVED_BLOCKED, ConflictSeverity.WARNING, List.of(a.getId()),
                        "Attendee " + a.getId() + " prefers row " + blockedRow + ", but its remaining seats are reserved. "
                                + a.getId() + " was seated in " + seat.getId() + " instead.",
                        "Release a reserved seat in row " + blockedRow + " if the preference matters.", "PREF:" + a.getId()));
            } else {
                String why = !a.getPreferredRow().isEmpty() && EventHall.rowIndexOf(a.getPreferredRow()) > hall.getRows()
                        ? " (row " + a.getPreferredRow() + " does not exist in this hall)" : "";
                out.add(live(ConflictCategory.PREFERENCE_UNMET, ConflictSeverity.WARNING, List.of(a.getId()),
                        "Attendee " + a.getId() + " prefers " + a.describePreference().toLowerCase() + why
                                + " but is seated in " + seat.getId() + ". All mandatory requirements are unaffected.",
                        "Move " + a.getId() + " manually if a suitable seat in the preferred area becomes free.",
                        "PREF:" + a.getId()));
            }
        }
    }

    private void detectUnseatedIssues(Project p, Attendee a, Attendee companion, boolean invalidReference,
                                      List<Conflict> out, Set<String> mandatoryFailures) {
        EventHall hall = p.getHall();
        if (a.hasAccessibilityNeed() && !hasFreeSuitableSeat(p, a)) {
            mandatoryFailures.add(a.getId());
            if (a.getAccessibilityNeed() == AccessibilityNeed.ACCESSIBLE_SEAT) {
                out.add(live(ConflictCategory.INSUFFICIENT_ACCESSIBLE, ConflictSeverity.ERROR, List.of(a.getId()),
                        "No accessible seat is available for attendee " + a.getId() + ". Please add an accessible seat "
                                + "or adjust the hall configuration.",
                        accessibleSeatSuggestion(p), "NO_SEAT:" + a.getId()));
            } else {
                out.add(live(ConflictCategory.ACCESSIBILITY_UNMET, ConflictSeverity.ERROR, List.of(a.getId()),
                        "No free " + a.getAccessibilityNeed().getLabel().toLowerCase() + " is available for attendee "
                                + a.getId() + ". The attendee has been left unassigned rather than given an unsuitable seat.",
                        "Free a suitable seat (move an attendee without this requirement) or adjust the hall configuration.",
                        "NO_SEAT:" + a.getId()));
            }
            return;
        }
        if (invalidReference) {
            return; // already reported; cannot be seated until the reference is fixed
        }
        if (companion != null && !hasFreeCompanionPair(p, a, companion)) {
            mandatoryFailures.add(a.getId());
            out.add(live(ConflictCategory.COMPANION_NO_ADJACENT, ConflictSeverity.ERROR, List.of(a.getId(), companion.getId()),
                    "Attendee " + a.getId() + " requires a companion, but no suitable adjacent seat is available.",
                    "Free two adjacent seats in one row (no aisle between them) that meet both attendees' requirements.",
                    "COMP_SEAT:" + a.getId()));
            return;
        }
        boolean noSeat = !hasFreeSuitableSeat(p, a);
        out.add(live(ConflictCategory.ATTENDEE_UNASSIGNED, noSeat ? ConflictSeverity.ERROR : ConflictSeverity.WARNING,
                List.of(a.getId()),
                noSeat
                        ? "Attendee " + a.getId() + " (" + a.getFullName() + ") is unassigned: no suitable free seat remains."
                        : "Attendee " + a.getId() + " (" + a.getFullName() + ") has not been assigned a seat yet.",
                noSeat
                        ? "Increase hall capacity, release reserved seats or remove registrations."
                        : "Run Auto-Assign (keeping existing assignments) or assign a seat manually.",
                "UNASSIGNED:" + a.getId()));
    }

    private void detectGroupIssues(Project p, List<Conflict> out, Set<String> mandatoryFailures) {
        EventHall hall = p.getHall();
        for (Map.Entry<String, List<Attendee>> e : p.getGroups().entrySet()) {
            List<Attendee> members = e.getValue();
            if (members.size() < 2) {
                continue;
            }
            List<Seat> seats = new ArrayList<>();
            int unseatedCount = 0;
            boolean anyMandatoryFailure = false;
            Set<String> rows = new TreeSet<>();
            for (Attendee m : members) {
                Seat s = p.getSeatOf(m);
                if (s == null) {
                    unseatedCount++;
                } else {
                    seats.add(s);
                    rows.add(s.getRowLabel());
                }
                anyMandatoryFailure |= mandatoryFailures.contains(m.getId());
            }
            if (seats.isEmpty()) {
                continue; // nothing seated yet - unassigned warnings cover this
            }
            int clusters = clusterCount(hall, seats);
            if (clusters <= 1 && unseatedCount == 0) {
                continue;
            }
            List<String> details = new ArrayList<>();
            if (clusters > 1) {
                details.add("seated in " + clusters + " separate clusters across row(s) " + String.join(", ", rows));
            }
            if (unseatedCount > 0) {
                details.add(unseatedCount + " member(s) not seated");
            }
            String tail = anyMandatoryFailure
                    ? "Some members also have unresolved mandatory requirements (see related errors)."
                    : "All mandatory requirements are satisfied, but the group preference remains unmet.";
            out.add(live(ConflictCategory.GROUP_SPLIT, ConflictSeverity.WARNING, ids(members),
                    "Group " + e.getKey() + " could not be seated entirely together (" + String.join("; ", details) + "). " + tail,
                    "Free a contiguous block of " + members.size() + " seats and re-run Auto-Assign, or move members manually.",
                    "GROUP:" + e.getKey().toUpperCase()));
        }
    }

    /** The preferred row label if that row's only free seats are reserved for others, else null. */
    private String reservedBlockedRow(Project p, Attendee a) {
        int row = EventHall.rowIndexOf(a.getPreferredRow());
        if (row < 1 || row > p.getHall().getRows()) {
            return null;
        }
        boolean hasHeldReserved = false;
        for (Seat s : p.getHall().getRowSeats(row)) {
            if (s.isOccupied()) {
                continue;
            }
            if (s.getType() == SeatType.RESERVED && !s.isReservedFor(a.getId())) {
                hasHeldReserved = true;
            } else if (isSuitable(a, s, p.getHall())) {
                return null; // a usable seat exists, so reservations are not what blocks the preference
            }
        }
        return hasHeldReserved ? a.getPreferredRow() : null;
    }

    private String accessibleSeatSuggestion(Project p) {
        for (Seat s : p.getHall().getSeats()) {
            if (s.getType() == SeatType.ACCESSIBLE && s.isOccupied()) {
                Attendee occ = p.getAttendee(s.getOccupantId());
                if (occ != null && occ.getAccessibilityNeed() != AccessibilityNeed.ACCESSIBLE_SEAT) {
                    return "Accessible seat " + s.getId() + " is used by " + occ.getId()
                            + ", who does not need it - move " + occ.getId() + " to free it, or mark another seat as Accessible.";
                }
            }
        }
        return "Mark an additional seat as Accessible on the Hall Setup screen.";
    }

    private static Conflict live(ConflictCategory cat, ConflictSeverity sev, List<String> affected,
                                 String desc, String suggestion, String key) {
        return new Conflict(cat, sev, affected, desc, suggestion, key, true);
    }

    private static List<String> ids(List<Attendee> attendees) {
        List<String> list = new ArrayList<>();
        for (Attendee a : attendees) {
            list.add(a.getId());
        }
        return list;
    }
}
