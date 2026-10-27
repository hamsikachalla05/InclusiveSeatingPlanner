package com.seatingplanner.service;

import com.seatingplanner.model.AccessibilityNeed;
import com.seatingplanner.model.AssignmentStatus;
import com.seatingplanner.model.Attendee;
import com.seatingplanner.model.Conflict;
import com.seatingplanner.model.ConflictCategory;
import com.seatingplanner.model.ConflictSeverity;
import com.seatingplanner.model.EventHall;
import com.seatingplanner.model.Ids;
import com.seatingplanner.model.Project;
import com.seatingplanner.model.ResolutionStatus;
import com.seatingplanner.model.Seat;
import com.seatingplanner.model.SeatType;
import com.seatingplanner.model.SeatingAssignment;
import com.seatingplanner.service.ConstraintValidator.Violation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Application facade: every change to the project goes through this class, which validates the
 * change, applies it, re-runs conflict detection and notifies listeners (the UI and autosave).
 */
public class SeatingPlanner {

    /** How to treat attendees who depend on someone being unassigned as their companion. */
    public enum DependentPolicy { UNASSIGN_DEPENDENTS, LEAVE_UNRESOLVED }

    /** Headline numbers for the dashboard. */
    public record Stats(int capacity, int assignableCapacity, int registered, int assigned, int availableSeats,
                        int reservedSeats, int accessibleSeats, int accessibleFree, int needAccessible,
                        long openErrors, long openWarnings, long unresolved, long awaitingReview, int unresolvedAttendees) {
    }

    /** Outcome of rebuilding the hall. */
    public record HallChangeReport(int keptAssignments, List<String> releasedAttendees) {
    }

    private Project project = new Project();
    private final ConstraintValidator validator = new ConstraintValidator();
    private final SeatingAllocator allocator = new SeatingAllocator(validator);
    private final ConflictManager conflicts = new ConflictManager();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private Map<String, AssignmentStatus> statusCache = new HashMap<>();

    // ------------------------------------------------------------------ access

    public Project getProject() {
        return project;
    }

    public ConstraintValidator getValidator() {
        return validator;
    }

    public ConflictManager getConflictManager() {
        return conflicts;
    }

    public EventHall getHall() {
        return project.getHall();
    }

    public boolean hasHall() {
        return project.hasHall();
    }

    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    /** Replaces the whole project (new, opened or demo). Saved conflict history is kept and revalidated. */
    public List<String> replaceProject(Project newProject, List<Conflict> savedConflicts, int nextConflictNumber) {
        this.project = newProject;
        conflicts.restore(savedConflicts, nextConflictNumber);
        List<String> released = validator.releaseInvalidAssignments(project);
        changed();
        return released;
    }

    // ------------------------------------------------------------------ status & statistics

    public AssignmentStatus statusOf(Attendee a) {
        AssignmentStatus s = statusCache.get(a.getId());
        return s != null ? s : AssignmentStatus.UNASSIGNED;
    }

    public Seat seatOf(Attendee a) {
        return project.getSeatOf(a);
    }

    public Attendee occupantOf(Seat seat) {
        return seat == null || !seat.isOccupied() ? null : project.getAttendee(seat.getOccupantId());
    }

    public Stats getStats() {
        EventHall hall = project.getHall();
        int capacity = hall == null ? 0 : hall.getCapacity();
        int assigned = project.getAssignment() == null ? 0 : project.getAssignment().size();
        int available = 0;
        int reserved = 0;
        int accessible = 0;
        int accessibleFree = 0;
        if (hall != null) {
            for (Seat s : hall.getSeats()) {
                if (s.getType() == SeatType.RESERVED) {
                    reserved++;
                } else if (!s.isOccupied()) {
                    available++;
                }
                if (s.getType() == SeatType.ACCESSIBLE) {
                    accessible++;
                    if (!s.isOccupied()) {
                        accessibleFree++;
                    }
                }
            }
        }
        int needAccessible = (int) project.getAttendees().stream()
                .filter(a -> a.getAccessibilityNeed() == AccessibilityNeed.ACCESSIBLE_SEAT).count();
        int unresolvedAttendees = (int) statusCache.values().stream().filter(s -> s == AssignmentStatus.UNRESOLVED).count();
        return new Stats(capacity, validator.assignableCapacity(project), project.getAttendeeCount(), assigned, available,
                reserved, accessible, accessibleFree, needAccessible,
                conflicts.count(ConflictSeverity.ERROR, ResolutionStatus.OPEN),
                conflicts.count(ConflictSeverity.WARNING, ResolutionStatus.OPEN),
                conflicts.countUnresolved(),
                conflicts.getConflicts().stream().filter(c -> c.getStatus() == ResolutionStatus.ADDRESSED).count(),
                unresolvedAttendees);
    }

