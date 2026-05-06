package com.cloudteachingai.user.service;

import com.cloudteachingai.user.config.SystemHealthProperties;
import com.cloudteachingai.user.dto.ServiceHealthResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class SystemHealthService {

    private final SystemHealthProperties properties;
    private final HttpClient systemHealthHttpClient;
    private final ObjectMapper objectMapper;

    public List<ServiceHealthResponse> listServiceHealth() {
        return properties.getTargets().stream()
                .map(target -> CompletableFuture.supplyAsync(() -> checkTarget(target)))
                .toList()
                .stream()
                .map(CompletableFuture::join)
                .toList();
    }

    private ServiceHealthResponse checkTarget(SystemHealthProperties.Target target) {
        long startedAt = System.nanoTime();
        String checkedAt = OffsetDateTime.now(ZoneOffset.UTC).toString();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(target.getUrl()))
                    .timeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                    .GET()
                    .build();

            HttpResponse<String> response = systemHealthHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long responseTimeMs = elapsedMillis(startedAt);
            String rawStatus = readStatus(response.body());
            boolean healthy = response.statusCode() >= 200
                    && response.statusCode() < 300
                    && ("UP".equals(rawStatus) || "OK".equals(rawStatus));

            return ServiceHealthResponse.builder()
                    .key(target.getKey())
                    .name(target.getName())
                    .endpoint(target.getUrl())
                    .status(healthy ? "UP" : "DOWN")
                    .httpStatus(response.statusCode())
                    .responseTimeMs(responseTimeMs)
                    .checkedAt(checkedAt)
                    .message(healthy ? null : "健康响应异常：" + (StringUtils.hasText(rawStatus) ? rawStatus : "UNKNOWN"))
                    .build();
        } catch (Exception ex) {
            return ServiceHealthResponse.builder()
                    .key(target.getKey())
                    .name(target.getName())
                    .endpoint(target.getUrl())
                    .status("DOWN")
                    .responseTimeMs(elapsedMillis(startedAt))
                    .checkedAt(checkedAt)
                    .message(ex.getMessage() == null ? "服务不可达或请求超时" : ex.getMessage())
                    .build();
        }
    }

    private String readStatus(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            return root.path("status").asText("").toUpperCase(Locale.ROOT);
        } catch (Exception ignored) {
            return "";
        }
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0L, Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
    }
}
