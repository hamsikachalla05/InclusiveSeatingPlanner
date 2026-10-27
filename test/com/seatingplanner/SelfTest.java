package com.seatingplanner;

import com.seatingplanner.model.AccessibilityNeed;
import com.seatingplanner.model.AssignmentStatus;
import com.seatingplanner.model.Attendee;
import com.seatingplanner.model.Conflict;
import com.seatingplanner.model.ConflictCategory;
import com.seatingplanner.model.EventHall;
import com.seatingplanner.model.Project;
import com.seatingplanner.model.ResolutionStatus;
import com.seatingplanner.model.Seat;
import com.seatingplanner.model.SeatType;
import com.seatingplanner.service.ActionResult;
import com.seatingplanner.service.Csv;
import com.seatingplanner.service.DemoData;
import com.seatingplanner.service.ExportService;
import com.seatingplanner.service.FileStorageService;
import com.seatingplanner.service.HallConfig;
import com.seatingplanner.service.InvalidConfigurationException;
import com.seatingplanner.service.SeatingPlanner;
import com.seatingplanner.service.StorageException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dependency-free test suite for the business logic (no JUnit needed).
 * Run with: run-tests.bat  (or java -cp out com.seatingplanner.SelfTest)
 */
public final class SelfTest {

    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        test("Hall generates unique seat IDs and aisle adjacency", SelfTest::hallBasics);
        test("Config parser expands ranges and rejects bad input", SelfTest::configParsing);
        test("Accessible attendees get accessible seats first", SelfTest::accessibleFirst);
        test("Insufficient accessible seats -> attendee left unassigned with ERROR", SelfTest::accessibleShortage);
        test("Companion pairs are seated adjacently, never across an aisle", SelfTest::companionAdjacent);
        test("Companion with no adjacent pair stays unassigned with ERROR", SelfTest::companionImpossible);
        test("Invalid companion reference is reported", SelfTest::invalidCompanion);
        test("Groups are seated together when possible", SelfTest::groupTogether);
        test("Oversized group is reported as split WARNING", SelfTest::groupSplit);
        test("Reserved seats are respected and honoured", SelfTest::reservedSeats);
        test("Capacity overflow is reported", SelfTest::capacity);
        test("Manual changes that break hard constraints are rejected", SelfTest::manualValidation);
        test("Swap validates both attendees", SelfTest::swapValidation);
        test("Reallocation preserves valid assignments", SelfTest::preserve);
        test("Allocation is deterministic", SelfTest::deterministic);
        test("Conflict lifecycle: open -> addressed -> resolved", SelfTest::conflictLifecycle);
        test("Save/load round trip preserves everything", SelfTest::roundTrip);
        test("Malformed project files fail gracefully", SelfTest::malformed);
        test("CSV escaping handles commas, quotes and formulas", SelfTest::csv);
        test("Attendee CSV import parses quoted fields and requirements", SelfTest::csvImport);
        test("Demo project allocates without violating hard constraints", SelfTest::demoProject);

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ tests

    private static void hallBasics() {
        EventHall h = new EventHall("T", 30, 10, List.of(5));
        Set<String> ids = new HashSet<>();
        for (Seat s : h.getSeats()) {
            check(ids.add(s.getId()), "duplicate " + s.getId());
        }
        check(h.getSeat("AD3") != null, "row 30 should be AD");
        check(h.areAdjacent(h.getSeat("A1"), h.getSeat("A2")), "A1-A2 adjacent");
        check(!h.areAdjacent(h.getSeat("A5"), h.getSeat("A6")), "aisle separates A5|A6");
        check(h.isAisleSeat(h.getSeat("A5")) && h.isAisleSeat(h.getSeat("A6")) && !h.isAisleSeat(h.getSeat("A3")), "aisle seats");
        expectThrows(() -> new EventHall("T", 0, 5, List.of()));
        expectThrows(() -> new EventHall("T", 5, 5, List.of(5)));
    }