    /** True only when everyone is seated and no mandatory-requirement error remains. */
    public boolean isPlanFullyValid() {
        return project.getAttendeeCount() > 0 && hasHall()
                && project.getAssignment().size() == project.getAttendeeCount()
                && conflicts.countOpenErrors() == 0;
    }

    // ------------------------------------------------------------------ hall configuration

    /**
     * Builds a new hall from the configuration. Existing assignments whose seat still exists and
     * remains suitable are kept; others are released and reported.
     */
    public HallChangeReport applyHallConfig(HallConfig config) {
        EventHall newHall = config.build();
        SeatingAssignment newAssignment = new SeatingAssignment(newHall);
        List<String> released = new ArrayList<>();
        SeatingAssignment old = project.getAssignment();
        if (old != null) {
            for (Map.Entry<String, String> e : old.asMap().entrySet()) {
                Seat target = newHall.getSeat(e.getValue());
                if (target != null && !target.isOccupied()) {
                    newAssignment.assign(e.getKey(), target);
                } else {
                    released.add(e.getKey());
                }
            }
        }
        project.setHall(newHall, newAssignment);
        released.addAll(validator.releaseInvalidAssignments(project));
        changed();
        return new HallChangeReport(newAssignment.size(), released);
    }

    /** Assignments that would not survive the given configuration (for a confirmation prompt). */
    public int countAssignmentsAffectedBy(HallConfig config) {
        if (project.getAssignment() == null || project.getAssignment().size() == 0) {
            return 0;
        }
        Project probe = new Project();
        for (Attendee a : project.getAttendees()) {
            probe.addAttendee(a);
        }
        EventHall hall = config.build();
        SeatingAssignment asg = new SeatingAssignment(hall);
        int lost = 0;
        for (Map.Entry<String, String> e : project.getAssignment().asMap().entrySet()) {
            Seat s = hall.getSeat(e.getValue());
            if (s == null) {
                lost++;
            } else {
                asg.assign(e.getKey(), s);
            }
        }
        probe.setHall(hall, asg);
        return lost + validator.releaseInvalidAssignments(probe).size();
    }

    public void renameEvent(String name) {
        if (hasHall()) {
            project.getHall().setEventName(name);
            changed();
        }
    }

    /** Changes one seat's type from the seat map. Refuses if the occupant would no longer fit. */
    public ActionResult setSeatType(String seatId, SeatType type, String reservedFor) {
        Seat seat = requireHall().getSeat(seatId);
        if (seat == null) {
            return ActionResult.rejected("Seat " + seatId + " does not exist.");
        }
        SeatType oldType = seat.getType();
        String oldHolder = seat.getReservedFor();
        seat.setType(type);
        seat.setReservedFor(type == SeatType.RESERVED ? reservedFor : "");
        Attendee occ = occupantOf(seat);
        if (occ != null) {
            String reason = validator.unsuitableReason(occ, seat, project.getHall());
            if (reason != null) {
                seat.setType(oldType);
                seat.setReservedFor(oldHolder);
                return ActionResult.rejected("Seat " + seat.getId() + " is occupied by " + occ.getId() + " (" + occ.getFullName()
                        + "). Making it " + type.getLabel().toLowerCase() + " would break a mandatory requirement:\n"
                        + reason + "\n\nMove or unassign " + occ.getId() + " first.");
            }
        }
        changed();
        return ActionResult.ok("Seat " + seat.getId() + " is now " + type.getLabel().toLowerCase() + ".");
    }

    // ------------------------------------------------------------------ attendees

    public void addAttendee(Attendee a) {
        project.addAttendee(a);
        changed();
    }

    /** Adds many attendees (e.g. an import) with a single re-validation. */
    public void addAttendees(List<Attendee> list) {
        for (Attendee a : list) {
            project.addAttendee(a);
        }
        changed();
    }

    /**
     * Mandatory problems the edited attendee would have in their current seat. Lets the UI warn
     * before saving instead of silently accepting an invalid state.
     */
    public String seatProblemAfterEdit(Attendee edited) {
        Seat seat = project.getAssignment() == null ? null : project.getAssignment().getSeatOf(edited.getId());
        return seat == null ? null : validator.unsuitableReason(edited, seat, project.getHall());
    }

