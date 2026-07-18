package dev.ayo.devinbridge.web;

import dev.ayo.devinbridge.domain.SessionState;
import dev.ayo.devinbridge.store.SessionStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StatusViewTest {

    private final Instant t0 = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void avgTimeToPrOpenIsNullWhenNoSessionIsPrOpened() {
        SessionStore store = new SessionStore();
        store.register(1, "Queued issue", "acme/widgets", t0);

        StatusView view = StatusView.of(store);

        assertNull(view.metrics().avgTimeToPrOpenSeconds());
    }

    @Test
    void avgTimeToPrOpenIsTheGapBetweenQueuedAndPrOpenedForOneSession() {
        SessionStore store = new SessionStore();
        store.register(1, "Some issue", "acme/widgets", t0);
        store.update(1, new SessionState.PrOpened("devin-1", "https://github.com/acme/widgets/pull/1",
                t0.plus(Duration.ofMinutes(10))));

        StatusView view = StatusView.of(store);

        assertEquals(600.0, view.metrics().avgTimeToPrOpenSeconds());
    }

    @Test
    void avgTimeToPrOpenAveragesAcrossMultiplePrOpenedSessions() {
        SessionStore store = new SessionStore();
        store.register(1, "Issue one", "acme/widgets", t0);
        store.update(1, new SessionState.PrOpened("devin-1", "https://github.com/acme/widgets/pull/1",
                t0.plus(Duration.ofMinutes(10))));
        store.register(2, "Issue two", "acme/widgets", t0);
        store.update(2, new SessionState.PrOpened("devin-2", "https://github.com/acme/widgets/pull/2",
                t0.plus(Duration.ofMinutes(20))));

        StatusView view = StatusView.of(store);

        assertEquals(900.0, view.metrics().avgTimeToPrOpenSeconds()); // (600 + 1200) / 2
    }

    @Test
    void nonPrOpenedSessionsDoNotSkewTheAverage() {
        SessionStore store = new SessionStore();
        store.register(1, "Open PR issue", "acme/widgets", t0);
        store.update(1, new SessionState.PrOpened("devin-1", "https://github.com/acme/widgets/pull/1",
                t0.plus(Duration.ofMinutes(5))));
        store.register(2, "Still queued", "acme/widgets", t0);
        store.register(3, "Completed issue", "acme/widgets", t0);
        store.update(3, new SessionState.Completed("devin-3", "https://github.com/acme/widgets/pull/3",
                Duration.ofHours(1)));

        StatusView view = StatusView.of(store);

        assertEquals(300.0, view.metrics().avgTimeToPrOpenSeconds());
    }
}
