package dev.ayo.devinbridge.domain;

/**
 * Normalized view of the Devin API's {@code status_enum} field
 * (<a href="https://docs.devin.ai/api-reference/v1/sessions/retrieve-details-about-an-existing-session">...</a>).
 */
public enum DevinStatus {
    /**
     * Devin is actively working the session.
     */
    WORKING,
    /**
     * Devin is waiting on input (e.g. a clarifying question). Treated as still-running.
     */
    BLOCKED,
    /**
     * Devin has finished the session.
     */
    FINISHED,
    /**
     * The session expired before finishing.
     */
    EXPIRED,
    /**
     * Any other/unrecognised value (e.g. suspend_requested). Treated as still-running.
     */
    UNKNOWN;

    public static DevinStatus fromRaw(String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        return switch (raw) {
            case "working" -> WORKING;
            case "blocked" -> BLOCKED;
            case "finished" -> FINISHED;
            case "expired" -> EXPIRED;
            default -> UNKNOWN;
        };
    }
}
