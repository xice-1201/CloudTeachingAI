package com.cloudteachingai.course.client;

import com.cloudteachingai.course.event.ResourceTaggedEvent;
import com.cloudteachingai.course.event.ResourceUploadedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceTagAgentClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Value("${ai.tag-agent.enabled:true}")
    private boolean enabled;

    @Value("${ai.tag-agent.base-url:http://localhost:8101}")
    private String baseUrl;

    @Value("${ai.tag-agent.timeout-seconds:30}")
    private int timeoutSeconds;

    public Optional<ResourceTaggedEvent> requestTagging(ResourceUploadedEvent event) {
        if (!enabled) {
            return Optional.empty();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(baseUrl) + "/api/v1/internal/resource-tagging/jobs"))
                    .timeout(Duration.ofSeconds(Math.max(3, timeoutSeconds)))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(event), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("tag-agent request failed: status={}, resourceId={}, body={}",
                        response.statusCode(),
                        event.resourceId(),
                        truncate(response.body(), 500));
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.treeToValue(data, ResourceTaggedEvent.class));
        } catch (Exception ex) {
            log.warn("tag-agent request failed: resourceId={}", event.resourceId(), ex);
            return Optional.empty();
        }
    }

    private String normalizeBaseUrl(String rawBaseUrl) {
        if (!StringUtils.hasText(rawBaseUrl)) {
            return "http://localhost:8101";
        }
        return rawBaseUrl.endsWith("/") ? rawBaseUrl.substring(0, rawBaseUrl.length() - 1) : rawBaseUrl;
    }

    private String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }
}

