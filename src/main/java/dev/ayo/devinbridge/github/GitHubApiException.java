package dev.ayo.devinbridge.github;

/**
 * Wraps any failure talking to the GitHub API (non-2xx response, network error).
 */
public final class GitHubApiException extends RuntimeException {

    public GitHubApiException(String message) {
        super(message);
    }

    public GitHubApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
