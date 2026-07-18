package dev.ayo.devinbridge.domain;

import java.time.Instant;

/**
 * A GitHub issue being remediated, plus its current {@link SessionState}.
 */
public record TrackedSession(
        long issueNumber,
        String issueTitle,
        String repo,
        SessionState state,
        Instant createdAt
) {

    /**
     * Returns a copy of this session with a new state, keeping identity fields fixed.
     */
    public TrackedSession withState(SessionState newState) {
        return new TrackedSession(issueNumber, issueTitle, repo, newState, createdAt);
    }
}
