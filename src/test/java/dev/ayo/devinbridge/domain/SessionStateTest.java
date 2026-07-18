package dev.ayo.devinbridge.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * Exercises every legal transition in {@link SessionState#advance} and a
 * representative sample of illegal ones, including both terminal states rejecting
 * everything.
 */
class SessionStateTest {

    private final Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
    private final Instant t1 = t0.plusSeconds(60);
    private final Instant t2 = t0.plusSeconds(120);

    // ---- Legal transitions ----

    @Test
    void queuedToRunningOnSessionStarted() {
        SessionState result = SessionState.advance(
                new SessionState.Queued(t0), new SessionState.Event.SessionStarted("d1", t1)
        );
        SessionState.Running running = assertInstanceOf(SessionState.Running.class, result);
        assertEquals("d1", running.devinSessionId());
        assertEquals(t1, running.started());
    }

    @Test
    void queuedToFailedOnSessionFailed() {
        SessionState result = SessionState.advance(
                new SessionState.Queued(t0), new SessionState.Event.SessionFailed("boom", t1)
        );
        assertInstanceOf(SessionState.Failed.class, result);
    }

    @Test
    void runningStaysRunningOnStillRunning() {
        SessionState.Running running = new SessionState.Running("d1", t0);
        SessionState result = SessionState.advance(running, new SessionState.Event.StillRunning(t1));
        assertInstanceOf(SessionState.Running.class, result);
        assertEquals("d1", ((SessionState.Running) result).devinSessionId());
    }

    @Test
    void runningToPrOpenedOnPrDetected() {
        SessionState.Running running = new SessionState.Running("d1", t0);
        SessionState result = SessionState.advance(
                running, new SessionState.Event.PrDetected("https://pr", t1)
        );
        SessionState.PrOpened opened = assertInstanceOf(SessionState.PrOpened.class, result);
        assertEquals("https://pr", opened.prUrl());
    }

    @Test
    void runningToCompletedOnSessionFinishedUsesSinceForDuration() {
        SessionState.Running running = new SessionState.Running("d1", t1); // started later than queued
        SessionState result = SessionState.advance(
                running, new SessionState.Event.SessionFinished("https://pr", t0, t2)
        );
        SessionState.Completed completed = assertInstanceOf(SessionState.Completed.class, result);
        assertEquals(Duration.between(t0, t2), completed.took());
    }

    @Test
    void runningToFailedOnSessionFailed() {
        SessionState.Running running = new SessionState.Running("d1", t0);
        SessionState result = SessionState.advance(running, new SessionState.Event.SessionFailed("err", t1));
        assertInstanceOf(SessionState.Failed.class, result);
    }

    @Test
    void prOpenedStaysPrOpenedOnPrDetected() {
        SessionState.PrOpened opened = new SessionState.PrOpened("d1", "https://pr", t0);
        SessionState result = SessionState.advance(opened, new SessionState.Event.PrDetected("https://pr2", t1));
        assertInstanceOf(SessionState.PrOpened.class, result);
    }

    @Test
    void prOpenedToCompletedOnSessionFinishedUsesSinceForDuration() {
        SessionState.PrOpened opened = new SessionState.PrOpened("d1", "https://pr", t1);
        SessionState result = SessionState.advance(
                opened, new SessionState.Event.SessionFinished("https://pr", t0, t2)
        );
        SessionState.Completed completed = assertInstanceOf(SessionState.Completed.class, result);
        assertEquals(Duration.between(t0, t2), completed.took());
    }

    @Test
    void prOpenedToFailedOnSessionFailed() {
        SessionState.PrOpened opened = new SessionState.PrOpened("d1", "https://pr", t0);
        SessionState result = SessionState.advance(opened, new SessionState.Event.SessionFailed("err", t1));
        assertInstanceOf(SessionState.Failed.class, result);
    }

    // ---- Illegal transitions ----

    @Test
    void queuedRejectsStillRunning() {
        assertThrows(IllegalStateTransition.class, () -> SessionState.advance(
                new SessionState.Queued(t0), new SessionState.Event.StillRunning(t1)));
    }

    @Test
    void queuedRejectsPrDetected() {
        assertThrows(IllegalStateTransition.class, () -> SessionState.advance(
                new SessionState.Queued(t0), new SessionState.Event.PrDetected("https://pr", t1)));
    }

    @Test
    void completedRejectsEverything() {
        SessionState.Completed completed = new SessionState.Completed("d1", "https://pr", Duration.ofMinutes(5));
        assertThrows(IllegalStateTransition.class, () -> SessionState.advance(
                completed, new SessionState.Event.StillRunning(t1)));
        assertThrows(IllegalStateTransition.class, () -> SessionState.advance(
                completed, new SessionState.Event.SessionFailed("err", t1)));
    }

    @Test
    void failedRejectsEverything() {
        SessionState.Failed failed = new SessionState.Failed("d1", "boom", t0);
        assertThrows(IllegalStateTransition.class, () -> SessionState.advance(
                failed, new SessionState.Event.StillRunning(t1)));
        assertThrows(IllegalStateTransition.class, () -> SessionState.advance(
                failed, new SessionState.Event.SessionStarted("d2", t1)));
    }
}
