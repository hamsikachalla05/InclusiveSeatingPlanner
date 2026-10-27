package com.seatingplanner.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Aggregate of everything that makes up one seating plan: hall, attendees and assignments. */
public class Project {

    private static final Pattern DEFAULT_ID = Pattern.compile("A(\\d+)");

    private EventHall hall;
    private SeatingAssignment assignment;
    private final Map<String, Attendee> attendees = new HashMap<>();

    public EventHall getHall() {
        return hall;
    }

    public boolean hasHall() {
        return hall != null;
    }

    /** Null when no hall has been configured yet. */
    public SeatingAssignment getAssignment() {
        return assignment;
    }

    public void setHall(EventHall hall, SeatingAssignment assignment) {
        if (hall != null && (assignment == null || assignment.getHall() != hall)) {
            throw new IllegalArgumentException("Assignment must belong to the hall.");
        }
        this.hall = hall;
        this.assignment = assignment;
    }

    /** All attendees ordered by ID (natural order, so A2 precedes A10). */
    public List<Attendee> getAttendees() {
        List<Attendee> list = new ArrayList<>(attendees.values());
        list.sort((a, b) -> Ids.compareNatural(a.getId(), b.getId()));
        return list;
    }

    public int getAttendeeCount() {
        return attendees.size();
    }

    public Attendee getAttendee(String id) {
        return id == null ? null : attendees.get(Ids.normalize(id));
    }

    public boolean containsAttendee(String id) {
        return getAttendee(id) != null;
    }

    public void addAttendee(Attendee attendee) {
        if (attendees.containsKey(attendee.getId())) {
            throw new IllegalArgumentException("Attendee ID " + attendee.getId() + " already exists.");
        }
        attendees.put(attendee.getId(), attendee);
    }

    public void replaceAttendee(Attendee attendee) {
        if (!attendees.containsKey(attendee.getId())) {
            throw new IllegalArgumentException("Attendee " + attendee.getId() + " is not registered.");
        }
        attendees.put(attendee.getId(), attendee);
    }

    public Attendee removeAttendee(String id) {
        if (assignment != null) {
            assignment.unassign(id);
        }
        return attendees.remove(Ids.normalize(id));
    }

    public Seat getSeatOf(Attendee attendee) {
        return assignment == null ? null : assignment.getSeatOf(attendee.getId());
    }

    /** Groups (case-insensitive name) -> members, in natural group order. */
    public Map<String, List<Attendee>> getGroups() {
        Map<String, List<Attendee>> groups = new TreeMap<>(Ids.NATURAL);
        for (Attendee a : getAttendees()) {
            if (a.hasGroup()) {
                groups.computeIfAbsent(a.getGroupId(), k -> new ArrayList<>()).add(a);
            }
        }
        return groups;
    }

    public List<Attendee> getGroupMembers(String groupId) {
        List<Attendee> members = getGroups().get(groupId);
        return members == null ? List.of() : members;
    }

    /** Next free ID of the form A001, A002 ... */
    public String nextAttendeeId() {
        int max = 0;
        for (String id : attendees.keySet()) {
            Matcher m = DEFAULT_ID.matcher(id);
            if (m.matches() && m.group(1).length() < 9) {
                max = Math.max(max, Integer.parseInt(m.group(1)));
            }
        }
        String candidate;
        do {
            max++;
            candidate = String.format("A%03d", max);
        } while (attendees.containsKey(candidate));
        return candidate;
    }
}
