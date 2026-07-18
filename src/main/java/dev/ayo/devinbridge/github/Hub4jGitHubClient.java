package dev.ayo.devinbridge.github;

import dev.ayo.devinbridge.domain.PrStatus;
import org.kohsuke.github.*;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Real GitHub client
 */
public final class Hub4jGitHubClient implements GitHubClient {

    private static final Pattern PR_NUMBER = Pattern.compile("/pull/(\\d+)/?$");

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

    private static int prNumberOf(String prUrl) {
        Matcher matcher = PR_NUMBER.matcher(prUrl);
        if (!matcher.find()) {
            throw new GitHubApiException("Could not extract a PR number from " + prUrl);
        }
        return Integer.parseInt(matcher.group(1));
    }

    @Override
    public PrStatus getPrStatus(String prUrl) {
        int number = prNumberOf(prUrl);
        try {
            GHPullRequest pr = repo().getPullRequest(number);
            if (pr.isMerged()) {
                return PrStatus.MERGED;
            }
            return pr.getState() == GHIssueState.CLOSED ? PrStatus.CLOSED : PrStatus.OPEN;
        } catch (IOException e) {
            throw new GitHubApiException("getPrStatus failed for " + prUrl, e);
        }
    }
}
