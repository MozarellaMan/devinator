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

public final class HttpDevinClient implements DevinClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final String baseUrl;
    private final String apiKey;

    public HttpDevinClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
    }

    @Override
    public String createSession(String prompt, String repo) {
        try {
            var body = new DevinApiDto.CreateSessionRequest(
                    prompt, "devinbridge: " + repo, true, List.of());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/sessions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 202) {
                throw new DevinApiException(
                        "createSession failed: HTTP " + response.statusCode() + " " + response.body());
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
                    .uri(URI.create(baseUrl + "/v1/sessions/" + sessionId))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new DevinApiException(
                        "getStatus failed: HTTP " + response.statusCode() + " " + response.body());
            }
            var parsed = mapper.readValue(response.body(), DevinApiDto.GetSessionResponse.class);
            String prUrl = parsed.pullRequest() != null ? parsed.pullRequest().url() : null;
            return new StatusSnapshot(sessionId, DevinStatus.fromRaw(parsed.status()), prUrl);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new DevinApiException("getStatus request failed", e);
        }
    }
}
