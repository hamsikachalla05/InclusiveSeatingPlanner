package com.seatingplanner.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The attendee-to-seat mapping for one hall. It keeps the attendee -> seat map and each
 * seat's occupant in sync, which makes double-booking a seat or an attendee impossible.
 */
public class SeatingAssignment {

    private final EventHall hall;
    private final Map<String, String> seatByAttendee = new LinkedHashMap<>();

    public SeatingAssignment(EventHall hall) {
        this.hall = hall;
    }

    public EventHall getHall() {
        return hall;
    }

    public Seat getSeatOf(String attendeeId) {
        String seatId = seatByAttendee.get(Ids.normalize(attendeeId));
        return seatId == null ? null : hall.getSeat(seatId);
    }

    public boolean isAssigned(String attendeeId) {
        return seatByAttendee.containsKey(Ids.normalize(attendeeId));
    }

    /**
     * Seats the attendee, moving them if they already hold another seat.
     *
     * @throws IllegalStateException if the seat is occupied by somebody else
     */
    public void assign(String attendeeId, Seat seat) {
        String id = Ids.normalize(attendeeId);
        if (seat == null || hall.getSeat(seat.getId()) != seat) {
            throw new IllegalArgumentException("Seat does not belong to this hall.");
        }
        if (seat.isOccupied() && !seat.getOccupantId().equals(id)) {
            throw new IllegalStateException("Seat " + seat.getId() + " is already occupied by " + seat.getOccupantId() + ".");
        }
        unassign(id);
        seatByAttendee.put(id, seat.getId());
        seat.setOccupantId(id);
    }

    public void unassign(String attendeeId) {
        String seatId = seatByAttendee.remove(Ids.normalize(attendeeId));
        if (seatId != null) {
            hall.getSeat(seatId).setOccupantId(null);
        }
    }

    public void clear() {
        for (String seatId : seatByAttendee.values()) {
            hall.getSeat(seatId).setOccupantId(null);
        }
        seatByAttendee.clear();
    }

    public int size() {
        return seatByAttendee.size();
    }

    /** Read-only view: attendee ID -> seat ID. */
    public Map<String, String> asMap() {
        return Collections.unmodifiableMap(seatByAttendee);
    }

    /** Copy of the current state, used to simulate a change and roll it back if invalid. */
    public Map<String, String> snapshot() {
        return new LinkedHashMap<>(seatByAttendee);
    }

    public void restore(Map<String, String> snapshot) {
        clear();
        snapshot.forEach((attendeeId, seatId) -> assign(attendeeId, hall.getSeat(seatId)));
    }
}
