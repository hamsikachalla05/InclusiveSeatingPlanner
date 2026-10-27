package com.seatingplanner.model;

import java.util.Objects;

/**
 * A single physical seat. Seats are created only by {@link EventHall}; their occupant is
 * maintained only by {@link SeatingAssignment} so the two views can never disagree.
 */
public class Seat {

    private final String id;
    private final int row;
    private final int number;
    private SeatType type = SeatType.STANDARD;
    private String reservedFor = "";
    private String occupantId;

    Seat(String id, int row, int number) {
        this.id = id;
        this.row = row;
        this.number = number;
    }

    /** Unique seat identifier, e.g. "C7" (row C, seat 7). */
    public String getId() {
        return id;
    }

    /** 1-based row index; row 1 (A) is closest to the stage. */
    public int getRow() {
        return row;
    }

    public String getRowLabel() {
        return EventHall.rowLabel(row);
    }

    /** 1-based seat number within the row, counted from the left when facing the stage. */
    public int getNumber() {
        return number;
    }

    public SeatType getType() {
        return type;
    }

    /** Changing away from RESERVED clears the reservation holder. */
    public void setType(SeatType type) {
        this.type = Objects.requireNonNull(type);
        if (type != SeatType.RESERVED) {
            reservedFor = "";
        }
    }

    /**
     * Who the seat is held for. If this matches a registered attendee ID the allocator
     * seats that attendee here; any other text (e.g. "Speakers") simply blocks the seat.
     */
    public String getReservedFor() {
        return reservedFor;
    }

    public void setReservedFor(String reservedFor) {
        this.reservedFor = reservedFor == null ? "" : reservedFor.trim();
    }

    public boolean isReservedFor(String attendeeId) {
        return type == SeatType.RESERVED && !reservedFor.isEmpty() && reservedFor.equalsIgnoreCase(attendeeId);
    }

    public String getOccupantId() {
        return occupantId;
    }

    void setOccupantId(String occupantId) {
        this.occupantId = occupantId;
    }

    public boolean isOccupied() {
        return occupantId != null;
    }

    public OccupancyStatus getOccupancyStatus() {
        if (occupantId != null) {
            return OccupancyStatus.OCCUPIED;
        }
        return type == SeatType.RESERVED ? OccupancyStatus.HELD : OccupancyStatus.AVAILABLE;
    }

    @Override
    public String toString() {
        return id;
    }
}
