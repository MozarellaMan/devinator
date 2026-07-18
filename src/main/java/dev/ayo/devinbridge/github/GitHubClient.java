package dev.ayo.devinbridge.github;

import dev.ayo.devinbridge.domain.PrStatus;

public interface GitHubClient {

    void postComment(long issueNumber, String body);

    PrStatus getPrStatus(String prUrl);
}
