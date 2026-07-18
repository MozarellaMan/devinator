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

/**
 * HTTP surface: the GitHub webhook receiver, the dashboard's status feed, a health
 * check, and the static dashboard itself (resources/public).
 */
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
        app = Javalin.create(config -> config.staticFiles.add("/public"))
                .post("/webhook/github", this::handleWebhook)
                .get("/status", this::handleStatus)
                .get("/health", ctx -> ctx.result("ok"))
                .start(port);
    }

    public void stop() {
        if (app != null) {
            app.stop();
        }
    }

    private void handleWebhook(Context ctx) {
        String body = ctx.body();
        String signature = ctx.header("X-Hub-Signature-256");
        if (!webhookVerifier.verify(body, signature)) {
            log.warning("Rejected webhook: signature verification failed");
            ctx.status(401).result("invalid signature");
            return;
        }

        WebhookParser.WebhookEvent event;
        try {
            event = webhookParser.parse(body);
        } catch (IllegalArgumentException e) {
            ctx.status(400).result("malformed payload");
            return;
        }

        if (event.isRelevant(Orchestrator.TARGET_LABEL)) {
            orchestrator.onIssueEvent(event.issueNumber(), event.issueTitle(), event.labels(), repo);
        } else {
            log.fine(() -> "Ignoring webhook action=" + event.action() + " for issue #" + event.issueNumber());
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