    /** Saves the edit. If the current seat no longer suits the attendee, the seat is released. */
    public boolean updateAttendee(Attendee edited) {
        boolean released = false;
        if (seatProblemAfterEdit(edited) != null) {
            project.getAssignment().unassign(edited.getId());
            released = true;
        }
        project.replaceAttendee(edited);
        changed();
        return released;
    }

    public void removeAttendee(String id) {
        project.removeAttendee(id);
        changed();
    }

    /** Attendees who list {@code id} as their required companion. */
    public List<Attendee> dependentsOf(String id) {
        return validator.dependentsOf(project, Ids.normalize(id));
    }

    // ------------------------------------------------------------------ automatic assignment

    public SeatingAllocator.AllocationResult autoAssign(boolean preserveExisting) {
        requireHall();
        SeatingAllocator.AllocationResult result = allocator.allocate(project, preserveExisting);
        changed();
        return result;
    }

    public void resetAssignments() {
        if (project.getAssignment() != null) {
            project.getAssignment().clear();
            changed();
        }
    }

    // ------------------------------------------------------------------ manual assignment

    /** Whether seating this attendee here would use up an accessible seat they do not need. */
    public boolean wouldWasteAccessibleSeat(String attendeeId, String seatId) {
        Attendee a = project.getAttendee(attendeeId);
        Seat s = hasHall() ? project.getHall().getSeat(seatId) : null;
        return a != null && s != null && validator.wastesAccessibleSeat(a, s);
    }

    /** Assigns an unseated attendee or moves a seated one. */
    public ActionResult assignSeat(String attendeeId, String seatId) {
        Attendee a = project.getAttendee(attendeeId);
        Seat target = hasHall() ? project.getHall().getSeat(seatId) : null;
        if (a == null || target == null) {
            return ActionResult.rejected("Select both an attendee and a seat.");
        }
        Seat current = seatOf(a);
        if (current == target) {
            return ActionResult.ok(a.getId() + " is already in seat " + target.getId() + ".");
        }
        if (target.isOccupied()) {
            Attendee occ = occupantOf(target);
            return ActionResult.rejected("Seat " + target.getId() + " is already occupied by " + occ.getId() + " (" + occ.getFullName()
                    + "). Two attendees can never share a seat - use Swap to exchange their seats.");
        }
        String precheck = precheckSeat(a, target, "assign");
        if (precheck != null) {
            return ActionResult.rejected(precheck);
        }
        String verb = current == null ? "Assigned" : "Moved";
        ActionResult result = applyIfValid(validator.companionClosure(project, a.getId()),
                () -> project.getAssignment().assign(a.getId(), target),
                verb + " " + a.getId() + " to seat " + target.getId() + ".");
        if (!result.isSuccess()) {
            ActionResult withProposal = proposeCompanionMove(a, target, current, result.getMessage());
            if (withProposal != null) {
                return withProposal;
            }
            recordRejection(a, result.getMessage());
        }
        return result;
    }

    /** Moves an attendee and their companion together (accepting a proposal). */
    public ActionResult assignPair(String attendeeId, String seatId, String partnerId, String partnerSeatId) {
        Attendee a = project.getAttendee(attendeeId);
        Attendee b = project.getAttendee(partnerId);
        Seat sa = hasHall() ? project.getHall().getSeat(seatId) : null;
        Seat sb = hasHall() ? project.getHall().getSeat(partnerSeatId) : null;
        if (a == null || b == null || sa == null || sb == null) {
            return ActionResult.rejected("The proposed move is no longer valid.");
        }
        for (Seat s : List.of(sa, sb)) {
            if (s.isOccupied() && !s.getOccupantId().equals(a.getId()) && !s.getOccupantId().equals(b.getId())) {
                return ActionResult.rejected("Seat " + s.getId() + " has become occupied.");
            }
        }
        String pre = precheckSeat(a, sa, "assign");
        if (pre == null) {
            pre = precheckSeat(b, sb, "assign");
        }
        if (pre != null) {
            return ActionResult.rejected(pre);
        }
        Set<String> affected = new LinkedHashSet<>(validator.companionClosure(project, a.getId()));
        affected.addAll(validator.companionClosure(project, b.getId()));
        SeatingAssignment asg = project.getAssignment();
        return applyIfValid(affected, () -> {
            asg.unassign(a.getId());
            asg.unassign(b.getId());
            asg.assign(a.getId(), sa);
            asg.assign(b.getId(), sb);
        }, "Seated " + a.getId() + " in " + sa.getId() + " and companion " + b.getId() + " beside them in " + sb.getId() + ".");
    }

