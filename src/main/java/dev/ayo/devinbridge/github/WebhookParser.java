package dev.ayo.devinbridge.github;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GitHub;

/**
 * Extracts the fields this we care about from a GitHub {@code issues} webhook payload
 */
public final class WebhookParser {

    private final GitHub offline = GitHub.offline();

    public WebhookEvent parse(String payload) {
        try {
            GHEventPayload.Issue event = offline.parseEventPayload(
                    new StringReader(payload),
                    GHEventPayload.Issue.class
            );
            GHIssue issue = event.getIssue();
            List<String> labels = issue.getLabels().stream().map(GHLabel::getName).toList();
            String repo = event.getRepository() != null ? event.getRepository().getFullName() : "";

            return new WebhookEvent(event.getAction(), issue.getNumber(), issue.getTitle(), labels, repo);
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Malformed GitHub issues webhook payload", e);
        }
    }

    public record WebhookEvent(
            String action,
            long issueNumber,
            String issueTitle,
            List<String> labels,
            String repoFullName
    ) {

        /**
         * We only act on new or newly-labelled issues that carry the target label.
         */
        public boolean isRelevant(String targetLabel) {
            return ("opened".equals(action) || "labeled".equals(action)) && labels.contains(targetLabel);
        }
    }
}
