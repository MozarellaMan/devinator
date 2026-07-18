package dev.ayo.devinbridge.devin;

import dev.ayo.devinbridge.domain.StatusSnapshot;

/**
 * Talks to Devin on behalf of the orchestrator. Two implementations:
 * {@link HttpDevinClient} (real API) and {@link MockDevinClient} (offline demo,
 * enabled via {@code MOCK_DEVIN=true}).
 */
public interface DevinClient {

    /**
     * Starts a Devin session with the given prompt and returns its session id.
     * {@code repo} is passed through for logging/prompt context, not as a separate
     * API field — the real Devin API takes only a free-text prompt.
     */
    String createSession(String prompt, String repo);

    /**
     * Fetches the current status of a previously-created session.
     */
    StatusSnapshot getStatus(String sessionId);

    /**
     * Terminates a session.
     */
    void terminateSession(String sessionId);
}
