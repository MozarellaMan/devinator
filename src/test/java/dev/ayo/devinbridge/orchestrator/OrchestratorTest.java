package dev.ayo.devinbridge.orchestrator;

import dev.ayo.devinbridge.devin.DevinClient;
import dev.ayo.devinbridge.devin.MockDevinClient;
import dev.ayo.devinbridge.domain.DevinStatus;
import dev.ayo.devinbridge.domain.PrStatus;
import dev.ayo.devinbridge.domain.SessionState;
import dev.ayo.devinbridge.domain.StatusSnapshot;
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

    @Test
    void sessionCompletesWhenItsOpenPrIsMerged() {
        SessionStore store = new SessionStore();
        FakeGitHubClient github = new FakeGitHubClient();
        FakeDevinClient devin = new FakeDevinClient();
        Orchestrator orchestrator = new Orchestrator(
                store, devin, github, Clock.systemUTC(), Duration.ofMinutes(30)
        );

        orchestrator.onIssueEvent(1, "Some issue", List.of("devin-fix"), "acme/widgets");
        orchestrator.poll();
        assertInstanceOf(SessionState.PrOpened.class, store.get(1).state());
        assertEquals(1, devin.statusChecks, "one Devin status check to first observe the PR");
        assertEquals(1, github.commentsPosted.size(), "PR-opened comment posted once");

        github.prStatus = PrStatus.MERGED;
        orchestrator.poll();

        assertInstanceOf(SessionState.Completed.class, store.get(1).state());
        assertEquals(2, devin.statusChecks,
                "Devin is still polled every cycle even once a PR is open -- just observationally; "
                        + "the PR itself drives completion");
        assertEquals(1, github.commentsPosted.size(), "no second comment posted on completion");
        assertEquals(List.of("fake-session-1"), devin.terminatedSessionIds,
                "Devin session should be terminated once its PR merges");
    }

    @Test
    void sessionFailsWhenItsOpenPrIsClosedWithoutMerging() {
        SessionStore store = new SessionStore();
        FakeGitHubClient github = new FakeGitHubClient();
        Orchestrator orchestrator = new Orchestrator(
                store, new FakeDevinClient(), github, Clock.systemUTC(), Duration.ofMinutes(30)
        );

        orchestrator.onIssueEvent(2, "Another issue", List.of("devin-fix"), "acme/widgets");
        orchestrator.poll();
        assertInstanceOf(SessionState.PrOpened.class, store.get(2).state());

        github.prStatus = PrStatus.CLOSED;
        orchestrator.poll();

        SessionState.Failed failed = assertInstanceOf(SessionState.Failed.class, store.get(2).state());
        assertEquals("Pull request was closed without merging", failed.reason());
    }

    @Test
    void closingWithoutMergingDoesNotTerminateTheDevinSession() {
        SessionStore store = new SessionStore();
        FakeGitHubClient github = new FakeGitHubClient();
        FakeDevinClient devin = new FakeDevinClient();
        Orchestrator orchestrator = new Orchestrator(
                store, devin, github, Clock.systemUTC(), Duration.ofMinutes(30)
        );

        orchestrator.onIssueEvent(2, "Another issue", List.of("devin-fix"), "acme/widgets");
        orchestrator.poll();

        github.prStatus = PrStatus.CLOSED;
        orchestrator.poll();

        assertTrue(devin.terminatedSessionIds.isEmpty(),
                "closing without merging is not the merge signal, so termination should not fire");
    }

    @Test
    void sessionStaysPrOpenedWhilePrIsStillOpen() {
        SessionStore store = new SessionStore();
        FakeGitHubClient github = new FakeGitHubClient();
        FakeDevinClient devin = new FakeDevinClient();
        Orchestrator orchestrator = new Orchestrator(
                store, devin, github, Clock.systemUTC(), Duration.ofMinutes(30)
        );

        orchestrator.onIssueEvent(3, "Yet another issue", List.of("devin-fix"), "acme/widgets");
        orchestrator.poll();
        orchestrator.poll();
        orchestrator.poll();

        assertInstanceOf(SessionState.PrOpened.class, store.get(3).state());
        assertTrue(devin.terminatedSessionIds.isEmpty(), "an open PR should not trigger termination");
        assertEquals(3, devin.statusChecks,
                "Devin is polled every cycle regardless of state -- once for Running, twice more while PrOpened");
    }

    @Test
    void devinReportingFinishedWhilePrStillOpenCompletesTheSession() {
        SessionStore store = new SessionStore();
        FakeGitHubClient github = new FakeGitHubClient();
        FakeDevinClient devin = new FakeDevinClient();
        Orchestrator orchestrator = new Orchestrator(
                store, devin, github, Clock.systemUTC(), Duration.ofMinutes(30)
        );

        orchestrator.onIssueEvent(5, "Yet another issue", List.of("devin-fix"), "acme/widgets");
        orchestrator.poll(); // Running -> PrOpened via WORKING + a PR
        assertInstanceOf(SessionState.PrOpened.class, store.get(5).state());

        // Devin now claims it's finished, even though the PR itself is still just open
        // (not merged) -- either signal can complete the session on its own.
        devin.statusToReport = DevinStatus.FINISHED;
        orchestrator.poll();

        assertInstanceOf(SessionState.Completed.class, store.get(5).state(),
                "Devin reporting FINISHED completes the session even without a PR merge");
        assertTrue(devin.terminatedSessionIds.isEmpty(),
                "no need to force-terminate a session Devin already considers finished on its own");
    }

    @Test
    void devinReportingExpiredWhilePrStillOpenDoesNotFailTheSession() {
        SessionStore store = new SessionStore();
        FakeGitHubClient github = new FakeGitHubClient();
        FakeDevinClient devin = new FakeDevinClient();
        Orchestrator orchestrator = new Orchestrator(
                store, devin, github, Clock.systemUTC(), Duration.ofMinutes(30)
        );

        orchestrator.onIssueEvent(6, "Yet another issue", List.of("devin-fix"), "acme/widgets");
        orchestrator.poll();
        assertInstanceOf(SessionState.PrOpened.class, store.get(6).state());

        devin.statusToReport = DevinStatus.EXPIRED;
        orchestrator.poll();

        assertInstanceOf(SessionState.PrOpened.class, store.get(6).state(),
                "PR status is authoritative -- Devin erroring/expiring must not fail "
                        + "the session while the PR is still open");
    }

    @Test
    void devinApiErrorWhileObservingDoesNotPreventThePrCheckFromCompletingTheSession() {
        SessionStore store = new SessionStore();
        FakeGitHubClient github = new FakeGitHubClient();
        FakeDevinClient devin = new FakeDevinClient();
        Orchestrator orchestrator = new Orchestrator(
                store, devin, github, Clock.systemUTC(), Duration.ofMinutes(30)
        );

        orchestrator.onIssueEvent(7, "Yet another issue", List.of("devin-fix"), "acme/widgets");
        orchestrator.poll();
        assertInstanceOf(SessionState.PrOpened.class, store.get(7).state());

        devin.throwOnStatusCheck = true;
        github.prStatus = PrStatus.MERGED;
        orchestrator.poll();

        assertInstanceOf(SessionState.Completed.class, store.get(7).state(),
                "a failed Devin status observation must not block the PR check from completing the session");
    }

    @Test
    void terminationFailureDoesNotPreventSessionFromCompleting() {
        SessionStore store = new SessionStore();
        FakeGitHubClient github = new FakeGitHubClient();
        FakeDevinClient devin = new FakeDevinClient();
        devin.throwOnTerminate = true;
        Orchestrator orchestrator = new Orchestrator(
                store, devin, github, Clock.systemUTC(), Duration.ofMinutes(30)
        );

        orchestrator.onIssueEvent(4, "Yet another issue", List.of("devin-fix"), "acme/widgets");
        orchestrator.poll();

        github.prStatus = PrStatus.MERGED;
        orchestrator.poll();

        assertInstanceOf(SessionState.Completed.class, store.get(4).state(),
                "a failed termination call must not undo the Completed transition -- "
                        + "the merged PR is still the source of truth");
    }

    private static final class FakeGitHubClient implements GitHubClient {
        final List<PostedComment> commentsPosted = new ArrayList<>();
        PrStatus prStatus = PrStatus.OPEN;

        @Override
        public void postComment(long issueNumber, String body) {
            commentsPosted.add(new PostedComment(issueNumber, body));
        }

        @Override
        public PrStatus getPrStatus(String prUrl) {
            return prStatus;
        }

        record PostedComment(long issueNumber, String body) {
        }
    }

    private static final class FakeDevinClient implements DevinClient {
        final List<String> terminatedSessionIds = new ArrayList<>();
        int statusChecks = 0;
        DevinStatus statusToReport = DevinStatus.WORKING;
        boolean throwOnStatusCheck = false;
        boolean throwOnTerminate = false;

        @Override
        public String createSession(String prompt, String repo) {
            return "fake-session-1";
        }

        @Override
        public StatusSnapshot getStatus(String sessionId) {
            statusChecks++;
            if (throwOnStatusCheck) {
                throw new RuntimeException("Devin API error: transient failure");
            }
            return new StatusSnapshot(sessionId, statusToReport, "https://github.com/acme/widgets/pull/7");
        }

        @Override
        public void terminateSession(String sessionId) {
            if (throwOnTerminate) {
                throw new RuntimeException("Devin API error: session already gone");
            }
            terminatedSessionIds.add(sessionId);
        }
    }
}
