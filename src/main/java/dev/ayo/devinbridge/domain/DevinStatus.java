package dev.ayo.devinbridge.domain;

/**
 * Normalized view of the Devin v3 API's two-field status model —
 * {@code status} (new/claimed/running/exit/error/suspended/resuming) plus a nullable
 * {@code status_detail} that's only documented for {@code status=running} (working/
 * waiting_for_user/waiting_for_approval/finished) and {@code status=suspended} (a
 * grab-bag of suspension reasons)
 * (<a href="https://docs.devin.ai/api-reference/v3/sessions/get-organizations-session">...</a>).
 */
public enum DevinStatus {
    /**
     * Devin is actively working the session (or still starting up).
     */
    WORKING,
    /**
     * Devin is waiting on input (e.g. a clarifying question or approval). Treated as still-running.
     */
    BLOCKED,
    /**
     * Devin has finished the session.
     */
    FINISHED,
    /**
     * The session ended without finishing — suspended (any reason), errored, or exited.
     */
    EXPIRED,
    /**
     * Any other/unrecognised combination. Treated as still-running.
     */
    UNKNOWN;

    /**
     * Maps the v3 {@code (status, status_detail)} pair to a {@link DevinStatus}.
     */
    public static DevinStatus fromRaw(String status, String statusDetail) {
        if (status == null) {
            return UNKNOWN;
        }
        return switch (status) {
            case "new", "claimed", "resuming" -> WORKING;
            case "running" -> switch (statusDetail == null ? "working" : statusDetail) {
                case "working" -> WORKING;
                case "waiting_for_user", "waiting_for_approval" -> BLOCKED;
                case "finished" -> FINISHED;
                default -> UNKNOWN;
            };
            case "suspended", "error", "exit" -> EXPIRED;
            default -> UNKNOWN;
        };
    }
}