    private static void configParsing() throws Exception {
        HallConfig c = HallConfig.parse("E", 4, 8, "4", "A1-A3, d8", "B2:A007, C1-C2");
        check(c.accessibleSeats().equals(Set.of("A1", "A2", "A3", "D8")), "accessible " + c.accessibleSeats());
        check("A007".equals(c.reservedSeats().get("B2")), "holder");
        EventHall h = c.build();
        check(HallConfig.describeSeats(h, SeatType.ACCESSIBLE, false).equals("A1-A3, D8"), HallConfig.describeSeats(h, SeatType.ACCESSIBLE, false));
        for (String[] bad : new String[][]{{"", "", ""}, {"9", "", ""}, {"", "Z1", ""}, {"", "A1", "A1"}, {"", "A1, A1", ""}, {"", "A1-B2", ""}}) {
            try {
                HallConfig.parse("E", 4, 8, bad[0], bad[1], bad[2]);
                if (!(bad[0].isEmpty() && bad[1].isEmpty() && bad[2].isEmpty())) {
                    throw new AssertionError("should reject " + String.join("|", bad));
                }
            } catch (InvalidConfigurationException expected) {
                check(!expected.getProblems().isEmpty(), "problems listed");
            }
        }
    }

    private static void accessibleFirst() throws Exception {
        SeatingPlanner p = planner(3, 6, "", "C6", "");
        for (int i = 1; i <= 10; i++) {
            p.addAttendee(new Attendee("P" + i, "Person " + i));
        }
        Attendee w = new Attendee("W1", "Wheel User");
        w.setAccessibilityNeed(AccessibilityNeed.ACCESSIBLE_SEAT);
        p.addAttendee(w);
        p.autoAssign(false);
        check(p.seatOf(w).getId().equals("C6"), "W1 should be in C6 but is " + p.seatOf(w));
        for (int i = 1; i <= 10; i++) {
            check(p.seatOf(p.getProject().getAttendee("P" + i)).getType() == SeatType.STANDARD, "P" + i + " should not use accessible seat");
        }
    }

    private static void accessibleShortage() throws Exception {
        SeatingPlanner p = planner(2, 4, "", "A1", "");
        for (int i = 1; i <= 2; i++) {
            Attendee a = new Attendee("W" + i, "Wheel " + i);
            a.setAccessibilityNeed(AccessibilityNeed.ACCESSIBLE_SEAT);
            p.addAttendee(a);
        }
        p.autoAssign(false);
        Attendee w2 = p.getProject().getAttendee("W2");
        check(p.seatOf(w2) == null, "W2 must stay unassigned, not be put in a standard seat");
        check(p.statusOf(w2) == AssignmentStatus.UNRESOLVED, "W2 unresolved");
        Conflict c = openConflict(p, ConflictCategory.INSUFFICIENT_ACCESSIBLE, "W2");
        check(c.formatMessage().startsWith("ERROR: No accessible seat is available for attendee W2"), c.formatMessage());
        check(!p.isPlanFullyValid(), "plan must not claim validity");
    }

    private static void companionAdjacent() throws Exception {
        // Two-seat blocks only (aisles after 2 and 4): pair must land inside a block.
        SeatingPlanner p = planner(1, 6, "2, 4", "", "");
        p.addAttendee(new Attendee("X", "Filler"));
        Attendee d = new Attendee("D", "Dependent");
        d.setRequiresCompanion(true);
        d.setCompanionId("C");
        p.addAttendee(d);
        p.addAttendee(new Attendee("C", "Companion"));
        p.autoAssign(false);
        check(p.getHall().areAdjacent(p.seatOf(d), p.seatOf(p.getProject().getAttendee("C"))), "pair adjacent");
        check(p.statusOf(d) == AssignmentStatus.ASSIGNED, "D assigned");
    }

