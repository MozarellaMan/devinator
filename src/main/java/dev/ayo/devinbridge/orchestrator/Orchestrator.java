package dev.ayo.devinbridge.orchestrator;

import dev.ayo.devinbridge.devin.DevinClient;
import dev.ayo.devinbridge.domain.DevinStatus;
import dev.ayo.devinbridge.domain.SessionState;
import dev.ayo.devinbridge.domain.StatusSnapshot;
import dev.ayo.devinbridge.domain.TrackedSession;
import dev.ayo.devinbridge.github.GitHubClient;
import dev.ayo.devinbridge.store.SessionStore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Coordinates the whole remediation flow: turns labelled issues into Devin sessions,
 * polls them to completion, and reports PR links back to GitHub. This is the only
 * class that drives {@link SessionState#advance} in response to real events, and the
 * only class that talks to both {@link DevinClient} and {@link GitHubClient}.
 *
 * <p>Two triggers feed the same entry point, {@link #onIssueEvent}: the webhook
 * (low-latency push) and {@link #scanIssues} (a 60s pull safety net for missed or
 * failed webhook deliveries). Both are idempotent via {@link SessionStore#register}.
 */
public final class Orchestrator {

    /**
     * The label that marks an issue for Devin remediation.
     */
    public static final String TARGET_LABEL = "devin-fix";
    private static final Logger log = Logger.getLogger(Orchestrator.class.getName());
    private final SessionStore store;
    private final DevinClient devin;
    private final GitHubClient github;
    private final Clock clock;
    private final Duration sessionTimeout;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "devinbridge-scheduler");
        t.setDaemon(true);
        return t;
    });

    public Orchestrator(SessionStore store, DevinClient devin, GitHubClient github,
                        Clock clock, Duration sessionTimeout) {
        this.store = store;
        this.devin = devin;
        this.github = github;
        this.clock = clock;
        this.sessionTimeout = sessionTimeout;
    }

    private static SessionState.Event toEvent(StatusSnapshot snapshot, Instant since, Instant now) {
        if (snapshot.status() == DevinStatus.EXPIRED) {
            return new SessionState.Event.SessionFailed("Devin session expired", now);
        }
        if (snapshot.status() == DevinStatus.FINISHED) {
            return new SessionState.Event.SessionFinished(snapshot.prUrl(), since, now);
        }
        if (snapshot.prUrl() != null) {
            return new SessionState.Event.PrDetected(snapshot.prUrl(), now);
        }
        return new SessionState.Event.StillRunning(now);
    }

    private static boolean isFirstPrSighting(SessionState before, SessionState after) {
        boolean hadPr = before instanceof SessionState.PrOpened;
        boolean hasPr = after instanceof SessionState.PrOpened || after instanceof SessionState.Completed;
        return hasPr && !hadPr;
    }

    private static String devinSessionIdOf(SessionState state) {
        return switch (state) {
            case SessionState.Running r -> r.devinSessionId();
            case SessionState.PrOpened p -> p.devinSessionId();
            case SessionState.Queued q -> null;
            case SessionState.Completed c -> null;
            case SessionState.Failed f -> null;
        };
    }

    private static String prUrlOf(SessionState state) {
        return switch (state) {
            case SessionState.PrOpened p -> p.prUrl();
            case SessionState.Completed c -> c.prUrl();
            default -> null;
        };
    }

    private static String buildPrompt(long issueNumber, String issueTitle, String repo) {
        return "Fix issue #" + issueNumber + " (\"" + issueTitle + "\") in the " + repo
                + " GitHub repository. Investigate the issue, implement a fix, and open "
                + "a pull request against the default branch with a clear description "
                + "of the change and how it resolves the issue.";
    }

    /**
     * Starts the poll (every 15s) and fallback issue-scan (every 60s) loops.
     */
    public void start(String repo) {
        scheduler.scheduleWithFixedDelay(this::poll, 15, 15, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(() -> scanIssues(repo), 60, 60, TimeUnit.SECONDS);
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }

    /**
     * Entry point for both triggers. If the issue carries {@link #TARGET_LABEL} and
     * isn't already tracked, registers it as {@code Queued}, kicks off a Devin
     * session, and immediately advances it to {@code Running}.
     */
    public void onIssueEvent(long issueNumber, String issueTitle, List<String> labels, String repo) {
        if (!labels.contains(TARGET_LABEL)) {
            return;
        }
        Instant now = clock.instant();
        boolean isNew = store.register(issueNumber, issueTitle, repo, now);
        if (!isNew) {
            log.fine(() -> "Issue #" + issueNumber + " already tracked, skipping");
            return;
        }

        SessionState queued = new SessionState.Queued(now);
        try {
            String prompt = buildPrompt(issueNumber, issueTitle, repo);
            String sessionId = devin.createSession(prompt, repo);
            SessionState running = SessionState.advance(
                    queued, new SessionState.Event.SessionStarted(sessionId, clock.instant()));
            store.update(issueNumber, running);
            log.info(() -> "Started Devin session " + sessionId + " for issue #" + issueNumber);
        } catch (Exception e) {
            log.log(Level.WARNING, e, () -> "Failed to start Devin session for issue #" + issueNumber);
            SessionState failed = SessionState.advance(
                    queued, new SessionState.Event.SessionFailed(e.getMessage(), clock.instant()));
            store.update(issueNumber, failed);
        }
    }

    /**
     * Polls every non-terminal tracked session and advances its state accordingly.
     */
    public void poll() {
        for (TrackedSession session : store.all()) {
            if (session.state().isTerminal()) {
                continue;
            }
            try {
                pollOne(session);
            } catch (Exception e) {
                // One session's failure must never stop the others from being polled.
                log.log(Level.WARNING, e, () -> "Poll failed for issue #" + session.issueNumber());
            }
        }
    }

    private void pollOne(TrackedSession session) {
        String devinSessionId = devinSessionIdOf(session.state());
        if (devinSessionId == null) {
            return; // Queued sessions with no Devin id yet (shouldn't normally happen)
        }

        Instant now = clock.instant();
        if (Duration.between(session.createdAt(), now).compareTo(sessionTimeout) > 0) {
            failSession(session, "timed out after " + sessionTimeout.toMinutes() + " minutes");
            return;
        }

        StatusSnapshot snapshot;
        try {
            snapshot = devin.getStatus(devinSessionId);
        } catch (Exception e) {
            failSession(session, "Devin API error: " + e.getMessage());
            return;
        }

        SessionState.Event event = toEvent(snapshot, session.createdAt(), now);
        SessionState next = SessionState.advance(session.state(), event);
        boolean prJustAppeared = isFirstPrSighting(session.state(), next);
        store.update(session.issueNumber(), next);

        if (prJustAppeared) {
            String prUrl = prUrlOf(next);
            github.postComment(session.issueNumber(),
                    "Devin opened a pull request for this issue: " + prUrl);
        }
    }

    private void failSession(TrackedSession session, String reason) {
        SessionState next = SessionState.advance(
                session.state(), new SessionState.Event.SessionFailed(reason, clock.instant()));
        store.update(session.issueNumber(), next);
        log.warning(() -> "Issue #" + session.issueNumber() + " failed: " + reason);
    }

    /**
     * Fallback pull trigger: re-scans the repo for {@link #TARGET_LABEL}-labelled
     * issues every 60s, in case a webhook delivery was missed or failed. Registration
     * is idempotent, so already-tracked issues are silently skipped.
     */
    public void scanIssues(String repo) {
        try {
            List<GitHubClient.Issue> issues = github.listLabelledIssues(TARGET_LABEL);
            for (GitHubClient.Issue issue : issues) {
                onIssueEvent(issue.number(), issue.title(), issue.labels(), repo);
            }
        } catch (Exception e) {
            log.log(Level.WARNING, e, () -> "Fallback issue scan failed");
        }
    }
}
