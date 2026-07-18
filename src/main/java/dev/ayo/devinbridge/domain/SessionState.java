package dev.ayo.devinbridge.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * The lifecycle of a single Devin remediation session, one issue at a time.
 *
 * <p>This is a sealed interface with a fixed set of record implementations so the
 * compiler can prove {@link #advance} is exhaustive: every (state, event) pair is
 * either handled explicitly or the switch fails to compile. There is no other place
 * in the codebase that transitions a session's state.
 */
public sealed interface SessionState {

    /**
     * The single authority for legal transitions. Throws {@link IllegalStateTransition}
     * for any (state, event) pair not explicitly listed below.
     *
     * <p>Legal transitions:
     * <pre>
     * Queued   -> Running | Failed
     * Running  -> Running | PrOpened | Completed | Failed
     * PrOpened -> PrOpened | Completed | Failed
     * Completed, Failed -> terminal, no transitions out
     * </pre>
     */
    static SessionState advance(SessionState current, Event event) {
        return switch (current) {
            case Queued q -> switch (event) {
                case Event.SessionStarted e -> new Running(e.devinSessionId(), e.now());
                case Event.SessionFailed e -> new Failed(null, e.reason(), e.now());
                default -> throw new IllegalStateTransition(current, event);
            };

            case Running r -> switch (event) {
                case Event.StillRunning e -> new Running(r.devinSessionId(), r.started());
                case Event.PrDetected e -> new PrOpened(r.devinSessionId(), e.prUrl(), e.now());
                case Event.SessionFinished e -> new Completed(
                        r.devinSessionId(), e.prUrl(), Duration.between(e.since(), e.now())
                );
                case Event.SessionFailed e -> new Failed(r.devinSessionId(), e.reason(), e.now());
                default -> throw new IllegalStateTransition(current, event);
            };

            case PrOpened p -> switch (event) {
                case Event.PrDetected e -> new PrOpened(p.devinSessionId(), e.prUrl(), e.now());
                case Event.SessionFinished e -> new Completed(
                        p.devinSessionId(), p.prUrl(), Duration.between(e.since(), e.now())
                );
                case Event.SessionFailed e -> new Failed(p.devinSessionId(), e.reason(), e.now());
                default -> throw new IllegalStateTransition(current, event);
            };
            case Completed ignored -> throw new IllegalStateTransition(current, event);
            case Failed ignored -> throw new IllegalStateTransition(current, event);
        };
    }

    /**
     * Human-readable label used by the dashboard and status JSON.
     */
    default String label() {
        return switch (this) {
            case Queued q -> "QUEUED";
            case Running r -> "RUNNING";
            case PrOpened p -> "PR_OPENED";
            case Completed c -> "COMPLETED";
            case Failed f -> "FAILED";
        };
    }

    default boolean isTerminal() {
        return switch (this) {
            case Completed c -> true;
            case Failed f -> true;
            case Queued q -> false;
            case Running r -> false;
            case PrOpened p -> false;
        };
    }

    /**
     * Events that drive {@link #advance}. Each maps to a request to move to a new state.
     */
    sealed interface Event {
        /**
         * Devin accepted the session and gave us its id — Queued -> Running.
         */
        record SessionStarted(String devinSessionId, Instant now) implements Event {
        }

        /**
         * Poll observed the session still working/blocked, no PR yet — Running -> Running.
         */
        record StillRunning(Instant now) implements Event {
        }

        /**
         * Poll observed a PR url for the first time — Running/PrOpened -> PrOpened.
         */
        record PrDetected(String prUrl, Instant now) implements Event {
        }

        /**
         * Poll observed the session as finished with a PR — * -> Completed.
         */
        record SessionFinished(String prUrl, Instant since, Instant now) implements Event {
        }

        /**
         * Poll observed expiry, an API error, or a timeout cap breach — * -> Failed.
         */
        record SessionFailed(String reason, Instant now) implements Event {
        }
    }

    record Queued(Instant at) implements SessionState {
    }

    record Running(String devinSessionId, Instant started) implements SessionState {
    }

    record PrOpened(String devinSessionId, String prUrl, Instant at) implements SessionState {
    }

    // ---- Events that can move a session forward ----

    record Completed(String devinSessionId, String prUrl, Duration took) implements SessionState {
    }

    record Failed(String devinSessionId, String reason, Instant at) implements SessionState {
    }
}
