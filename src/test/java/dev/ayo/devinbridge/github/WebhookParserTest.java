package dev.ayo.devinbridge.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

class WebhookParserTest {

    private final WebhookParser parser = new WebhookParser();

    private String fixture() throws IOException {
        try (var in = getClass().getResourceAsStream("/issues-labeled-webhook.json")) {
            return new String(in != null ? in.readAllBytes() : null, StandardCharsets.UTF_8);
        }
    }

    @Test
    void extractsActionIssueAndLabelsFromRealPayload() throws IOException {
        WebhookParser.WebhookEvent event = parser.parse(fixture());

        assertEquals("labeled", event.action());
        assertEquals(42, event.issueNumber());
        assertEquals("Null pointer exception on empty cart checkout", event.issueTitle());
        assertEquals("acme/widgets", event.repoFullName());
        assertTrue(event.labels().contains("bug"));
        assertTrue(event.labels().contains("devin-fix"));
    }

    @Test
    void relevantWhenLabeledActionCarriesTargetLabel() throws IOException {
        WebhookParser.WebhookEvent event = parser.parse(fixture());
        assertTrue(event.isRelevant("devin-fix"));
        assertFalse(event.isRelevant("nonexistent-label"));
    }

    @Test
    void notRelevantForIgnoredActionsEvenWithLabel() {
        var closed = new WebhookParser.WebhookEvent(
                "closed",
                7, "Some issue",
                List.of("devin-fix"),
                "acme/widgets"
        );
        assertFalse(closed.isRelevant("devin-fix"));
    }
}
