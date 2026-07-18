package dev.ayo.devinbridge.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ayo.devinbridge.domain.SessionState;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class SessionStoreTest {

    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void registerReturnsTrueForNewIssue() {
        SessionStore store = new SessionStore();
        assertTrue(store.register(1, "Fix the bug", "acme/widgets", now));
        assertEquals(1, store.all().size());
        assertEquals("Fix the bug", store.get(1).issueTitle());
    }

    @Test
    void secondRegisterOfSameIssueIsNoOp() {
        SessionStore store = new SessionStore();
        assertTrue(store.register(1, "Fix the bug", "acme/widgets", now));
        assertFalse(store.register(1, "A different title", "acme/widgets", now.plusSeconds(10)));

        assertEquals(1, store.all().size());
        assertEquals("Fix the bug", store.get(1).issueTitle());
    }

    @Test
    void updateChangesStateOfTrackedIssue() {
        SessionStore store = new SessionStore();
        store.register(1, "Fix the bug", "acme/widgets", now);
        store.update(1, new SessionState.Running("devin-1", now));
        assertEquals("RUNNING", store.get(1).state().label());
    }

    @Test
    void updateOfUntrackedIssueIsNoOp() {
        SessionStore store = new SessionStore();
        store.update(42, new SessionState.Running("devin-1", now));
        assertNull(store.get(42));
    }

    @Test
    void countsByStateReflectsAllTrackedSessions() {
        SessionStore store = new SessionStore();
        store.register(1, "Issue one", "acme/widgets", now);
        store.register(2, "Issue two", "acme/widgets", now);
        store.update(2, new SessionState.Running("devin-2", now));

        var counts = store.countsByState();
        assertEquals(1L, counts.get("QUEUED"));
        assertEquals(1L, counts.get("RUNNING"));
    }
}
