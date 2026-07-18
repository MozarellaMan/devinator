package dev.ayo.devinbridge.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHRepository;

/**
 * {@link Hub4jGitHubClient} is a thin adapter over hub4j's {@code GHRepository}, so
 * it's tested with a Mockito-mocked {@code GHRepository} rather than real network
 * calls — no WireMock/fixture server needed for what is essentially delegation logic.
 */
class Hub4jGitHubClientTest {

    private GHLabel label(String name) {
        GHLabel label = mock(GHLabel.class);
        when(label.getName()).thenReturn(name);
        return label;
    }

    private GHIssue issue(int number, String title, boolean isPr, String... labelNames) {
        GHIssue issue = mock(GHIssue.class);
        when(issue.getNumber()).thenReturn(number);
        when(issue.getTitle()).thenReturn(title);
        when(issue.isPullRequest()).thenReturn(isPr);
        List<GHLabel> labels = Stream.of(labelNames).map(this::label).toList();
        when(issue.getLabels()).thenReturn(labels);
        return issue;
    }

    @Test
    void listLabelledIssuesFiltersByLabelAndExcludesPullRequests() throws Exception {
        GHRepository repo = mock(GHRepository.class);
        GHIssue labelled = issue(1, "Fix the bug", false, "devin-fix", "bug");
        GHIssue unlabelled = issue(2, "Unrelated issue", false, "bug");
        GHIssue labelledPr = issue(3, "A PR that happens to carry the label", true, "devin-fix");
        when(repo.getIssues(GHIssueState.OPEN)).thenReturn(List.of(labelled, unlabelled, labelledPr));

        Hub4jGitHubClient client = new Hub4jGitHubClient(repo);
        List<GitHubClient.Issue> result = client.listLabelledIssues("devin-fix");

        assertEquals(1, result.size());
        assertEquals(1, result.getFirst().number());
        assertEquals("Fix the bug", result.getFirst().title());
        assertTrue(result.getFirst().labels().contains("devin-fix"));
    }

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
    void listLabelledIssuesWrapsIoExceptionAsGitHubApiException() throws Exception {
        GHRepository repo = mock(GHRepository.class);
        when(repo.getIssues(GHIssueState.OPEN)).thenThrow(new java.io.IOException("network down"));

        Hub4jGitHubClient client = new Hub4jGitHubClient(repo);
        org.junit.jupiter.api.Assertions.assertThrows(
                GitHubApiException.class, () -> client.listLabelledIssues("devin-fix"));
    }
}
