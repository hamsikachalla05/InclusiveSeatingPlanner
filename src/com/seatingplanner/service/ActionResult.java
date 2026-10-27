package com.seatingplanner.service;

/**
 * Outcome of a manual seating action. A rejected action may carry a proposal - e.g. "also move
 * companion A009 to seat C6" - that the UI can offer to the organizer.
 */
public final class ActionResult {

    private final boolean success;
    private final String message;
    private final String proposedAttendeeId;
    private final String proposedSeatId;

    private ActionResult(boolean success, String message, String proposedAttendeeId, String proposedSeatId) {
        this.success = success;
        this.message = message;
        this.proposedAttendeeId = proposedAttendeeId;
        this.proposedSeatId = proposedSeatId;
    }

    public static ActionResult ok(String message) {
        return new ActionResult(true, message, null, null);
    }

    public static ActionResult rejected(String message) {
        return new ActionResult(false, message, null, null);
    }

    public static ActionResult rejectedWithProposal(String message, String attendeeId, String seatId) {
        return new ActionResult(false, message, attendeeId, seatId);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public boolean hasProposal() {
        return proposedAttendeeId != null;
    }

    public String getProposedAttendeeId() {
        return proposedAttendeeId;
    }

    public String getProposedSeatId() {
        return proposedSeatId;
    }
}
