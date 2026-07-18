package dev.ayo.devinbridge.github;

import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.io.IOException;

/**
 * Real GitHub client
 */
public final class Hub4jGitHubClient implements GitHubClient {

    private final String repoFullName;
    private final GitHub gitHub;
    private final GHRepository injectedRepo;

    public Hub4jGitHubClient(String repoFullName, String token) {
        this.repoFullName = repoFullName;
        this.injectedRepo = null;
        try {
            this.gitHub = new GitHubBuilder().withOAuthToken(token).build();
        } catch (IOException e) {
            throw new GitHubApiException("Failed to build GitHub client", e);
        }
    }

    Hub4jGitHubClient(GHRepository repo) {
        this.repoFullName = null;
        this.gitHub = null;
        this.injectedRepo = repo;
    }

    private GHRepository repo() {
        if (injectedRepo != null) {
            return injectedRepo;
        }
        try {
            return gitHub.getRepository(repoFullName);
        } catch (IOException e) {
            throw new GitHubApiException("Failed to resolve repository " + repoFullName, e);
        }
    }

    @Override
    public void postComment(long issueNumber, String body) {
        try {
            repo().getIssue((int) issueNumber).comment(body);
        } catch (IOException e) {
            throw new GitHubApiException("postComment failed for issue #" + issueNumber, e);
        }
    }
}
