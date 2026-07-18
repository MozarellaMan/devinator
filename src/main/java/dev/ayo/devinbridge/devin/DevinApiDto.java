package dev.ayo.devinbridge.devin;

import java.util.List;

/**
 * Wire DTOs for the Devin v3 API
 * <a href="https://docs.devin.ai/api-reference/v3/sessions/post-organizations-sessions">...</a>
 */
final class DevinApiDto {

    private DevinApiDto() {
    }

    record CreateSessionRequest(String prompt, String title, List<String> tags) {
    }

    record CreateSessionResponse(String sessionId, String url) {
    }

    record GetSessionResponse(String sessionId, String status, String statusDetail, List<PullRequest> pullRequests) {
    }

    record PullRequest(String prUrl, String prState) {
    }
}
