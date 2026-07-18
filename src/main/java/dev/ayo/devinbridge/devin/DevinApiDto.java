package dev.ayo.devinbridge.devin;

import java.util.List;

/**
 * Wire DTOs for the Devin v1 API, verified against
 * <a href="https://docs.devin.ai/api-reference/v1/sessions/create-a-new-devin-session">...</a> and
 * .../retrieve-details-about-an-existing-session. Field names below are the exact
 * snake_case wire names; {@link HttpDevinClient} configures Jackson with a snake_case
 * naming strategy so these Java records can use normal camelCase without per-field
 * {@code @JsonProperty} annotations.
 */
final class DevinApiDto {

    private DevinApiDto() {
    }

    /**
     * POST /v1/sessions request body. Only the fields this app needs are modeled.
     */
    record CreateSessionRequest(String prompt, String title, boolean idempotent, List<String> tags) {
    }

    /**
     * POST /v1/sessions response body.
     */
    record CreateSessionResponse(String sessionId, String url, Boolean isNewSession) {
    }

    /**
     * GET /v1/sessions/{id} response body (trimmed to the fields this app reads).
     * {@code status} is the raw {@code status_enum} string (working/blocked/finished/expired/...).
     */
    record GetSessionResponse(String sessionId, String status, PullRequest pullRequest) {
    }

    /**
     * Nested {@code pull_request} object on the get-session response; null until Devin opens one.
     */
    record PullRequest(String url) {
    }
}
