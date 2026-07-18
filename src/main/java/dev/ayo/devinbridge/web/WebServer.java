package dev.ayo.devinbridge.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.ayo.devinbridge.github.WebhookParser;
import dev.ayo.devinbridge.github.WebhookVerifier;
import dev.ayo.devinbridge.orchestrator.Orchestrator;
import dev.ayo.devinbridge.store.SessionStore;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class WebServer {

    private static final Logger log = Logger.getLogger(WebServer.class.getName());

    private final Orchestrator orchestrator;
    private final SessionStore store;
    private final WebhookParser webhookParser;
    private final WebhookVerifier webhookVerifier;
    private final String repo;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private Javalin app;

    public WebServer(
            Orchestrator orchestrator,
            SessionStore store,
            WebhookParser webhookParser,
            WebhookVerifier webhookVerifier,
            String repo
    ) {
        this.orchestrator = orchestrator;
        this.store = store;
        this.webhookParser = webhookParser;
        this.webhookVerifier = webhookVerifier;
        this.repo = repo;
    }

    public void start(int port) {
        app = Javalin.create(config -> {
            config.staticFiles.add("/public");
            config.routes.post("/webhook/github", this::handleWebhook);
            config.routes.get("/status", this::handleStatus);
            config.routes.get("/health", ctx -> ctx.result("ok"));
        }).start(port);
    }

    public void stop() {
        if (app != null) {
            app.stop();
        }
    }

    private void handleWebhook(Context ctx) {
        String body = ctx.body();
        String signature = ctx.header("X-Hub-Signature-256");
        String githubEvent = ctx.header("X-GitHub-Event");

        if (!webhookVerifier.verify(body, signature)) {
            log.warning(() -> "Rejected webhook (X-GitHub-Event=" + githubEvent + "): signature verification failed");
            ctx.status(401).result("invalid signature");
            return;
        }

        // Only "issues" events parse as GHEventPayload.Issue. Anything else (push, ping, etc.) we'll ignore
        if (!"issues".equals(githubEvent)) {
            log.info(() -> "Ignoring webhook: X-GitHub-Event=" + githubEvent + " (only \"issues\" events are handled)");
            ctx.status(202).result("ignored");
            return;
        }

        WebhookParser.WebhookEvent event;
        try {
            event = webhookParser.parse(body);
        } catch (IllegalArgumentException e) {
            log.log(Level.WARNING, e,
                    () -> "Rejected webhook (X-GitHub-Event=" + githubEvent + "): failed to parse as an issues payload");
            ctx.status(400).result("malformed payload");
            return;
        }

        if (event.isRelevant(Orchestrator.TARGET_LABEL)) {
            log.info(() -> "Accepted webhook: action=" + event.action() + " issue=#" + event.issueNumber());
            orchestrator.onIssueEvent(event.issueNumber(), event.issueTitle(), event.labels(), repo);
        } else {
            log.info(() -> "Ignoring webhook: action=" + event.action() + " issue=#" + event.issueNumber()
                    + " (not opened/labeled with \"" + Orchestrator.TARGET_LABEL + "\")");
        }
        ctx.status(202).result("accepted");
    }

    private void handleStatus(Context ctx) {
        try {
            ctx.contentType("application/json").result(mapper.writeValueAsString(StatusView.of(store)));
        } catch (Exception e) {
            log.log(Level.WARNING, e, () -> "Failed to serialize /status response");
            ctx.status(500).result("internal error");
        }
    }
}
