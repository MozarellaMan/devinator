package dev.ayo.devinbridge.github;

import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHRepository;

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
}
