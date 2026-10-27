package com.seatingplanner.service;

import com.seatingplanner.model.AssignmentStatus;
import com.seatingplanner.model.Attendee;
import com.seatingplanner.model.Conflict;
import com.seatingplanner.model.EventHall;
import com.seatingplanner.model.ResolutionStatus;
import com.seatingplanner.model.Seat;
import com.seatingplanner.model.SeatType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Produces the CSV exports and the printable seating plan report. */
public class ExportService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public void exportAssignmentsCsv(SeatingPlanner planner, Path path) throws StorageException {
        write(path, buildAssignmentsCsv(planner));
    }

    public void exportConflictsCsv(SeatingPlanner planner, Path path) throws StorageException {
        write(path, buildConflictsCsv(planner));
    }

    public void exportHtmlReport(SeatingPlanner planner, Path path) throws StorageException {
        write(path, buildHtmlReport(planner));
    }

    public String buildAssignmentsCsv(SeatingPlanner planner) {
        StringBuilder sb = new StringBuilder("﻿"); // BOM so Excel detects UTF-8 names
        sb.append(Csv.row("Attendee ID", "Attendee Name", "Group ID", "Assigned Row", "Assigned Seat", "Seat ID",
                "Seat Type", "Assignment Status", "Accessibility Requirement", "Companion ID", "Preference", "Notes"));
        for (Attendee a : planner.getProject().getAttendees()) {
            Seat s = planner.seatOf(a);
            sb.append(Csv.row(a.getId(), a.getFullName(), a.getGroupId(),
                    s == null ? "" : s.getRowLabel(),
                    s == null ? "" : String.valueOf(s.getNumber()),
                    s == null ? "" : s.getId(),
                    s == null ? "" : s.getType().getLabel(),
                    planner.statusOf(a).getLabel(),
                    a.getAccessibilityNeed().getLabel(),
                    a.isRequiresCompanion() ? a.getCompanionId() : "",
                    a.describePreference(),
                    a.getNotes()));
        }
        return sb.toString();
    }

    public String buildConflictsCsv(SeatingPlanner planner) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append(Csv.row("Conflict ID", "Severity", "Category", "Affected Attendees", "Description",
                "Suggested Resolution", "Resolution Status", "Detected At", "Last Updated"));
        for (Conflict c : planner.getConflictManager().getConflicts()) {
            sb.append(Csv.row(c.getId(), c.getSeverity().getLabel(), c.getCategory().getLabel(),
                    String.join("; ", c.getAffectedAttendees()), c.getDescription(), c.getSuggestion(),
                    c.getStatus().getLabel(), c.getDetectedAt().format(STAMP), c.getUpdatedAt().format(STAMP)));
        }
        return sb.toString();
    }

    /**
     * A self-contained HTML report (seat map + attendee lookup + conflicts). It deliberately uses
     * simple HTML 3.2-compatible markup so it renders both in browsers and in Swing's print preview.
     */
    public String buildHtmlReport(SeatingPlanner planner) {
        EventHall hall = planner.getHall();
        SeatingPlanner.Stats st = planner.getStats();
        StringBuilder h = new StringBuilder();
        String title = hall == null ? "Seating Plan" : hall.getEventName();
        h.append("<html><head><meta charset=\"UTF-8\"><title>").append(esc(title)).append(" - Seating Plan</title>");
        h.append("<style>body{font-family:'Segoe UI',Arial,sans-serif;color:#1e293b;margin:24px;}"
                + "h1{font-size:22px;margin:0 0 4px 0;} h2{font-size:16px;margin:22px 0 8px 0;}"
                + "table{border-collapse:collapse;} td,th{font-size:11px;padding:4px 6px;}"
                + "th{background:#e2e8f0;text-align:left;} .list td{border-bottom:1px solid #e2e8f0;}"
                + ".seat{width:58px;height:30px;text-align:center;border:1px solid #94a3b8;font-size:9px;}"
                + ".muted{color:#64748b;font-size:11px;} @media print{body{margin:8mm;}}</style></head><body>");
        h.append("<h1>").append(esc(title)).append("</h1>");
        h.append("<div class=\"muted\">Seating plan report generated ").append(LocalDateTime.now().format(STAMP)).append("</div>");

        String validity = planner.isPlanFullyValid()
                ? "<font color=\"#15803d\"><b>All attendees are seated and every mandatory requirement is satisfied.</b></font>"
                : "<font color=\"#b91c1c\"><b>Plan is NOT fully valid: " + st.openErrors() + " open error(s), "
                  + (st.registered() - st.assigned()) + " attendee(s) unassigned.</b></font>";
        h.append("<p>").append(validity).append("</p>");
        h.append("<table><tr>")
                .append(statCell("Capacity", st.capacity()))
                .append(statCell("Registered", st.registered()))
                .append(statCell("Assigned", st.assigned()))
                .append(statCell("Unassigned", st.registered() - st.assigned()))
                .append(statCell("Available seats", st.availableSeats()))
                .append(statCell("Unresolved conflicts", (int) st.unresolved()))
                .append("</tr></table>");

        if (hall != null) {
            h.append("<h2>Seat map</h2>");
            h.append("<table cellspacing=\"3\"><tr><td></td><td colspan=\"").append(hall.getSeatsPerRow() + hall.getAisleAfter().size())
                    .append("\" bgcolor=\"#1e293b\" align=\"center\"><font color=\"#ffffff\"><b>STAGE / FRONT OF HALL</b></font></td></tr>");
            for (int r = 1; r <= hall.getRows(); r++) {
                h.append("<tr><td><b>").append(EventHall.rowLabel(r)).append("</b></td>");
                for (Seat s : hall.getRowSeats(r)) {
                    Attendee occ = planner.occupantOf(s);
                    String bg = occ != null ? "#c7d2fe" : switch (s.getType()) {
                        case ACCESSIBLE -> "#d1fae5";
                        case RESERVED -> "#fef3c7";
                        default -> "#f8fafc";
                    };
                    String label = occ != null ? esc(occ.getId()) + "<br>" + esc(occ.getShortName())
                            : s.getId() + (s.getType() == SeatType.RESERVED ? "<br>Reserved" : s.getType() == SeatType.ACCESSIBLE ? "<br>Accessible" : "");
                    if (occ != null && s.getType() != SeatType.STANDARD) {
                        label += "<br><i>" + s.getType().getLabel() + "</i>";
                    }
                    h.append("<td class=\"seat\" bgcolor=\"").append(bg).append("\">").append(label).append("</td>");
                    if (hall.hasAisleAfter(s.getNumber())) {
                        h.append("<td width=\"14\"></td>");
                    }
                }
                h.append("<td><b>").append(EventHall.rowLabel(r)).append("</b></td></tr>");
            }
            h.append("</table>");
            h.append("<p class=\"muted\">Legend: <font color=\"#4338ca\">blue = occupied</font>, "
                    + "<font color=\"#047857\">green = accessible</font>, <font color=\"#b45309\">amber = reserved</font>, "
                    + "white = empty.</p>");
        }

        h.append("<h2>Attendee lookup</h2><table class=\"list\" width=\"100%\"><tr><th>ID</th><th>Name</th><th>Group</th>"
                + "<th>Row</th><th>Seat</th><th>Seat type</th><th>Requirement</th><th>Status</th></tr>");
        List<Attendee> attendees = planner.getProject().getAttendees();
        attendees.sort((a, b) -> a.getFullName().compareToIgnoreCase(b.getFullName()));
        for (Attendee a : attendees) {
            Seat s = planner.seatOf(a);
            AssignmentStatus status = planner.statusOf(a);
            String color = status == AssignmentStatus.ASSIGNED ? "#15803d" : status == AssignmentStatus.UNRESOLVED ? "#b91c1c" : "#b45309";
            String req = a.getAccessibilityNeed().getLabel() + (a.isRequiresCompanion() ? " + companion " + a.getCompanionId() : "");
            h.append("<tr><td>").append(esc(a.getId())).append("</td><td>").append(esc(a.getFullName()))
                    .append("</td><td>").append(esc(a.getGroupId()))
                    .append("</td><td>").append(s == null ? "-" : s.getRowLabel())
                    .append("</td><td>").append(s == null ? "-" : s.getNumber())
                    .append("</td><td>").append(s == null ? "-" : s.getType().getLabel())
                    .append("</td><td>").append(esc(a.hasAccessibilityNeed() || a.isRequiresCompanion() ? req : "-"))
                    .append("</td><td><font color=\"").append(color).append("\">").append(status.getLabel()).append("</font></td></tr>");
        }
        h.append("</table>");

        h.append("<h2>Unresolved conflicts</h2>");
        boolean any = false;
        h.append("<table class=\"list\" width=\"100%\"><tr><th>ID</th><th>Severity</th><th>Category</th><th>Description</th><th>Status</th></tr>");
        for (Conflict c : planner.getConflictManager().getConflicts()) {
            if (c.getStatus() == ResolutionStatus.OPEN || c.getStatus() == ResolutionStatus.ACKNOWLEDGED) {
                any = true;
                String color = c.getSeverity().name().equals("ERROR") ? "#b91c1c" : "#b45309";
                h.append("<tr><td>").append(c.getId()).append("</td><td><font color=\"").append(color).append("\"><b>")
                        .append(c.getSeverity().getLabel()).append("</b></font></td><td>").append(esc(c.getCategory().getLabel()))
                        .append("</td><td>").append(esc(c.getDescription())).append("</td><td>").append(c.getStatus().getLabel())
                        .append("</td></tr>");
            }
        }
        h.append("</table>");
        if (!any) {
            h.append("<p class=\"muted\">No unresolved conflicts.</p>");
        }
        h.append("</body></html>");
        return h.toString();
    }

    private static String statCell(String label, int value) {
        return "<td bgcolor=\"#f1f5f9\" style=\"padding:8px 14px;\"><font size=\"5\"><b>" + value
                + "</b></font><br><span class=\"muted\">" + label + "</span></td><td width=\"6\"></td>";
    }

    static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '&' -> sb.append("&amp;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void write(Path path, String content) throws StorageException {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (AccessDeniedException e) {
            throw new StorageException("Permission denied writing " + path + ". If the file is open in another program "
                    + "(e.g. Excel), close it and try again.", e);
        } catch (IOException | InvalidPathException | SecurityException e) {
            throw new StorageException("Could not write " + path + ": " + e.getMessage(), e);
        }
    }
}
