package dev.ayo.devinbridge.github;

public interface GitHubClient {

    void postComment(long issueNumber, String body);
}
