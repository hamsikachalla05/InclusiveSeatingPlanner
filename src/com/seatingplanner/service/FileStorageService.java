package com.seatingplanner.service;

import com.seatingplanner.model.AccessibilityNeed;
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
import com.seatingplanner.model.SeatingArea;
import com.seatingplanner.model.SeatingAssignment;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Saves and loads projects as human-readable JSON. Loading is defensive: malformed entries are
 * skipped with a warning instead of aborting, and the restored state is re-validated by the planner.
 */
public class FileStorageService {

    public static final String FORMAT = "InclusiveSeatingPlanner";
    public static final int VERSION = 1;

    /** Everything recovered from a project file. */
    public record LoadResult(Project project, List<Conflict> conflicts, int nextConflictNumber, List<String> warnings) {
    }

    /** Attendees parsed from an import file. */
    public record ImportResult(List<Attendee> attendees, List<String> warnings) {
    }

    // ------------------------------------------------------------------ save

    /** Writes atomically (temp file + move) so a crash mid-save never corrupts the existing file. */
    public void save(SeatingPlanner planner, Path path) throws StorageException {
        String json = Json.write(toJson(planner));
        try {
            Path target = path.toAbsolutePath();
            Path dir = target.getParent();
            if (dir != null) {
                Files.createDirectories(dir);
            }
            Path tmp = Files.createTempFile(dir, ".seating-", ".tmp");
            try {
                Files.writeString(tmp, json, StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (AccessDeniedException e) {
            throw new StorageException("Permission denied while saving to " + path + ". Choose a folder you can write to.", e);
        } catch (IOException | InvalidPathException | SecurityException e) {
            throw new StorageException("Could not save the project to " + path + ": " + e.getMessage(), e);
        }
    }

    private Map<String, Object> toJson(SeatingPlanner planner) {
        Project p = planner.getProject();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", FORMAT);
        root.put("version", VERSION);
        root.put("savedAt", LocalDateTime.now().toString());

        EventHall hall = p.getHall();
        if (hall != null) {
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("eventName", hall.getEventName());
            h.put("rows", hall.getRows());
            h.put("seatsPerRow", hall.getSeatsPerRow());
            h.put("aisleAfter", new ArrayList<Object>(hall.getAisleAfter()));
            List<Object> special = new ArrayList<>();
            for (Seat s : hall.getSeats()) {
                if (s.getType() != SeatType.STANDARD) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", s.getId());
                    m.put("type", s.getType().name());
                    if (!s.getReservedFor().isEmpty()) {
                        m.put("reservedFor", s.getReservedFor());
                    }
                    special.add(m);
                }
            }
            h.put("specialSeats", special);
            root.put("hall", h);
        }

        List<Object> attendees = new ArrayList<>();
        for (Attendee a : p.getAttendees()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("fullName", a.getFullName());
            m.put("groupId", a.getGroupId());
            m.put("accessibilityNeed", a.getAccessibilityNeed().name());
            m.put("requiresCompanion", a.isRequiresCompanion());
            m.put("companionId", a.getCompanionId());
            m.put("preferredArea", a.getPreferredArea().name());
            m.put("preferredRow", a.getPreferredRow());
            m.put("notes", a.getNotes());
            attendees.add(m);
        }
        root.put("attendees", attendees);

        List<Object> assignments = new ArrayList<>();
        if (p.getAssignment() != null) {
            p.getAssignment().asMap().forEach((attendeeId, seatId) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("attendeeId", attendeeId);
                m.put("seatId", seatId);
                assignments.add(m);
            });
        }
        root.put("assignments", assignments);

        List<Object> conflicts = new ArrayList<>();
        for (Conflict c : planner.getConflictManager().getConflicts()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("category", c.getCategory().name());
            m.put("severity", c.getSeverity().name());
            m.put("affected", new ArrayList<Object>(c.getAffectedAttendees()));
            m.put("description", c.getDescription());
            m.put("suggestion", c.getSuggestion());
            m.put("status", c.getStatus().name());
            m.put("key", c.getKey());
            m.put("live", c.isLive());
            m.put("detectedAt", c.getDetectedAt().toString());
            m.put("updatedAt", c.getUpdatedAt().toString());
            conflicts.add(m);
        }
        Map<String, Object> conflictBlock = new LinkedHashMap<>();
        conflictBlock.put("nextNumber", planner.getConflictManager().getNextNumber());
        conflictBlock.put("items", conflicts);
        root.put("conflicts", conflictBlock);
        return root;
    }

    // ------------------------------------------------------------------ load

    public LoadResult load(Path path) throws StorageException {
        String text = readText(path);
        Object parsed;
        try {
            parsed = Json.parse(text);
        } catch (IllegalArgumentException e) {
            throw new StorageException("The project file is damaged or is not valid JSON (" + e.getMessage() + ").", e);
        }
        if (!(parsed instanceof Map<?, ?> root) || !FORMAT.equals(root.get("format"))) {
            throw new StorageException("This file is not an Inclusive Event Seating Planner project.");
        }
        long version = asLong(root.get("version"), 0);
        if (version > VERSION) {
            throw new StorageException("This project was saved by a newer version of the application (format " + version + ").");
        }

        List<String> warnings = new ArrayList<>();
        Project project = new Project();

        Object hallObj = root.get("hall");
        if (hallObj instanceof Map<?, ?> h) {
            EventHall hall = readHall(h, warnings);
            if (hall != null) {
                project.setHall(hall, new SeatingAssignment(hall));
            }
        }

        for (Object o : asList(root.get("attendees"))) {
            if (!(o instanceof Map<?, ?> m)) {
                warnings.add("Skipped a malformed attendee entry.");
                continue;
            }
            try {
                Attendee a = readAttendee(m);
                if (project.containsAttendee(a.getId())) {
                    warnings.add("Skipped duplicate attendee ID " + a.getId() + ".");
                } else {
                    project.addAttendee(a);
                }
            } catch (IllegalArgumentException e) {
                warnings.add("Skipped attendee " + str(m, "id", "?") + ": " + e.getMessage());
            }
        }

        if (project.hasHall()) {
            SeatingAssignment asg = project.getAssignment();
            for (Object o : asList(root.get("assignments"))) {
                if (!(o instanceof Map<?, ?> m)) {
                    warnings.add("Skipped a malformed assignment entry.");
                    continue;
                }
                String attendeeId = Ids.normalize(str(m, "attendeeId", ""));
                Seat seat = project.getHall().getSeat(str(m, "seatId", ""));
                if (!project.containsAttendee(attendeeId)) {
                    warnings.add("Skipped assignment for unknown attendee '" + attendeeId + "'.");
                } else if (seat == null) {
                    warnings.add("Skipped assignment of " + attendeeId + " to a seat that does not exist.");
                } else if (asg.isAssigned(attendeeId)) {
                    warnings.add("Attendee " + attendeeId + " was assigned twice; kept the first seat.");
                } else if (seat.isOccupied()) {
                    warnings.add("Seat " + seat.getId() + " was assigned twice; " + attendeeId + " was left unassigned.");
                } else {
                    asg.assign(attendeeId, seat);
                }
            }
        }

        List<Conflict> conflicts = new ArrayList<>();
        int nextNumber = 1;
        if (root.get("conflicts") instanceof Map<?, ?> cb) {
            nextNumber = (int) Math.max(1, asLong(cb.get("nextNumber"), 1));
            Set<String> seenIds = new HashSet<>();
            for (Object o : asList(cb.get("items"))) {
                if (o instanceof Map<?, ?> m) {
                    Conflict c = readConflict(m);
                    if (c != null && seenIds.add(c.getId())) {
                        conflicts.add(c);
                    }
                }
            }
        }
        return new LoadResult(project, conflicts, nextNumber, warnings);
    }

    private EventHall readHall(Map<?, ?> h, List<String> warnings) throws StorageException {
        int rows = (int) asLong(h.get("rows"), -1);
        int seats = (int) asLong(h.get("seatsPerRow"), -1);
        List<Integer> aisles = new ArrayList<>();
        for (Object o : asList(h.get("aisleAfter"))) {
            int a = (int) asLong(o, -1);
            if (a >= 1 && a < seats && !aisles.contains(a)) {
                aisles.add(a);
            } else {
                warnings.add("Ignored invalid aisle position " + o + ".");
            }
        }
        EventHall hall;
        try {
            hall = new EventHall(str(h, "eventName", "Untitled Event"), rows, seats, aisles);
        } catch (IllegalArgumentException e) {
            throw new StorageException("The saved hall layout is invalid: " + e.getMessage(), e);
        }
        for (Object o : asList(h.get("specialSeats"))) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            Seat s = hall.getSeat(str(m, "id", ""));
            SeatType type = parseEnum(SeatType.class, str(m, "type", ""), null);
            if (s == null || type == null) {
                warnings.add("Ignored unknown seat entry " + str(m, "id", "?") + ".");
                continue;
            }
            s.setType(type);
            s.setReservedFor(str(m, "reservedFor", ""));
        }
        return hall;
    }