    /** Exchanges the seats of two seated attendees. */
    public ActionResult swap(String firstId, String secondId) {
        Attendee a = project.getAttendee(firstId);
        Attendee b = project.getAttendee(secondId);
        if (a == null || b == null || a == b) {
            return ActionResult.rejected("Select two different attendees to swap.");
        }
        Seat sa = seatOf(a);
        Seat sb = seatOf(b);
        if (sa == null || sb == null) {
            return ActionResult.rejected("Both attendees must already be seated to swap. Use Assign for unseated attendees.");
        }
        String pre = precheckSeat(a, sb, "swap");
        if (pre == null) {
            pre = precheckSeat(b, sa, "swap");
        }
        if (pre != null) {
            return ActionResult.rejected(pre);
        }
        Set<String> affected = new LinkedHashSet<>(validator.companionClosure(project, a.getId()));
        affected.addAll(validator.companionClosure(project, b.getId()));
        SeatingAssignment asg = project.getAssignment();
        ActionResult result = applyIfValid(affected, () -> {
            asg.unassign(a.getId());
            asg.unassign(b.getId());
            asg.assign(a.getId(), sb);
            asg.assign(b.getId(), sa);
        }, "Swapped " + a.getId() + " (now " + sb.getId() + ") and " + b.getId() + " (now " + sa.getId() + ").");
        if (!result.isSuccess()) {
            recordRejection(a, result.getMessage());
        }
        return result;
    }

    /**
     * Unassigns an attendee. Dependents (people who need this attendee beside them) are either
     * unassigned too, or deliberately left seated with their requirement reported as unresolved.
     */
    public ActionResult unassign(String attendeeId, DependentPolicy policy) {
        Attendee a = project.getAttendee(attendeeId);
        if (a == null || seatOf(a) == null) {
            return ActionResult.rejected("That attendee is not currently seated.");
        }
        Seat old = seatOf(a);
        project.getAssignment().unassign(a.getId());
        List<String> also = new ArrayList<>();
        if (policy == DependentPolicy.UNASSIGN_DEPENDENTS) {
            for (Attendee d : dependentsOf(a.getId())) {
                if (project.getAssignment().isAssigned(d.getId())) {
                    project.getAssignment().unassign(d.getId());
                    also.add(d.getId());
                }
            }
        }
        changed();
        String msg = "Unassigned " + a.getId() + " from seat " + old.getId() + ".";
        if (!also.isEmpty()) {
            msg += " Also unassigned dependent(s): " + String.join(", ", also) + ".";
        }
        return ActionResult.ok(msg);
    }

    /** Seated attendees whose companion requirement would break if this attendee were unassigned. */
    public List<Attendee> seatedDependentsOf(String attendeeId) {
        List<Attendee> list = new ArrayList<>();
        for (Attendee d : dependentsOf(attendeeId)) {
            if (project.getAssignment() != null && project.getAssignment().isAssigned(d.getId())) {
                list.add(d);
            }
        }
        return list;
    }

    // ------------------------------------------------------------------ conflicts

    public void revalidate() {
        changed();
    }

    public String markConflictResolved(String conflictId) {
        String error = conflicts.markResolved(conflictId, project, validator);
        notifyListeners();
        return error;
    }

    public String acknowledgeConflict(String conflictId) {
        String error = conflicts.acknowledge(conflictId);
        notifyListeners();
        return error;
    }

    public int confirmAllAddressed() {
        int n = conflicts.confirmAllAddressed(project, validator);
        notifyListeners();
        return n;
    }

    public int purgeResolvedConflicts() {
        int n = conflicts.purgeResolved();
        notifyListeners();
        return n;
    }

    // ------------------------------------------------------------------ internals

    private EventHall requireHall() {
        if (!hasHall()) {
            throw new IllegalStateException("No hall has been configured yet.");
        }
        return project.getHall();
    }

