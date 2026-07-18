package dev.ayo.devinbridge;

import dev.ayo.devinbridge.devin.DevinClient;
import dev.ayo.devinbridge.devin.HttpDevinClient;
import dev.ayo.devinbridge.devin.MockDevinClient;
import dev.ayo.devinbridge.github.GitHubClient;
import dev.ayo.devinbridge.github.Hub4jGitHubClient;
import dev.ayo.devinbridge.github.WebhookParser;
import dev.ayo.devinbridge.github.WebhookVerifier;
import dev.ayo.devinbridge.orchestrator.Orchestrator;
import dev.ayo.devinbridge.store.SessionStore;
import dev.ayo.devinbridge.web.WebServer;

import java.time.Clock;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * Composition root. Reads env config, wires every collaborator by hand (no DI
 * framework — this is the entire object graph), and starts the server + schedulers.
 */
public final class Main {

    private static final Logger log = Logger.getLogger(Main.class.getName());

    private Main() {
    }

    public static void main(String[] args) {
        Config config = Config.fromEnv();
        log.info(() -> "Starting devinbridge for repo=" + config.targetRepo()
                + " mockDevin=" + config.mockDevin());

        SessionStore store = new SessionStore();
        Orchestrator orchestrator = createDevinSessionOrchestrator(config, store);

        WebhookParser webhookParser = new WebhookParser();
        WebhookVerifier webhookVerifier = new WebhookVerifier(config.githubWebhookSecret());

        WebServer webServer = new WebServer(
                orchestrator,
                store,
                webhookParser,
                webhookVerifier,
                config.targetRepo()
        );

        webServer.start(config.port());
        orchestrator.start(config.targetRepo());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down");
            orchestrator.shutdown();
            webServer.stop();
        }, "devinbridge-shutdown"));

        log.info(() -> "devinbridge listening on port " + config.port());
    }

    private static Orchestrator createDevinSessionOrchestrator(Config config, SessionStore store) {
        DevinClient devinClient = config.mockDevin()
                ? new MockDevinClient()
                : new HttpDevinClient(config.devinApiUrl(), config.devinApiKey());

        GitHubClient githubClient = new Hub4jGitHubClient(config.targetRepo(), config.githubToken());

        return new Orchestrator(
                store,
                devinClient,
                githubClient,
                Clock.systemUTC(),
                Duration.ofMinutes(config.devinTimeoutMinutes())
        );
    }

    /**
     * Environment-derived configuration, resolved once at startup.
     */
    record Config(
            String devinApiUrl,
            String devinApiKey,
            boolean mockDevin,
            String githubToken,
            String targetRepo,
            String githubWebhookSecret,
            long devinTimeoutMinutes,
            int port
    ) {
        static Config fromEnv() {
            boolean mock = Boolean.parseBoolean(env("MOCK_DEVIN", "false"));
            String targetRepo = mock
                    ? env("TARGET_REPO", "mock-org/mock-repo")
                    : requireEnv("TARGET_REPO");

            return new Config(
                    env("DEVIN_API_URL", "https://api.devin.ai"),
                    env("DEVIN_API_KEY", ""),
                    mock,
                    env("GITHUB_TOKEN", ""),
                    targetRepo,
                    env("GITHUB_WEBHOOK_SECRET", null),
                    Long.parseLong(env("DEVIN_TIMEOUT_MINUTES", "30")),
                    Integer.parseInt(env("PORT", "8080"))
            );
        }

        private static String env(String key, String fallback) {
            String value = System.getenv(key);
            return (value == null || value.isBlank()) ? fallback : value;
        }

        private static String requireEnv(String key) {
            String value = System.getenv(key);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Required env var " + key + " is not set");
            }
            return value;
        }
    }
}