    private Attendee readAttendee(Map<?, ?> m) {
        String name = str(m, "fullName", "");
        if (name.length() > 120) {
            throw new IllegalArgumentException("name is too long");
        }
        Attendee a = new Attendee(str(m, "id", ""), name);
        a.setGroupId(str(m, "groupId", ""));
        a.setAccessibilityNeed(parseEnum(AccessibilityNeed.class, str(m, "accessibilityNeed", ""), AccessibilityNeed.NONE));
        a.setRequiresCompanion(Boolean.TRUE.equals(m.get("requiresCompanion")));
        a.setCompanionId(str(m, "companionId", ""));
        a.setPreferredArea(parseEnum(SeatingArea.class, str(m, "preferredArea", ""), SeatingArea.ANY));
        a.setPreferredRow(str(m, "preferredRow", ""));
        a.setNotes(str(m, "notes", ""));
        return a;
    }

    private Conflict readConflict(Map<?, ?> m) {
        ConflictCategory cat = parseEnum(ConflictCategory.class, str(m, "category", ""), null);
        ConflictSeverity sev = parseEnum(ConflictSeverity.class, str(m, "severity", ""), null);
        ResolutionStatus status = parseEnum(ResolutionStatus.class, str(m, "status", ""), null);
        String id = str(m, "id", "");
        if (cat == null || sev == null || status == null || id.isEmpty()) {
            return null;
        }
        List<String> affected = new ArrayList<>();
        for (Object o : asList(m.get("affected"))) {
            if (o instanceof String s) {
                affected.add(s);
            }
        }
        Conflict c = new Conflict(cat, sev, affected, str(m, "description", ""), str(m, "suggestion", ""),
                str(m, "key", "EVENT"), Boolean.TRUE.equals(m.get("live")));
        c.setId(id);
        c.setStatus(status);
        try {
            c.setTimestamps(LocalDateTime.parse(str(m, "detectedAt", "")), LocalDateTime.parse(str(m, "updatedAt", "")));
        } catch (DateTimeParseException ignored) {
            // keep "now" timestamps
        }
        return c;
    }

