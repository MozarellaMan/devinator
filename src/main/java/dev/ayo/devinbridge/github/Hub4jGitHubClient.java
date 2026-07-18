package dev.ayo.devinbridge.github;

import java.io.IOException;
import java.util.List;

import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

/**
 * Real GitHub client, backed by {@code org.kohsuke:github-api} (hub4j)
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
    public List<Issue> listLabelledIssues(String label) {
        try {
            return repo().getIssues(GHIssueState.OPEN).stream()
                    .filter(issue -> !issue.isPullRequest())
                    .filter(issue -> issue.getLabels().stream()
                            .anyMatch(l -> l.getName().equals(label)))
                    .map(this::toIssue)
                    .toList();
        } catch (IOException e) {
            throw new GitHubApiException("listLabelledIssues failed for label " + label, e);
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

    private Issue toIssue(GHIssue issue) {
        List<String> labels = issue.getLabels().stream().map(GHLabel::getName).toList();
        return new Issue(issue.getNumber(), issue.getTitle(), labels);
    }
}
