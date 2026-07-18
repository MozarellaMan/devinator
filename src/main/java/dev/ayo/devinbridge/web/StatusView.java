package dev.ayo.devinbridge.web;

import dev.ayo.devinbridge.domain.SessionState;
import dev.ayo.devinbridge.domain.TrackedSession;
import dev.ayo.devinbridge.store.SessionStore;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * JSON shape returned by {@code GET /status}: every tracked session, summary counts
 * by state, and a couple of simple derived metrics.
 */
public record StatusView(List<SessionView> sessions, Map<String, Long> counts, Metrics metrics) {

    public static StatusView of(SessionStore store) {
        List<TrackedSession> all = store.all();
        Instant now = Instant.now();

        List<SessionView> views = all.stream().map(t -> toView(t, now)).toList();

        double avgTimeToPrOpen = all.stream()
                .filter(t -> t.state() instanceof SessionState.PrOpened)
                .mapToLong(t -> {
                    SessionState.PrOpened prOpened = (SessionState.PrOpened) t.state();
                    return Duration.between(t.createdAt(), prOpened.at()).toSeconds();
                })
                .average()
                .orElse(Double.NaN);

        return new StatusView(
                views,
                store.countsByState(),
                new Metrics(all.size(), Double.isNaN(avgTimeToPrOpen) ? null : avgTimeToPrOpen));
    }

    private static SessionView toView(TrackedSession t, Instant now) {
        long elapsed = Duration.between(t.createdAt(), now).toSeconds();
        return switch (t.state()) {
            case SessionState.Queued _ -> new SessionView(
                    t.issueNumber(), t.issueTitle(), t.repo(), "QUEUED",
                    t.createdAt(), null, null, elapsed, null);
            case SessionState.Running r -> new SessionView(
                    t.issueNumber(), t.issueTitle(), t.repo(), "RUNNING",
                    t.createdAt(), r.devinSessionId(), null, elapsed, null);
            case SessionState.PrOpened p -> new SessionView(
                    t.issueNumber(), t.issueTitle(), t.repo(), "PR_OPENED",
                    t.createdAt(), p.devinSessionId(), p.prUrl(), elapsed, null);
            case SessionState.Completed c -> new SessionView(
                    t.issueNumber(), t.issueTitle(), t.repo(), "COMPLETED",
                    t.createdAt(), c.devinSessionId(), c.prUrl(), c.took().toSeconds(), null);
            case SessionState.Failed f -> new SessionView(
                    t.issueNumber(), t.issueTitle(), t.repo(), "FAILED",
                    t.createdAt(), f.devinSessionId(), null, elapsed, f.reason());
        };
    }

    public record SessionView(
            long issueNumber,
            String issueTitle,
            String repo,
            String state,
            Instant createdAt,
            String devinSessionId,
            String prUrl,
            long elapsedSeconds,
            String failureReason
    ) {

    }

    public record Metrics(long totalTracked, Double avgTimeToPrOpenSeconds) {
    }
}
