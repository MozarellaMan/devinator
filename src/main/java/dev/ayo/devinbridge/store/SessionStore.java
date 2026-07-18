package dev.ayo.devinbridge.store;

import dev.ayo.devinbridge.domain.SessionState;
import dev.ayo.devinbridge.domain.TrackedSession;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of tracked sessions, keyed by GitHub issue number.
 */
public final class SessionStore {

    private final Map<Long, TrackedSession> sessions = new ConcurrentHashMap<>();

    /**
     * Registers a newly-seen issue as {@code Queued}. Idempotent: if the issue is
     * already tracked (by either the webhook or the fallback scan), this is a no-op.
     * Returns false if already exists
     */
    public boolean register(long issueNumber, String issueTitle, String repo, Instant now) {
        TrackedSession fresh = new TrackedSession(
                issueNumber,
                issueTitle,
                repo,
                new SessionState.Queued(now),
                now
        );
        return sessions.putIfAbsent(issueNumber, fresh) == null;
    }

    /**
     * Replaces the state of an already-tracked session. No-op if the issue isn't tracked.
     */
    public void update(long issueNumber, SessionState newState) {
        sessions.computeIfPresent(issueNumber, (k, existing) -> existing.withState(newState));
    }

    public TrackedSession get(long issueNumber) {
        return sessions.get(issueNumber);
    }

    public List<TrackedSession> all() {
        return List.copyOf(sessions.values());
    }

    public Map<String, Long> countsByState() {
        Map<String, Long> counts = new ConcurrentHashMap<>();
        for (TrackedSession s : sessions.values()) {
            counts.merge(s.state().label(), 1L, Long::sum);
        }
        return counts;
    }
}
