package dev.ayo.devinbridge.github;

import java.util.List;

/**
 * Talks to the GitHub REST API for the two things this app needs:
 * pulling {@code devin-fix}-labelled issues (the 60s fallback scan) and posting the PR link
 * back as a comment once Devin opens one.
 */
public interface GitHubClient {

    List<Issue> listLabelledIssues(String label);

    void postComment(long issueNumber, String body);

    record Issue(long number, String title, List<String> labels) {
    }
}
