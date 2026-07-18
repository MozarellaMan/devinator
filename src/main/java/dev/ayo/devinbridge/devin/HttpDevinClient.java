package dev.ayo.devinbridge.devin;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import dev.ayo.devinbridge.domain.DevinStatus;
import dev.ayo.devinbridge.domain.StatusSnapshot;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Real Devin client
 */
public final class HttpDevinClient implements DevinClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final String baseUrl;
    private final String apiKey;
    private final String orgId;

    public HttpDevinClient(String baseUrl, String apiKey, String orgId) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.orgId = orgId;
    }

    @Override
    public String createSession(String prompt, String repo) {
        try {
            var body = new DevinApiDto.CreateSessionRequest(
                    prompt, "devinbridge: " + repo, List.of());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v3/organizations/" + orgId + "/sessions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new DevinApiException(
                        "createSession failed: HTTP " + response.statusCode() + " " + response.body()
                );
            }
            var parsed = mapper.readValue(response.body(), DevinApiDto.CreateSessionResponse.class);
            return parsed.sessionId();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new DevinApiException("createSession request failed", e);
        }
    }

    @Override
    public StatusSnapshot getStatus(String sessionId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v3/organizations/" + orgId + "/sessions/" + sessionId))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new DevinApiException(
                        "getStatus failed: HTTP " + response.statusCode() + " " + response.body());
            }
            var parsed = mapper.readValue(response.body(), DevinApiDto.GetSessionResponse.class);
            String prUrl = parsed.pullRequests() == null || parsed.pullRequests().isEmpty()
                    ? null
                    : parsed.pullRequests().getFirst().prUrl();
            return new StatusSnapshot(sessionId, DevinStatus.fromRaw(parsed.status(), parsed.statusDetail()), prUrl);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new DevinApiException("getStatus request failed", e);
        }
    }
}