    private static void companionImpossible() throws Exception {
        // Every seat is its own block: no adjacency exists at all.
        SeatingPlanner p = planner(1, 3, "1, 2", "", "");
        Attendee d = new Attendee("A008", "Dependent");
        d.setRequiresCompanion(true);
        d.setCompanionId("A009");
        p.addAttendee(d);
        p.addAttendee(new Attendee("A009", "Companion"));
        p.autoAssign(false);
        check(p.seatOf(d) == null, "dependent must not be seated without companion beside them");
        Conflict c = openConflict(p, ConflictCategory.COMPANION_NO_ADJACENT, "A008");
        check(c.formatMessage().equals("ERROR: Attendee A008 requires a companion, but no suitable adjacent seat is available."), c.formatMessage());
    }

    private static void invalidCompanion() throws Exception {
        SeatingPlanner p = planner(2, 4, "", "", "");
        Attendee d = new Attendee("D", "Dependent");
        d.setRequiresCompanion(true);
        d.setCompanionId("NOPE");
        p.addAttendee(d);
        p.autoAssign(false);
        check(p.seatOf(d) == null, "not seated");
        openConflict(p, ConflictCategory.COMPANION_INVALID, "D");
    }

    private static void groupTogether() throws Exception {
        SeatingPlanner p = planner(4, 8, "4", "", "");
        for (int i = 1; i <= 3; i++) {
            p.addAttendee(member("S" + i, ""));
        }
        for (int i = 1; i <= 4; i++) {
            p.addAttendee(member("G" + i, "TEAM"));
        }
        p.autoAssign(false);
        List<Seat> seats = p.getProject().getGroupMembers("TEAM").stream().map(p::seatOf).toList();
        check(p.getValidator().clusterCount(p.getHall(), seats) == 1, "team in one cluster: " + seats);
        int row = seats.get(0).getRow();
        check(seats.stream().allMatch(s -> s.getRow() == row), "team in one row");
        check(noConflict(p, ConflictCategory.GROUP_SPLIT), "no split warning");
    }

    private static void groupSplit() throws Exception {
        // 3 rows x 4 seats split in blocks of 2; a group of 4 in a hall already partly full must split.
        SeatingPlanner p = planner(2, 4, "2", "", "");
        for (int i = 1; i <= 7; i++) {
            p.addAttendee(member("G" + i, "BIG"));
        }
        p.addAttendee(member("Z1", "SMALL"));
        p.addAttendee(member("Z2", "SMALL"));
        p.autoAssign(false);
        Conflict c = openConflict(p, ConflictCategory.GROUP_SPLIT, null);
        check(c.getSeverity().name().equals("WARNING"), "warning");
    }

    private static void reservedSeats() throws Exception {
        SeatingPlanner p = planner(1, 4, "", "", "A1:VIP1, A2:Staff");
        p.addAttendee(member("VIP1", ""));
        p.addAttendee(member("N1", ""));
        p.addAttendee(member("N2", ""));
        p.addAttendee(member("N3", ""));
        p.autoAssign(false);
        check(p.seatOf(p.getProject().getAttendee("VIP1")).getId().equals("A1"), "VIP gets their reserved seat");
        check(p.getHall().getSeat("A2").getOccupantId() == null, "staff seat stays empty");
        ActionResult r = p.assignSeat("N3", "A2");
        check(!r.isSuccess() && r.getMessage().contains("reserved"), "manual move into reserved seat rejected");
    }

    private static void capacity() throws Exception {
        SeatingPlanner p = planner(1, 3, "", "", "");
        for (int i = 1; i <= 5; i++) {
            p.addAttendee(member("P" + i, ""));
        }
        p.autoAssign(false);
        check(p.getProject().getAssignment().size() == 3, "3 seated");
        openConflict(p, ConflictCategory.CAPACITY_EXCEEDED, null);
    }

