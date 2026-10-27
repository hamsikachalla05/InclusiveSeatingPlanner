package com.seatingplanner.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The event hall: a rectangular grid of seats (stored in a 2D array) with optional
 * vertical aisles that split each row into seating blocks.
 * Row 1 ("A") is the front row nearest the stage.
 */
public class EventHall {

    public static final int MAX_ROWS = 52;
    public static final int MAX_SEATS_PER_ROW = 60;

    private String eventName;
    private final int rows;
    private final int seatsPerRow;
    private final Seat[][] grid;
    private final TreeSet<Integer> aisleAfter = new TreeSet<>();
    private final Map<String, Seat> seatsById = new LinkedHashMap<>();

    /**
     * @param aisleAfter seat numbers after which an aisle runs, e.g. {4, 10} puts aisles
     *                   between seats 4|5 and 10|11 in every row
     * @throws IllegalArgumentException if any dimension or aisle position is invalid
     */
    public EventHall(String eventName, int rows, int seatsPerRow, Collection<Integer> aisleAfter) {
        if (rows < 1 || rows > MAX_ROWS) {
            throw new IllegalArgumentException("Rows must be between 1 and " + MAX_ROWS + ".");
        }
        if (seatsPerRow < 1 || seatsPerRow > MAX_SEATS_PER_ROW) {
            throw new IllegalArgumentException("Seats per row must be between 1 and " + MAX_SEATS_PER_ROW + ".");
        }
        for (Integer a : aisleAfter) {
            if (a == null || a < 1 || a >= seatsPerRow) {
                throw new IllegalArgumentException("Aisle position " + a + " must be between 1 and "
                        + (seatsPerRow - 1) + " (an aisle sits after that seat number).");
            }
            this.aisleAfter.add(a);
        }
        setEventName(eventName);
        this.rows = rows;
        this.seatsPerRow = seatsPerRow;
        this.grid = new Seat[rows][seatsPerRow];
        for (int r = 1; r <= rows; r++) {
            for (int n = 1; n <= seatsPerRow; n++) {
                String id = seatId(r, n);
                if (seatsById.containsKey(id)) {
                    throw new IllegalStateException("Duplicate seat ID generated: " + id);
                }
                Seat seat = new Seat(id, r, n);
                grid[r - 1][n - 1] = seat;
                seatsById.put(id, seat);
            }
        }
    }

    /** 1 -> A, 26 -> Z, 27 -> AA ... */
    public static String rowLabel(int row) {
        StringBuilder sb = new StringBuilder();
        int n = row;
        while (n > 0) {
            n--;
            sb.insert(0, (char) ('A' + n % 26));
            n /= 26;
        }
        return sb.toString();
    }

    /** Inverse of {@link #rowLabel(int)}; returns 0 when the label is not a valid row label. */
    public static int rowIndexOf(String label) {
        if (label == null || label.isBlank()) {
            return 0;
        }
        int n = 0;
        for (char c : label.trim().toUpperCase().toCharArray()) {
            if (c < 'A' || c > 'Z') {
                return 0;
            }
            n = n * 26 + (c - 'A' + 1);
            if (n > 10_000) {
                return 0;
            }
        }
        return n;
    }

    public static String seatId(int row, int number) {
        return rowLabel(row) + number;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName == null || eventName.isBlank() ? "Untitled Event" : eventName.trim();
    }

    public int getRows() {
        return rows;
    }

    public int getSeatsPerRow() {
        return seatsPerRow;
    }

    public int getCapacity() {
        return rows * seatsPerRow;
    }

    public Seat getSeat(int row, int number) {
        if (row < 1 || row > rows || number < 1 || number > seatsPerRow) {
            return null;
        }
        return grid[row - 1][number - 1];
    }

    public Seat getSeat(String id) {
        return id == null ? null : seatsById.get(Ids.normalize(id));
    }

    /** All seats in row-major order (front row first, left to right). */
    public List<Seat> getSeats() {
        return Collections.unmodifiableList(new ArrayList<>(seatsById.values()));
    }

    public List<Seat> getRowSeats(int row) {
        List<Seat> list = new ArrayList<>();
        if (row >= 1 && row <= rows) {
            Collections.addAll(list, grid[row - 1]);
        }
        return list;
    }

    public Set<Integer> getAisleAfter() {
        return Collections.unmodifiableSet(aisleAfter);
    }

    public boolean hasAisleAfter(int seatNumber) {
        return aisleAfter.contains(seatNumber);
    }

    /** Two seats are adjacent when they are side by side in the same row with no aisle between them. */
    public boolean areAdjacent(Seat a, Seat b) {
        if (a == null || b == null || a.getRow() != b.getRow()) {
            return false;
        }
        int lo = Math.min(a.getNumber(), b.getNumber());
        int hi = Math.max(a.getNumber(), b.getNumber());
        return hi - lo == 1 && !aisleAfter.contains(lo);
    }

    /** Adjacent in the row, or directly in front of / behind each other. Used for group clustering. */
    public boolean areNear(Seat a, Seat b) {
        if (areAdjacent(a, b)) {
            return true;
        }
        return a != null && b != null && a.getNumber() == b.getNumber() && Math.abs(a.getRow() - b.getRow()) == 1;
    }

    /** The (up to two) seats directly beside this one in its row. */
    public List<Seat> rowNeighbours(Seat seat) {
        List<Seat> list = new ArrayList<>(2);
        Seat left = getSeat(seat.getRow(), seat.getNumber() - 1);
        Seat right = getSeat(seat.getRow(), seat.getNumber() + 1);
        if (areAdjacent(seat, left)) {
            list.add(left);
        }
        if (areAdjacent(seat, right)) {
            list.add(right);
        }
        return list;
    }

    /** A seat at either end of a row or directly beside an aisle. */
    public boolean isAisleSeat(Seat seat) {
        int n = seat.getNumber();
        return n == 1 || n == seatsPerRow || aisleAfter.contains(n) || aisleAfter.contains(n - 1);
    }

    /** Number of rows in the front (and back) section: one third of the hall, at least one row. */
    public int getSectionRows() {
        return Math.max(1, (int) Math.ceil(rows / 3.0));
    }

    public SeatingArea areaOf(int row) {
        int section = getSectionRows();
        if (row <= section) {
            return SeatingArea.FRONT;
        }
        if (row > rows - section) {
            return SeatingArea.BACK;
        }
        return SeatingArea.MIDDLE;
    }

    /** Index (0-based) of the seating block a seat number falls into, given the aisles. */
    public int blockOf(int seatNumber) {
        return aisleAfter.headSet(seatNumber).size();
    }

    /**
     * Walking distance used for proximity scoring: one step per seat along a row (crossing an
     * aisle costs one extra step) and two steps per row.
     */
    public int distance(Seat a, Seat b) {
        int lo = Math.min(a.getNumber(), b.getNumber());
        int hi = Math.max(a.getNumber(), b.getNumber());
        int aisles = aisleAfter.subSet(lo, true, hi, false).size();
        return 2 * Math.abs(a.getRow() - b.getRow()) + (hi - lo) + aisles;
    }

    public int countSeats(SeatType type) {
        int count = 0;
        for (Seat s : seatsById.values()) {
            if (s.getType() == type) {
                count++;
            }
        }
        return count;
    }
}
