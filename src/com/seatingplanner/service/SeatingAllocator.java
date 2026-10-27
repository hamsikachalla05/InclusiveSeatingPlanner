package com.seatingplanner.service;

import com.seatingplanner.model.AccessibilityNeed;
import com.seatingplanner.model.Attendee;
import com.seatingplanner.model.EventHall;
import com.seatingplanner.model.Ids;
import com.seatingplanner.model.Project;
import com.seatingplanner.model.Seat;
import com.seatingplanner.model.SeatType;
import com.seatingplanner.model.SeatingAssignment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic, greedy seating allocation.
 *
 * <h2>Strategy</h2>
 * <ol>
 *   <li><b>Clean up</b> - optionally keep existing assignments, but release any that are no longer valid.</li>
 *   <li><b>Reservations</b> - seats reserved for a specific attendee ID are given to that attendee.</li>
 *   <li><b>Free misused accessible seats</b> - if more people need accessible seats than are free,
 *       attendees without that need are moved out of accessible seats.</li>
 *   <li><b>Hard units first</b> - companion pairs and attendees with accessibility needs, scarcest
 *       resource first (accessible &gt; front &gt; aisle &gt; companion-only), pairs before singles.
 *       A pair is only ever placed on two adjacent seats; if that is impossible it stays unassigned.</li>
 *   <li><b>Groups</b> - largest group first; try one contiguous run in a single row, otherwise grow a
 *       compact cluster around members already seated.</li>
 *   <li><b>Everyone else</b> - by attendee ID.</li>
 * </ol>
 * Every candidate seat is scored (lower is better) and seats are scanned in row-major order with a
 * strict "less than" comparison, so identical input always yields the identical plan.
 * Hard constraints are filters (a seat is never considered unless it is suitable); soft constraints
 * only add score penalties, and anything left unmet is reported by {@link ConstraintValidator#detect}.
 */
public class SeatingAllocator {

    /** Penalty for giving an accessible seat to someone who does not need one. */
    private static final double ACCESSIBLE_WASTE_PENALTY = 1000;
    /** Penalty for missing a preferred row/area. */
    private static final double PREFERENCE_PENALTY = 100;
    /** Penalty per step of distance from the nearest seated group member. */
    private static final double GROUP_DISTANCE_PENALTY = 10;
    /** Bonus per free seat next to a placement that remaining group members could use. */
    private static final double HEADROOM_BONUS = 2;

    private final ConstraintValidator validator;

    public SeatingAllocator(ConstraintValidator validator) {
        this.validator = validator;
    }

    /** Summary of one allocation run. */
    public record AllocationResult(int keptAssignments, int releasedInvalid, int relocatedFromAccessible,
                                   int newlyAssigned, int assignedTotal, int unassignedTotal) {
    }

    private record Unit(List<Attendee> members, int priority) {
    }

    public AllocationResult allocate(Project p, boolean preserveExisting) {
        SeatingAssignment asg = p.getAssignment();
        if (!preserveExisting) {
            asg.clear();
        }
        int released = validator.releaseInvalidAssignments(p).size();
        int kept = asg.size();

        honourReservations(p);
        int relocated = freeMisusedAccessibleSeats(p);

        for (Unit unit : buildHardUnits(p)) {
            placeHardUnit(p, unit);
        }

        List<Map.Entry<String, List<Attendee>>> groups = new ArrayList<>(p.getGroups().entrySet());
        groups.sort(Comparator.<Map.Entry<String, List<Attendee>>>comparingInt(e -> -pending(p, e.getValue()).size())
                .thenComparing(Map.Entry::getKey, Ids.NATURAL));
        for (Map.Entry<String, List<Attendee>> group : groups) {
            placeGroup(p, pending(p, group.getValue()));
        }

        for (Attendee a : p.getAttendees()) {
            if (!a.hasGroup() && isPlainPending(p, a)) {
                Seat best = bestSingleSeat(p, a);
                if (best != null) {
                    asg.assign(a.getId(), best);
                }
            }
        }

        int total = asg.size();
        return new AllocationResult(kept, released, relocated, Math.max(0, total - kept), total,
                p.getAttendeeCount() - total);
    }

    // ------------------------------------------------------------------ phases

    private void honourReservations(Project p) {
        for (Seat s : p.getHall().getSeats()) {
            if (s.getType() != SeatType.RESERVED || s.isOccupied()) {
                continue;
            }
            Attendee a = p.getAttendee(s.getReservedFor());
            if (a != null && !p.getAssignment().isAssigned(a.getId()) && validator.isSuitable(a, s, p.getHall())) {
                p.getAssignment().assign(a.getId(), s);
            }
        }
    }

    private int freeMisusedAccessibleSeats(Project p) {
        SeatingAssignment asg = p.getAssignment();
        long demand = p.getAttendees().stream()
                .filter(a -> a.getAccessibilityNeed() == AccessibilityNeed.ACCESSIBLE_SEAT && !asg.isAssigned(a.getId()))
                .count();
        long free = p.getHall().getSeats().stream()
                .filter(s -> s.getType() == SeatType.ACCESSIBLE && !s.isOccupied()).count();
        int relocated = 0;
        for (Seat s : p.getHall().getSeats()) {
            if (demand - free - relocated <= 0) {
                break;
            }
            if (s.getType() != SeatType.ACCESSIBLE || !s.isOccupied()) {
                continue;
            }
            Attendee occ = p.getAttendee(s.getOccupantId());
            // Never break a seated companion pair to do this.
            if (occ != null && occ.getAccessibilityNeed() != AccessibilityNeed.ACCESSIBLE_SEAT
                    && validator.companionClosure(p, occ.getId()).size() == 1) {
                asg.unassign(occ.getId());
                relocated++;
            }
        }
        return relocated;
    }

    private List<Unit> buildHardUnits(Project p) {
        List<Unit> units = new ArrayList<>();
        for (Attendee a : p.getAttendees()) {
            Attendee c = validator.resolveCompanion(p, a);
            if (c != null) {
                int prio = Math.min(a.getAccessibilityNeed().allocationPriority(), c.getAccessibilityNeed().allocationPriority());
                units.add(new Unit(List.of(a, c), prio * 2));
            }
        }
        for (Attendee a : p.getAttendees()) {
            if (a.hasAccessibilityNeed() && !a.isRequiresCompanion()) {
                units.add(new Unit(List.of(a), a.getAccessibilityNeed().allocationPriority() * 2 + 1));
            }
        }
        // stable sort keeps attendee-ID order within the same priority
        units.sort(Comparator.comparingInt(Unit::priority));
        return units;
    }

    private void placeHardUnit(Project p, Unit unit) {
        SeatingAssignment asg = p.getAssignment();
        if (unit.members().size() == 1) {
            Attendee a = unit.members().get(0);
            if (!asg.isAssigned(a.getId())) {
                Seat best = bestSingleSeat(p, a);
                if (best != null) {
                    asg.assign(a.getId(), best);
                }
            }
            return;
        }
        Attendee dependent = unit.members().get(0);
        Attendee companion = unit.members().get(1);
        Seat sd = asg.getSeatOf(dependent.getId());
        Seat sc = asg.getSeatOf(companion.getId());
        if (sd != null && sc != null) {
            return; // already seated together (apart pairs were released during clean-up)
        }
        if (sd != null || sc != null) {
            Attendee anchor = sd != null ? dependent : companion;
            Attendee other = sd != null ? companion : dependent;
            Seat anchorSeat = sd != null ? sd : sc;
            Seat beside = bestNeighbour(p, other, anchorSeat);
            if (beside != null) {
                asg.assign(other.getId(), beside);
                return;
            }
            // No room beside the anchor - relocate the anchor too, if that breaks nothing else.
            if (!canRelocate(p, anchor, other)) {
                return;
            }
            asg.unassign(anchor.getId());
            if (!placePairFresh(p, dependent, companion)) {
                asg.assign(anchor.getId(), anchorSeat);
            }
            return;
        }
        placePairFresh(p, dependent, companion);
    }

    /** Places a whole group, preferring a single contiguous run of seats in one row. */
    private void placeGroup(Project p, List<Attendee> pending) {
        if (pending.isEmpty()) {
            return;
        }
        SeatingAssignment asg = p.getAssignment();
        List<Seat> run = bestRun(p, pending);
        if (run != null) {
            for (int i = 0; i < pending.size(); i++) {
                asg.assign(pending.get(i).getId(), run.get(i));
            }
            return;
        }
        // Grow a cluster: each member goes to the best seat near already seated members.
        for (Attendee a : pending) {
            Seat best = bestSingleSeat(p, a);
            if (best == null) {
                return;
            }
            asg.assign(a.getId(), best);
        }
    }

    // ------------------------------------------------------------------ placement search

    private Seat bestSingleSeat(Project p, Attendee a) {
        Seat best = null;
        double bestScore = Double.MAX_VALUE;
        for (Seat s : p.getHall().getSeats()) {
            if (s.isOccupied() || !validator.isSuitable(a, s, p.getHall())) {
                continue;
            }
            double score = score(p, a, s, Set.of());
            if (score < bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    private Seat bestNeighbour(Project p, Attendee a, Seat anchor) {
        Seat best = null;
        double bestScore = Double.MAX_VALUE;
        for (Seat n : p.getHall().rowNeighbours(anchor)) {
            if (!n.isOccupied() && validator.isSuitable(a, n, p.getHall())) {
                double score = score(p, a, n, Set.of());
                if (score < bestScore) {
                    bestScore = score;
                    best = n;
                }
            }
        }
        return best;
    }

    /** Finds the best pair of adjacent seats for a dependent and their companion (either order). */
    private boolean placePairFresh(Project p, Attendee dependent, Attendee companion) {
        EventHall hall = p.getHall();
        Seat bestD = null;
        Seat bestC = null;
        double bestScore = Double.MAX_VALUE;
        int remainingGroup = remainingGroupMembers(p, dependent, Set.of(dependent.getId(), companion.getId()));
        for (Seat left : hall.getSeats()) {
            Seat right = hall.getSeat(left.getRow(), left.getNumber() + 1);
            if (right == null || !hall.areAdjacent(left, right) || left.isOccupied() || right.isOccupied()) {
                continue;
            }
            Seat[][] orientations = {{left, right}, {right, left}};
            for (Seat[] o : orientations) {
                if (!validator.isSuitable(dependent, o[0], hall) || !validator.isSuitable(companion, o[1], hall)) {
                    continue;
                }
                Set<Seat> pair = Set.of(o[0], o[1]);
                double score = score(p, dependent, o[0], pair) + score(p, companion, o[1], pair)
                        - HEADROOM_BONUS * Math.min(remainingGroup, headroom(p, left, right));
                if (score < bestScore) {
                    bestScore = score;
                    bestD = o[0];
                    bestC = o[1];
                }
            }
        }
        if (bestD == null) {
            return false;
        }
        p.getAssignment().assign(dependent.getId(), bestD);
        p.getAssignment().assign(companion.getId(), bestC);
        return true;
    }

    /** Best run of consecutive adjacent free seats in one row for the whole list, or null. */
    private List<Seat> bestRun(Project p, List<Attendee> members) {
        EventHall hall = p.getHall();
        int k = members.size();
        List<Seat> best = null;
        double bestScore = Double.MAX_VALUE;
        for (int r = 1; r <= hall.getRows(); r++) {
            for (int start = 1; start + k - 1 <= hall.getSeatsPerRow(); start++) {
                List<Seat> run = new ArrayList<>(k);
                double score = 0;
                boolean ok = true;
                for (int i = 0; i < k && ok; i++) {
                    Seat s = hall.getSeat(r, start + i);
                    ok = !s.isOccupied() && validator.isSuitable(members.get(i), s, hall)
                            && (i == 0 || hall.areAdjacent(run.get(i - 1), s));
                    if (ok) {
                        run.add(s);
                        score += score(p, members.get(i), s, Set.of());
                    }
                }
                if (ok && score < bestScore) {
                    bestScore = score;
                    best = run;
                }
            }
        }
        return best;
    }

    /**
     * Score of placing an attendee on a seat; lower is better.
     *
     * @param planned seats being planned together with this one (excluded from proximity checks)
     */
    private double score(Project p, Attendee a, Seat seat, Set<Seat> planned) {
        EventHall hall = p.getHall();
        double score = 0;
        if (validator.wastesAccessibleSeat(a, seat)) {
            score += ACCESSIBLE_WASTE_PENALTY;
        }
        if (!validator.preferenceSatisfied(a, seat, hall)) {
            score += PREFERENCE_PENALTY;
        }
        if (a.hasGroup()) {
            int nearest = Integer.MAX_VALUE;
            for (Attendee m : p.getGroupMembers(a.getGroupId())) {
                Seat ms = p.getSeatOf(m);
                if (ms != null && !m.getId().equals(a.getId()) && !planned.contains(ms)) {
                    nearest = Math.min(nearest, hall.distance(seat, ms));
                }
            }
            if (nearest != Integer.MAX_VALUE) {
                score += GROUP_DISTANCE_PENALTY * (nearest - 1);
            }
        }
        return score;
    }

    /** Free standard seats directly continuing the run [left, right] in its row, on both sides. */
    private int headroom(Project p, Seat left, Seat right) {
        EventHall hall = p.getHall();
        int count = 0;
        for (int dir : new int[]{-1, 1}) {
            Seat cur = dir < 0 ? left : right;
            while (true) {
                Seat next = hall.getSeat(cur.getRow(), cur.getNumber() + dir);
                if (next == null || !hall.areAdjacent(cur, next) || next.isOccupied() || next.getType() != SeatType.STANDARD) {
                    break;
                }
                count++;
                cur = next;
            }
        }
        return count;
    }

    private int remainingGroupMembers(Project p, Attendee a, Set<String> exclude) {
        if (!a.hasGroup()) {
            return 0;
        }
        int count = 0;
        for (Attendee m : p.getGroupMembers(a.getGroupId())) {
            if (!exclude.contains(m.getId()) && !p.getAssignment().isAssigned(m.getId())) {
                count++;
            }
        }
        return count;
    }

    /** An anchor may move only if no other seated attendee depends on its current position. */
    private boolean canRelocate(Project p, Attendee anchor, Attendee partner) {
        Seat seat = p.getSeatOf(anchor);
        if (seat.isReservedFor(anchor.getId())) {
            return false;
        }
        for (String linked : validator.companionClosure(p, anchor.getId())) {
            if (!linked.equals(anchor.getId()) && !linked.equals(partner.getId()) && p.getAssignment().isAssigned(linked)) {
                return false;
            }
        }
        return true;
    }

    /** Group members still to place by the group phase (hard-constraint members are handled earlier). */
    private List<Attendee> pending(Project p, List<Attendee> members) {
        List<Attendee> list = new ArrayList<>();
        for (Attendee m : members) {
            if (isPlainPending(p, m)) {
                list.add(m);
            }
        }
        return list;
    }

    /**
     * Unseated attendees without hard requirements. Attendees who require a companion are never
     * seated alone, and attendees with accessibility needs were already given every chance.
     */
    private boolean isPlainPending(Project p, Attendee a) {
        return !p.getAssignment().isAssigned(a.getId()) && !a.isRequiresCompanion() && !a.hasAccessibilityNeed();
    }

    /** IDs of attendees that could not be seated (for reporting). */
    public Set<String> unassignedIds(Project p) {
        Set<String> ids = new HashSet<>();
        for (Attendee a : p.getAttendees()) {
            if (!p.getAssignment().isAssigned(a.getId())) {
                ids.add(a.getId());
            }
        }
        return ids;
    }
}
