package dev.ayo.devinbridge.orchestrator;

import dev.ayo.devinbridge.devin.MockDevinClient;
import dev.ayo.devinbridge.domain.SessionState;
import dev.ayo.devinbridge.github.GitHubClient;
import dev.ayo.devinbridge.store.SessionStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Happy-path test using {@link MockDevinClient}: a labelled issue arrives via the
 * webhook path, gets queued and started, and repeated polling drives it all the way
 * to Completed — with a PR-link comment posted back to GitHub exactly once.
 */
class OrchestratorTest {

    @Test
    void labelledIssueGoesFromQueuedToCompletedAndCommentsOncePrAppears() {
        SessionStore store = new SessionStore();
        FakeGitHubClient github = new FakeGitHubClient();
        Orchestrator orchestrator = new Orchestrator(
                store, new MockDevinClient(), github, Clock.systemUTC(), Duration.ofMinutes(30)
        );

        orchestrator.onIssueEvent(
                42, "Flaky test in CI", List.of("devin-fix", "bug"), "acme/widgets"
        );

        assertInstanceOf(
                SessionState.Running.class,
                store.get(42).state(),
                "onIssueEvent should immediately move a newly-registered issue to Running"
        );

        // MockDevinClient reports "working" for the first two polls, then "finished" with a fake PR on the third
        orchestrator.poll();
        assertInstanceOf(SessionState.Running.class, store.get(42).state());
        orchestrator.poll();
        assertInstanceOf(SessionState.Running.class, store.get(42).state());
        orchestrator.poll();
        SessionState.Completed completed = assertInstanceOf(
                SessionState.Completed.class, store.get(42).state()
        );
        assertTrue(completed.prUrl().contains("github.com"));

        assertEquals(1, github.commentsPosted.size(), "PR link should be commented exactly once");
        assertEquals(42, github.commentsPosted.getFirst().issueNumber());
        assertTrue(github.commentsPosted.getFirst().body().contains(completed.prUrl()));

        // Further polling on a terminal session is a no-op
        orchestrator.poll();
        assertEquals(1, github.commentsPosted.size());
    }

    @Test
    void unlabelledIssueIsIgnored() {
        SessionStore store = new SessionStore();
        Orchestrator orchestrator = new Orchestrator(
                store, new MockDevinClient(), new FakeGitHubClient(),
                Clock.systemUTC(), Duration.ofMinutes(30)
        );

        orchestrator.onIssueEvent(7, "Unrelated issue", List.of("bug"), "acme/widgets");

        assertEquals(0, store.all().size());
    }

    @Test
    void duplicateEventForAlreadyTrackedIssueDoesNotStartASecondSession() {
        SessionStore store = new SessionStore();
        MockDevinClient devin = new MockDevinClient();
        Orchestrator orchestrator = new Orchestrator(
                store, devin, new FakeGitHubClient(), Clock.systemUTC(), Duration.ofMinutes(30)
        );

        orchestrator.onIssueEvent(1, "Issue", List.of("devin-fix"), "acme/widgets");
        String firstSessionId = ((SessionState.Running) store.get(1).state()).devinSessionId();


        orchestrator.onIssueEvent(1, "Issue", List.of("devin-fix"), "acme/widgets");
        String secondSessionId = ((SessionState.Running) store.get(1).state()).devinSessionId();

        assertEquals(firstSessionId, secondSessionId);
    }

    private static final class FakeGitHubClient implements GitHubClient {
        final List<PostedComment> commentsPosted = new ArrayList<>();

        @Override
        public void postComment(long issueNumber, String body) {
            commentsPosted.add(new PostedComment(issueNumber, body));
        }

        record PostedComment(long issueNumber, String body) {
        }
    }
}