    private static void manualValidation() throws Exception {
        SeatingPlanner p = planner(2, 6, "3", "A1", "");
        Attendee w = new Attendee("W", "Wheel");
        w.setAccessibilityNeed(AccessibilityNeed.ACCESSIBLE_SEAT);
        p.addAttendee(w);
        Attendee d = new Attendee("D", "Dep");
        d.setRequiresCompanion(true);
        d.setCompanionId("C");
        p.addAttendee(d);
        p.addAttendee(new Attendee("C", "Comp"));
        p.autoAssign(false);
        check(!p.assignSeat("W", "B5").isSuccess(), "accessible attendee can't move to standard seat");
        check(p.seatOf(w).getId().equals("A1"), "W unchanged");
        Seat before = p.seatOf(d);
        ActionResult r = p.assignSeat("D", "B6");
        check(!r.isSuccess(), "moving dependent away from companion rejected");
        check(r.hasProposal() && r.getProposedAttendeeId().equals("C") && r.getProposedSeatId().equals("B5"), "proposes C -> B5");
        check(p.seatOf(d) == before, "layout unchanged after rejection");
        check(p.assignPair("D", "B6", "C", "B5").isSuccess(), "pair move accepted");
        check(p.getHall().areAdjacent(p.seatOf(d), p.seatOf(p.getProject().getAttendee("C"))), "still adjacent");
        check(!p.assignSeat("C", "A1").isSuccess(), "occupied seat rejected");
    }

    private static void swapValidation() throws Exception {
        SeatingPlanner p = planner(1, 4, "", "A1", "");
        Attendee w = new Attendee("W", "Wheel");
        w.setAccessibilityNeed(AccessibilityNeed.ACCESSIBLE_SEAT);
        p.addAttendee(w);
        p.addAttendee(member("N", ""));
        p.addAttendee(member("M", ""));
        p.autoAssign(false);
        check(!p.swap("W", "N").isSuccess(), "swap would put W in a standard seat");
        check(p.swap("N", "M").isSuccess(), "plain swap allowed");
    }

    private static void preserve() throws Exception {
        SeatingPlanner p = planner(3, 5, "", "", "");
        for (int i = 1; i <= 5; i++) {
            p.addAttendee(member("P" + i, ""));
        }
        p.autoAssign(false);
        check(p.assignSeat("P1", "C5").isSuccess(), "manual move");
        p.addAttendee(member("P6", ""));
        p.autoAssign(true);
        check(p.seatOf(p.getProject().getAttendee("P1")).getId().equals("C5"), "manual seat preserved");
        check(p.getProject().getAssignment().size() == 6, "new attendee seated");
    }

    private static void deterministic() {
        String first = null;
        for (int run = 0; run < 3; run++) {
            SeatingPlanner p = new SeatingPlanner();
            p.replaceProject(DemoData.create(), List.of(), 1);
            p.autoAssign(false);
            String snapshot = p.getProject().getAssignment().asMap().toString();
            if (first == null) {
                first = snapshot;
            } else {
                check(first.equals(snapshot), "allocation differs between runs");
            }
        }
    }

    private static void conflictLifecycle() throws Exception {
        SeatingPlanner p = planner(1, 2, "", "", "");
        p.addAttendee(member("P1", ""));
        p.addAttendee(member("P2", ""));
        p.addAttendee(member("P3", ""));
        p.autoAssign(false);
        Conflict cap = openConflict(p, ConflictCategory.CAPACITY_EXCEEDED, null);
        check(p.markConflictResolved(cap.getId()) != null, "cannot resolve while still present");
        check(p.acknowledgeConflict(cap.getId()) != null, "cannot acknowledge an error");
        p.removeAttendee("P3");
        check(cap.getStatus() == ResolutionStatus.ADDRESSED, "addressed after fix: " + cap.getStatus());
        check(p.markConflictResolved(cap.getId()) == null, "resolve after fix");
        check(cap.getStatus() == ResolutionStatus.RESOLVED, "resolved");
        p.addAttendee(member("P4", ""));
        check(cap.getStatus() == ResolutionStatus.OPEN, "reopened when issue returns");
    }

