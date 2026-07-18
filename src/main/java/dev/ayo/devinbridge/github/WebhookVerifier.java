package dev.ayo.devinbridge.github;

import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifies GitHub's {@code X-Hub-Signature-256} HMAC-SHA256 signature
 * (<a href="https://docs.github.com/en/webhooks/using-webhooks/validating-webhook-deliveries">...</a>).
 */
public final class WebhookVerifier {

    @Nullable
    private final String secret;

    /**
     * Passing a null secret means verification is disabled
     */
    public WebhookVerifier(String secret) {
        this.secret = (secret == null || secret.isBlank()) ? null : secret;
    }

    public boolean isEnabled() {
        return secret != null;
    }

    /**
     * Returns true if verification is disabled, or if {@code signatureHeader} is a
     * valid {@code sha256=<hex>} HMAC of {@code body} under the configured secret.
     */
    public boolean verify(String body, String signatureHeader) {
        if (!isEnabled()) {
            return true;
        }
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            String computedHex = "sha256=" + HexFormat.of().formatHex(computed);
            return MessageDigest.isEqual(
                    computedHex.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }
}