    // ------------------------------------------------------------------ attendee CSV import

    /**
     * Imports attendees from a CSV with a header row. Recognised columns (any order, case-insensitive):
     * id, name, group, accessibility, companion_required, companion_id, preferred_area, preferred_row, notes.
     */
    public ImportResult importAttendeesCsv(Path path, Project existing) throws StorageException {
        List<List<String>> rows = Csv.parse(readText(path));
        if (rows.isEmpty()) {
            throw new StorageException("The CSV file is empty.");
        }
        Map<String, Integer> col = new LinkedHashMap<>();
        List<String> header = rows.get(0);
        for (int i = 0; i < header.size(); i++) {
            col.put(header.get(i).trim().toLowerCase(Locale.ROOT).replace(' ', '_'), i);
        }
        if (!col.containsKey("name") && !col.containsKey("full_name")) {
            throw new StorageException("The CSV needs a header row with at least a 'name' column.");
        }
        List<String> warnings = new ArrayList<>();
        List<Attendee> out = new ArrayList<>();
        Set<String> taken = new HashSet<>();
        for (Attendee a : existing.getAttendees()) {
            taken.add(a.getId());
        }
        int nextNumber = 1;
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            String name = cell(row, col, "name", cell(row, col, "full_name", ""));
            if (name.isBlank()) {
                warnings.add("Row " + (r + 1) + ": skipped (no name).");
                continue;
            }
            String id = Ids.normalize(cell(row, col, "id", ""));
            if (id.isEmpty()) {
                do {
                    id = String.format("A%03d", nextNumber++);
                } while (taken.contains(id));
            }
            if (!id.matches("[A-Z0-9_-]{1,20}")) {
                warnings.add("Row " + (r + 1) + ": skipped (invalid ID '" + id + "').");
                continue;
            }
            if (!taken.add(id)) {
                warnings.add("Row " + (r + 1) + ": skipped (ID " + id + " already exists).");
                continue;
            }
            Attendee a = new Attendee(id, name);
            a.setGroupId(cell(row, col, "group", ""));
            a.setAccessibilityNeed(parseNeed(cell(row, col, "accessibility", "")));
            String comp = cell(row, col, "companion_id", "");
            String required = cell(row, col, "companion_required", "").toLowerCase(Locale.ROOT);
            a.setRequiresCompanion(required.matches("y|yes|true|1") || (!comp.isBlank() && required.isBlank()));
            a.setCompanionId(comp);
            a.setPreferredArea(parseEnum(SeatingArea.class, cell(row, col, "preferred_area", ""), SeatingArea.ANY));
            a.setPreferredRow(cell(row, col, "preferred_row", ""));
            a.setNotes(cell(row, col, "notes", ""));
            out.add(a);
        }
        return new ImportResult(out, warnings);
    }

    private static AccessibilityNeed parseNeed(String text) {
        String t = text.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty() || t.equals("none") || t.equals("no")) {
            return AccessibilityNeed.NONE;
        }
        if (t.contains("aisle")) {
            return AccessibilityNeed.AISLE_SEAT;
        }
        if (t.contains("front")) {
            return AccessibilityNeed.FRONT_SECTION;
        }
        if (t.contains("access") || t.contains("wheel")) {
            return AccessibilityNeed.ACCESSIBLE_SEAT;
        }
        return parseEnum(AccessibilityNeed.class, t, AccessibilityNeed.NONE);
    }

    // ------------------------------------------------------------------ helpers

    private static String readText(Path path) throws StorageException {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            throw new StorageException("File not found: " + path, e);
        } catch (AccessDeniedException e) {
            throw new StorageException("Permission denied reading " + path + ".", e);
        } catch (MalformedInputException e) {
            throw new StorageException("The file " + path.getFileName() + " is not UTF-8 text.", e);
        } catch (IOException | InvalidPathException | SecurityException e) {
            throw new StorageException("Could not read " + path + ": " + e.getMessage(), e);
        }
    }

    private static String cell(List<String> row, Map<String, Integer> col, String name, String fallback) {
        Integer i = col.get(name);
        return i == null || i >= row.size() ? fallback : row.get(i).trim();
    }

    private static List<?> asList(Object o) {
        return o instanceof List<?> l ? l : List.of();
    }

    private static String str(Map<?, ?> m, String key, String fallback) {
        Object v = m.get(key);
        return v instanceof String s ? s : fallback;
    }

    private static long asLong(Object o, long fallback) {
        if (o instanceof Long l) {
            return l;
        }
        if (o instanceof Double d && d == Math.floor(d)) {
            return d.longValue();
        }
        return fallback;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