    private static void roundTrip() throws Exception {
        SeatingPlanner p = new SeatingPlanner();
        p.replaceProject(DemoData.create(), List.of(), 1);
        p.autoAssign(false);
        Attendee tricky = new Attendee("Q1", "O'Neil, \"Jo\" <b>");
        tricky.setNotes("line1\nline2");
        p.addAttendee(tricky);
        Path tmp = Files.createTempFile("seating-test", ".json");
        FileStorageService fs = new FileStorageService();
        fs.save(p, tmp);
        FileStorageService.LoadResult lr = fs.load(tmp);
        SeatingPlanner q = new SeatingPlanner();
        List<String> released = q.replaceProject(lr.project(), lr.conflicts(), lr.nextConflictNumber());
        check(released.isEmpty(), "nothing released: " + released);
        check(lr.warnings().isEmpty(), "no warnings: " + lr.warnings());
        check(q.getProject().getAssignment().asMap().equals(p.getProject().getAssignment().asMap()), "assignments equal");
        check(q.getProject().getAttendee("Q1").getFullName().equals(tricky.getFullName()), "name preserved");
        check(q.getProject().getAttendee("Q1").getNotes().equals("line1\nline2"), "notes preserved");
        check(q.getHall().getSeat("B7").isReservedFor("A003"), "reservation preserved");
        check(q.getConflictManager().getConflicts().size() == p.getConflictManager().getConflicts().size(), "conflicts preserved");
        Files.deleteIfExists(tmp);
    }

    private static void malformed() throws Exception {
        FileStorageService fs = new FileStorageService();
        Path tmp = Files.createTempFile("seating-bad", ".json");
        for (String bad : new String[]{"", "{", "[1,2]", "{\"format\":\"Other\"}", "{\"format\":\"InclusiveSeatingPlanner\",\"hall\":{\"rows\":-3}}"}) {
            Files.writeString(tmp, bad);
            try {
                fs.load(tmp);
                throw new AssertionError("should fail: " + bad);
            } catch (StorageException expected) {
                check(!expected.getMessage().isBlank(), "message");
            }
        }
        Files.writeString(tmp, "{\"format\":\"InclusiveSeatingPlanner\",\"version\":1,"
                + "\"hall\":{\"eventName\":\"X\",\"rows\":2,\"seatsPerRow\":3,\"aisleAfter\":[],\"specialSeats\":[]},"
                + "\"attendees\":[{\"id\":\"A1\",\"fullName\":\"Ann\"},{\"id\":\"A1\",\"fullName\":\"Dup\"},{\"id\":\"\"},5],"
                + "\"assignments\":[{\"attendeeId\":\"A1\",\"seatId\":\"Z9\"},{\"attendeeId\":\"GHOST\",\"seatId\":\"A1\"}]}");
        FileStorageService.LoadResult lr = fs.load(tmp);
        check(lr.project().getAttendeeCount() == 1, "one valid attendee");
        check(lr.warnings().size() >= 4, "warnings " + lr.warnings());
        try {
            fs.load(tmp.resolveSibling("does-not-exist-" + System.nanoTime() + ".json"));
            throw new AssertionError("missing file should fail");
        } catch (StorageException expected) {
            check(expected.getMessage().contains("not found"), expected.getMessage());
        }
        Files.deleteIfExists(tmp);
    }

    private static void csv() {
        check(Csv.escape("plain").equals("plain"), "plain");
        check(Csv.escape("Smith, Jo").equals("\"Smith, Jo\""), "comma");
        check(Csv.escape("Jo \"JJ\" Smith").equals("\"Jo \"\"JJ\"\" Smith\""), "quotes");
        check(Csv.escape("=SUM(A1)").equals("'=SUM(A1)"), "formula");
        List<List<String>> rows = Csv.parse("a,\"b,c\",\"d \"\"e\"\"\"\r\n\"multi\nline\",x,\n");
        check(rows.size() == 2 && rows.get(0).get(1).equals("b,c") && rows.get(0).get(2).equals("d \"e\""), "parse " + rows);
        check(rows.get(1).get(0).equals("multi\nline") && rows.get(1).size() == 3, "multiline " + rows);
        SeatingPlanner p = new SeatingPlanner();
        p.replaceProject(DemoData.create(), List.of(), 1);
        p.autoAssign(false);
        String out = new ExportService().buildAssignmentsCsv(p);
        List<List<String>> parsed = Csv.parse(out);
        check(parsed.size() == p.getProject().getAttendeeCount() + 1, "row count");
        for (List<String> r : parsed) {
            check(r.size() == 12, "column count " + r);
        }
    }

