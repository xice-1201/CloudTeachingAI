package com.cloudteachingai.user.service;

import com.cloudteachingai.user.config.SystemHealthProperties;
import com.cloudteachingai.user.dto.ServiceHealthResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SystemHealthServiceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void listServiceHealthMarksUpAndDownTargets() throws Exception {
        startServer();
        SystemHealthProperties properties = new SystemHealthProperties();
        properties.setTimeoutSeconds(3);
        properties.setTargets(List.of(
                target("auth-service", "认证服务", "/up"),
                target("tag-agent", "AI 标注服务", "/down")
        ));
        SystemHealthService service = new SystemHealthService(
                properties,
                HttpClient.newHttpClient(),
                new ObjectMapper()
        );

        List<ServiceHealthResponse> results = service.listServiceHealth();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getKey()).isEqualTo("auth-service");
        assertThat(results.get(0).getStatus()).isEqualTo("UP");
        assertThat(results.get(0).getHttpStatus()).isEqualTo(200);
        assertThat(results.get(0).getResponseTimeMs()).isNotNegative();
        assertThat(results.get(0).getCheckedAt()).isNotBlank();
        assertThat(results.get(0).getMessage()).isNull();

        assertThat(results.get(1).getKey()).isEqualTo("tag-agent");
        assertThat(results.get(1).getStatus()).isEqualTo("DOWN");
        assertThat(results.get(1).getHttpStatus()).isEqualTo(503);
        assertThat(results.get(1).getMessage()).contains("DOWN");
    }

    private void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/up", exchange -> respond(exchange, 200, "{\"status\":\"UP\"}"));
        server.createContext("/down", exchange -> respond(exchange, 503, "{\"status\":\"DOWN\"}"));
        server.start();
    }

    private SystemHealthProperties.Target target(String key, String name, String path) {
        SystemHealthProperties.Target target = new SystemHealthProperties.Target();
        target.setKey(key);
        target.setName(name);
        target.setUrl("http://localhost:" + server.getAddress().getPort() + path);
        return target;
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