    /** Seat-level hard checks shared by assign/move/swap; returns an explanation or null. */
    private String precheckSeat(Attendee a, Seat target, String action) {
        if (target.getType() == SeatType.RESERVED && !target.isReservedFor(a.getId())) {
            String holder = target.getReservedFor().isEmpty() ? "" : " for " + target.getReservedFor();
            String msg = "Seat " + target.getId() + " is reserved" + holder + " and cannot be given to " + a.getId() + ".";
            conflicts.record(ConflictCategory.RESERVED_BLOCKED, ConflictSeverity.WARNING, List.of(a.getId()),
                    "Manual " + action + " blocked: " + msg + " The layout was not changed.",
                    "Choose another seat, or change the seat type on the Hall Setup screen.");
            notifyListeners();
            return msg;
        }
        String reason = validator.unsuitableReason(a, target, project.getHall());
        if (reason != null) {
            recordRejection(a, reason);
            return "This change would violate a mandatory requirement:\n" + reason;
        }
        return null;
    }

    /**
     * Applies a mutation, then checks the affected attendees for NEW hard violations. If any appear
     * the mutation is rolled back - the layout is never left in a state worse than before.
     */
    private ActionResult applyIfValid(Set<String> affected, Runnable mutation, String successMessage) {
        SeatingAssignment asg = project.getAssignment();
        Map<String, String> snapshot = asg.snapshot();
        Set<String> before = new HashSet<>();
        for (Violation v : validator.violationsFor(project, affected)) {
            before.add(v.key());
        }
        try {
            mutation.run();
        } catch (RuntimeException ex) {
            asg.restore(snapshot);
            return ActionResult.rejected(ex.getMessage());
        }
        List<String> problems = new ArrayList<>();
        for (Violation v : validator.violationsFor(project, affected)) {
            if (!before.contains(v.key())) {
                problems.add(v.message());
            }
        }
        if (!problems.isEmpty()) {
            asg.restore(snapshot);
            return ActionResult.rejected("This change would break a mandatory requirement:\n- " + String.join("\n- ", problems));
        }
        changed();
        return ActionResult.ok(successMessage);
    }

    /**
     * When moving an attendee breaks a companion pairing, look for a free seat beside the target
     * for the partner so the organizer can move both together.
     */
    private ActionResult proposeCompanionMove(Attendee a, Seat target, Seat current, String reason) {
        for (String partnerId : validator.companionClosure(project, a.getId())) {
            if (partnerId.equals(a.getId())) {
                continue;
            }
            Attendee partner = project.getAttendee(partnerId);
            for (Seat n : project.getHall().rowNeighbours(target)) {
                boolean free = !n.isOccupied() || n.getOccupantId().equals(partnerId) || n == current;
                if (free && validator.isSuitable(partner, n, project.getHall())) {
                    return ActionResult.rejectedWithProposal(reason, partnerId, n.getId());
                }
            }
        }
        return null;
    }

    private void recordRejection(Attendee a, String reason) {
        conflicts.record(ConflictCategory.INVALID_MANUAL_CHANGE, ConflictSeverity.WARNING, List.of(a.getId()),
                "Manual change for " + a.getId() + " was blocked: " + reason.replace("\n", " ") + " The layout was not changed.",
                "Choose a seat that satisfies the attendee's mandatory requirements.");
        notifyListeners();
    }

    /** Re-validates, refreshes cached statuses and notifies listeners. */
    private void changed() {
        conflicts.reconcile(validator.detect(project));
        rebuildStatusCache();
        notifyListeners();
    }

    private void rebuildStatusCache() {
        Map<String, AssignmentStatus> map = new HashMap<>();
        Set<String> unresolved = new HashSet<>();
        for (Conflict c : conflicts.getConflicts()) {
            if (c.isLive() && c.isOpen() && c.getSeverity() == ConflictSeverity.ERROR && c.getCategory().isMandatory()
                    && c.getCategory() != ConflictCategory.CAPACITY_EXCEEDED) {
                unresolved.add(c.getPrimaryAttendee());
            }
        }
        for (Attendee a : project.getAttendees()) {
            if (unresolved.contains(a.getId())) {
                map.put(a.getId(), AssignmentStatus.UNRESOLVED);
            } else if (project.getAssignment() != null && project.getAssignment().isAssigned(a.getId())) {
                map.put(a.getId(), AssignmentStatus.ASSIGNED);
            } else {
                map.put(a.getId(), AssignmentStatus.UNASSIGNED);
            }
        }
        statusCache = map;
    }

    private void notifyListeners() {
        for (Runnable r : listeners) {
            r.run();
        }
    }
}