    private static void csvImport() throws Exception {
        Path sample = Path.of("sample-data", "attendees-sample.csv");
        check(Files.exists(sample), "run from the project folder so " + sample + " is found");
        FileStorageService.ImportResult r = new FileStorageService().importAttendeesCsv(sample, new Project());
        check(r.attendees().size() == 12 && r.warnings().isEmpty(), "12 rows, no warnings: " + r.warnings());
        Attendee b1 = r.attendees().get(0);
        check(b1.getFullName().equals("Okafor, Chidi"), "comma in name: " + b1.getFullName());
        check(b1.getAccessibilityNeed() == AccessibilityNeed.ACCESSIBLE_SEAT && b1.isRequiresCompanion()
                && b1.getCompanionId().equals("B002"), "B001 requirements");
        check(r.attendees().get(2).getFullName().equals("Sam \"Sunny\" Rivera"), "quotes in name");
        check(r.attendees().get(5).getAccessibilityNeed() == AccessibilityNeed.AISLE_SEAT, "aisle need");
        check(r.attendees().get(7).getPreferredRow().equals("C"), "preferred row");
    }

    private static void demoProject() {
        SeatingPlanner p = new SeatingPlanner();
        p.replaceProject(DemoData.create(), List.of(), 1);
        p.autoAssign(false);
        Project proj = p.getProject();
        for (Attendee a : proj.getAttendees()) {
            check(p.getValidator().violationsFor(proj, a).isEmpty(), "violation for " + a.getId() + ": " + p.getValidator().violationsFor(proj, a));
        }
        openConflict(p, ConflictCategory.INSUFFICIENT_ACCESSIBLE, "A055");
        openConflict(p, ConflictCategory.COMPANION_INVALID, "A070");
        check(proj.getAttendee("A003") != null && p.seatOf(proj.getAttendee("A003")).getId().equals("B7"), "A003 in reserved B7");
        check(!p.isPlanFullyValid(), "demo has errors so must not claim validity");
        System.out.println("    demo: " + proj.getAssignment().size() + "/" + proj.getAttendeeCount() + " seated, "
                + p.getConflictManager().countUnresolved() + " unresolved conflicts");
        for (Conflict c : p.getConflictManager().getConflicts()) {
            if (c.isOpen()) {
                System.out.println("      " + c.getId() + " " + c.formatMessage());
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static SeatingPlanner planner(int rows, int seats, String aisles, String accessible, String reserved) throws Exception {
        SeatingPlanner p = new SeatingPlanner();
        p.applyHallConfig(HallConfig.parse("Test", rows, seats, aisles, accessible, reserved));
        return p;
    }

    private static Attendee member(String id, String group) {
        Attendee a = new Attendee(id, "Name " + id);
        a.setGroupId(group);
        return a;
    }

    private static Conflict openConflict(SeatingPlanner p, ConflictCategory cat, String attendee) {
        for (Conflict c : p.getConflictManager().getConflicts()) {
            if (c.isOpen() && c.getCategory() == cat && (attendee == null || c.getAffectedAttendees().contains(attendee))) {
                return c;
            }
        }
        throw new AssertionError("expected open " + cat + " conflict" + (attendee == null ? "" : " for " + attendee));
    }

    private static boolean noConflict(SeatingPlanner p, ConflictCategory cat) {
        return p.getConflictManager().getConflicts().stream().noneMatch(c -> c.isOpen() && c.getCategory() == cat);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expectThrows(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError("expected an exception");
    }

    private interface TestBody {
        void run() throws Exception;
    }

    private static void test(String name, TestBody body) {
        try {
            body.run();
            passed++;
            System.out.println("  PASS  " + name);
        } catch (Throwable t) {
            failed++;
            System.out.println("  FAIL  " + name + "\n        " + t);
        }
    }
}
