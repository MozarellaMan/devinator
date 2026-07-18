package dev.ayo.devinbridge.github;

import dev.ayo.devinbridge.domain.PrStatus;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * {@link Hub4jGitHubClient} is a thin adapter over hub4j's {@code GHRepository}, so
 * it's tested with a Mockito-mocked {@code GHRepository} rather than real network
 * calls — no WireMock/fixture server needed for what is essentially delegation logic.
 */
class Hub4jGitHubClientTest {

    @Test
    void postCommentDelegatesToTheIssuesCommentMethod() throws Exception {
        GHRepository repo = mock(GHRepository.class);
        GHIssue targetIssue = mock(GHIssue.class);
        when(repo.getIssue(42)).thenReturn(targetIssue);

        Hub4jGitHubClient client = new Hub4jGitHubClient(repo);
        client.postComment(42, "Devin opened a pull request: https://example/pr/1");

        verify(targetIssue).comment(anyString());
    }

    @Test
    void postCommentWrapsIoExceptionAsGitHubApiException() throws Exception {
        GHRepository repo = mock(GHRepository.class);
        when(repo.getIssue(42)).thenThrow(new java.io.IOException("network down"));

        Hub4jGitHubClient client = new Hub4jGitHubClient(repo);

        org.junit.jupiter.api.Assertions.assertThrows(
                GitHubApiException.class, () -> client.postComment(42, "body"));
    }

    @Test
    void getPrStatusReturnsMergedWhenPullRequestIsMerged() throws Exception {
        GHRepository repo = mock(GHRepository.class);
        GHPullRequest pr = mock(GHPullRequest.class);
        when(pr.isMerged()).thenReturn(true);
        when(repo.getPullRequest(17)).thenReturn(pr);

        Hub4jGitHubClient client = new Hub4jGitHubClient(repo);

        assertEquals(PrStatus.MERGED, client.getPrStatus("https://github.com/acme/widgets/pull/17"));
    }

    @Test
    void getPrStatusReturnsClosedWhenPullRequestIsClosedButNotMerged() throws Exception {
        GHRepository repo = mock(GHRepository.class);
        GHPullRequest pr = mock(GHPullRequest.class);
        when(pr.isMerged()).thenReturn(false);
        when(pr.getState()).thenReturn(GHIssueState.CLOSED);
        when(repo.getPullRequest(17)).thenReturn(pr);

        Hub4jGitHubClient client = new Hub4jGitHubClient(repo);

        assertEquals(PrStatus.CLOSED, client.getPrStatus("https://github.com/acme/widgets/pull/17"));
    }

    @Test
    void getPrStatusReturnsOpenWhenPullRequestIsStillOpen() throws Exception {
        GHRepository repo = mock(GHRepository.class);
        GHPullRequest pr = mock(GHPullRequest.class);
        when(pr.isMerged()).thenReturn(false);
        when(pr.getState()).thenReturn(GHIssueState.OPEN);
        when(repo.getPullRequest(17)).thenReturn(pr);

        Hub4jGitHubClient client = new Hub4jGitHubClient(repo);

        assertEquals(PrStatus.OPEN, client.getPrStatus("https://github.com/acme/widgets/pull/17"));
    }

    @Test
    void getPrStatusWrapsIoExceptionAsGitHubApiException() throws Exception {
        GHRepository repo = mock(GHRepository.class);
        when(repo.getPullRequest(17)).thenThrow(new java.io.IOException("network down"));

        Hub4jGitHubClient client = new Hub4jGitHubClient(repo);

        assertThrows(GitHubApiException.class,
                () -> client.getPrStatus("https://github.com/acme/widgets/pull/17"));
    }
}
