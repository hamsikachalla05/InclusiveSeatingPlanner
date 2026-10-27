package com.seatingplanner.service;

import com.seatingplanner.model.AccessibilityNeed;
import com.seatingplanner.model.Attendee;
import com.seatingplanner.model.Project;
import com.seatingplanner.model.SeatingArea;
import com.seatingplanner.model.SeatingAssignment;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A realistic sample project used for demonstrations. It intentionally contains a few problems
 * (one more accessible-seat request than accessible seats, an unregistered companion, a group too
 * large for one seating block) so the conflict screens have something to explain.
 */
public final class DemoData {

    private static final String[] FIRST = {"Aarav", "Maya", "Liam", "Priya", "Noah", "Zara", "Ethan", "Amara", "Lucas",
            "Ananya", "Oliver", "Fatima", "Mateo", "Sofia", "Kenji", "Isla", "Omar", "Chloe", "Ravi", "Hannah",
            "Diego", "Leila", "Samuel", "Mei", "Arjun", "Grace", "Tariq", "Elena", "Kwame", "Nora"};
    private static final String[] LAST = {"Sharma", "Okafor", "Nguyen", "Fernandes", "Kim", "Haddad", "Rossi", "Mensah",
            "Patel", "Schmidt", "Garcia", "O'Brien", "Tanaka", "Iyer", "Kowalski", "Silva", "Reddy", "Dubois",
            "Ahmed", "Lindqvist"};

    private DemoData() {
    }

    public static Project create() {
        Set<String> accessible = new LinkedHashSet<>(List.of("A1", "A2", "A13", "A14", "F1", "F14"));
        Map<String, String> reserved = new LinkedHashMap<>();
        reserved.put("A7", "Speakers");
        reserved.put("A8", "Speakers");
        reserved.put("B7", "A003");
        HallConfig cfg = new HallConfig("Inclusion & Innovation Summit 2026", 9, 14, List.of(4, 10), accessible, reserved);

        Project p = new Project();
        var hall = cfg.build();
        p.setHall(hall, new SeatingAssignment(hall));

        int n = 0;
        // Group sizes: G01 is larger than any seating block (6 seats) so it must split across rows.
        String[] groups = {"G01", "G02", "G03", "G04", "G05", "G06", "G07"};
        int[] sizes = {9, 6, 5, 4, 4, 3, 3};
        for (int g = 0; g < groups.length; g++) {
            for (int k = 0; k < sizes[g]; k++) {
                add(p, ++n, groups[g]);
            }
        }
        while (n < 78) {
            add(p, ++n, "");
        }

        need(p, "A001", AccessibilityNeed.ACCESSIBLE_SEAT);
        companion(p, "A001", "A002");
        need(p, "A011", AccessibilityNeed.ACCESSIBLE_SEAT);
        need(p, "A020", AccessibilityNeed.ACCESSIBLE_SEAT);
        companion(p, "A020", "A021");
        need(p, "A040", AccessibilityNeed.ACCESSIBLE_SEAT);
        need(p, "A045", AccessibilityNeed.ACCESSIBLE_SEAT);
        need(p, "A050", AccessibilityNeed.ACCESSIBLE_SEAT);
        need(p, "A055", AccessibilityNeed.ACCESSIBLE_SEAT); // the 7th request for only 6 accessible seats
        need(p, "A016", AccessibilityNeed.AISLE_SEAT);
        need(p, "A060", AccessibilityNeed.AISLE_SEAT);
        need(p, "A065", AccessibilityNeed.FRONT_SECTION);
        need(p, "A066", AccessibilityNeed.FRONT_SECTION);
        companion(p, "A066", "A067");
        companion(p, "A070", "A099"); // companion was never registered

        p.getAttendee("A003").setNotes("Keynote guest - reserved seat B7");
        p.getAttendee("A030").setPreferredArea(SeatingArea.FRONT);
        p.getAttendee("A031").setPreferredArea(SeatingArea.BACK);
        p.getAttendee("A032").setPreferredRow("C");
        p.getAttendee("A033").setPreferredRow("A");
        p.getAttendee("A034").setPreferredArea(SeatingArea.MIDDLE);
        p.getAttendee("A055").setNotes("Registered late");
        return p;
    }

    private static void add(Project p, int n, String group) {
        // Deterministic and collision-free for up to several hundred attendees.
        String name = FIRST[n % FIRST.length] + " " + LAST[(n * 7 + n / FIRST.length) % LAST.length];
        Attendee a = new Attendee(String.format("A%03d", n), name);
        a.setGroupId(group);
        p.addAttendee(a);
    }

    private static void need(Project p, String id, AccessibilityNeed need) {
        p.getAttendee(id).setAccessibilityNeed(need);
    }

    private static void companion(Project p, String id, String companionId) {
        Attendee a = p.getAttendee(id);
        a.setRequiresCompanion(true);
        a.setCompanionId(companionId);
    }
}
