package com.seatingplanner.service;

import com.seatingplanner.model.EventHall;
import com.seatingplanner.model.Seat;
import com.seatingplanner.model.SeatType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validated description of a hall layout, parsed from the Hall Setup form.
 * <p>
 * Seat lists accept IDs and same-row ranges: {@code "A1, A2, B10-B12"}.
 * Reserved seats may name who they are held for: {@code "C5:A003, A7-A8:Speakers"}.
 */
public record HallConfig(String eventName, int rows, int seatsPerRow, List<Integer> aisleAfter,
                         Set<String> accessibleSeats, Map<String, String> reservedSeats) {

    public static HallConfig parse(String eventName, int rows, int seatsPerRow, String aisles,
                                   String accessible, String reserved) throws InvalidConfigurationException {
        List<String> problems = new ArrayList<>();
        if (eventName == null || eventName.isBlank()) {
            problems.add("Event name is required.");
        } else if (eventName.trim().length() > 100) {
            problems.add("Event name must be 100 characters or fewer.");
        }
        if (rows < 1 || rows > EventHall.MAX_ROWS) {
            problems.add("Rows must be between 1 and " + EventHall.MAX_ROWS + ".");
        }
        if (seatsPerRow < 1 || seatsPerRow > EventHall.MAX_SEATS_PER_ROW) {
            problems.add("Seats per row must be between 1 and " + EventHall.MAX_SEATS_PER_ROW + ".");
        }
        if (!problems.isEmpty()) {
            throw new InvalidConfigurationException(problems);
        }

        List<Integer> aisleList = new ArrayList<>();
        for (String token : split(aisles)) {
            try {
                int a = Integer.parseInt(token);
                if (a < 1 || a >= seatsPerRow) {
                    problems.add("Aisle position " + a + " is out of range: it must be between 1 and " + (seatsPerRow - 1) + ".");
                } else if (aisleList.contains(a)) {
                    problems.add("Aisle position " + a + " is listed twice.");
                } else {
                    aisleList.add(a);
                }
            } catch (NumberFormatException e) {
                problems.add("Aisle position '" + token + "' is not a whole number.");
            }
        }
        aisleList.sort(Integer::compare);

        Set<String> accessibleSet = new LinkedHashSet<>();
        for (String token : split(accessible)) {
            for (String id : expand(token, rows, seatsPerRow, problems)) {
                if (!accessibleSet.add(id)) {
                    problems.add("Accessible seat " + id + " is listed more than once.");
                }
            }
        }

        Map<String, String> reservedMap = new LinkedHashMap<>();
        for (String token : split(reserved)) {
            String holder = "";
            int colon = token.indexOf(':');
            if (colon >= 0) {
                holder = token.substring(colon + 1).trim();
                token = token.substring(0, colon).trim();
                if (holder.length() > 40) {
                    problems.add("Reservation label for " + token + " must be 40 characters or fewer.");
                }
            }
            for (String id : expand(token, rows, seatsPerRow, problems)) {
                if (reservedMap.containsKey(id)) {
                    problems.add("Reserved seat " + id + " is listed more than once.");
                } else if (accessibleSet.contains(id)) {
                    problems.add("Seat " + id + " cannot be both accessible and reserved.");
                } else {
                    reservedMap.put(id, holder);
                }
            }
        }
        if (reservedMap.size() >= rows * seatsPerRow) {
            problems.add("Every seat is reserved - at least one seat must remain assignable.");
        }
        if (!problems.isEmpty()) {
            throw new InvalidConfigurationException(problems);
        }
        return new HallConfig(eventName.trim(), rows, seatsPerRow, aisleList, accessibleSet, reservedMap);
    }

    /** Builds the hall described by this configuration. */
    public EventHall build() {
        EventHall hall = new EventHall(eventName, rows, seatsPerRow, aisleAfter);
        for (String id : accessibleSeats) {
            hall.getSeat(id).setType(SeatType.ACCESSIBLE);
        }
        reservedSeats.forEach((id, holder) -> {
            Seat s = hall.getSeat(id);
            s.setType(SeatType.RESERVED);
            s.setReservedFor(holder);
        });
        return hall;
    }

    /** Compact text for a seat list, collapsing runs: [A1, A2, A3, B5] -> "A1-A3, B5". */
    public static String describeSeats(EventHall hall, SeatType type, boolean withHolders) {
        List<String> parts = new ArrayList<>();
        for (int r = 1; r <= hall.getRows(); r++) {
            int n = 1;
            while (n <= hall.getSeatsPerRow()) {
                Seat s = hall.getSeat(r, n);
                if (s.getType() != type) {
                    n++;
                    continue;
                }
                int start = n;
                String holder = s.getReservedFor();
                while (n < hall.getSeatsPerRow()) {
                    Seat next = hall.getSeat(r, n + 1);
                    if (next.getType() != type || (withHolders && !next.getReservedFor().equals(holder))) {
                        break;
                    }
                    n++;
                }
                String label = EventHall.seatId(r, start) + (n > start ? "-" + EventHall.seatId(r, n) : "");
                parts.add(withHolders && !holder.isEmpty() ? label + ":" + holder : label);
                n++;
            }
        }
        return String.join(", ", parts);
    }

    private static List<String> split(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null) {
            return tokens;
        }
        for (String t : text.split("[,;\\n]")) {
            if (!t.isBlank()) {
                tokens.add(t.trim());
            }
        }
        return tokens;
    }

    /** Expands "B3" or "B3-B6" into seat IDs, recording problems instead of throwing. */
    private static List<String> expand(String token, int rows, int seatsPerRow, List<String> problems) {
        List<String> ids = new ArrayList<>();
        String[] ends = token.toUpperCase().replace(" ", "").split("-");
        if (ends.length > 2) {
            problems.add("'" + token + "' is not a valid seat or range (use e.g. B3 or B3-B6).");
            return ids;
        }
        int[] a = parseSeat(ends[0]);
        int[] b = ends.length == 2 ? parseSeat(ends[1]) : a;
        if (a == null || b == null) {
            problems.add("'" + token + "' is not a valid seat ID (use a row letter followed by a seat number, e.g. C7).");
            return ids;
        }
        if (a[0] != b[0]) {
            problems.add("Range '" + token + "' must stay within one row.");
            return ids;
        }
        if (a[0] > rows || Math.max(a[1], b[1]) > seatsPerRow || Math.min(a[1], b[1]) < 1) {
            problems.add("Seat '" + token + "' is outside the hall (" + rows + " rows A-" + EventHall.rowLabel(rows)
                    + ", " + seatsPerRow + " seats per row).");
            return ids;
        }
        for (int n = Math.min(a[1], b[1]); n <= Math.max(a[1], b[1]); n++) {
            ids.add(EventHall.seatId(a[0], n));
        }
        return ids;
    }

    private static int[] parseSeat(String id) {
        int i = 0;
        while (i < id.length() && Character.isLetter(id.charAt(i))) {
            i++;
        }
        if (i == 0 || i == id.length()) {
            return null;
        }
        int row = EventHall.rowIndexOf(id.substring(0, i));
        try {
            int number = Integer.parseInt(id.substring(i));
            return row > 0 ? new int[]{row, number} : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
