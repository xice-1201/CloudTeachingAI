package com.cloudteachingai.course.client;

import com.cloudteachingai.course.event.ResourceTaggedEvent;
import com.cloudteachingai.course.event.ResourceUploadedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceTagAgentClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void requestTaggingReturnsTaggedEventWhenAgentSucceeds() throws Exception {
        startServer(200, """
                {
                  "code": 0,
                  "message": "success",
                  "data": {
                    "resourceId": 1001,
                    "chapterId": 201,
                    "courseId": 301,
                    "teacherId": 401,
                    "title": "函数极限讲义",
                    "storageKey": "resources/1001.pdf",
                    "taggingStatus": "SUGGESTED",
                    "taggingUpdatedAt": "2026-05-06T00:00:00+00:00",
                    "knowledgePoints": [
                      {
                        "knowledgePointId": 7,
                        "confidence": 0.91,
                        "reason": "命中章节关键概念"
                      }
                    ]
                  }
                }
                """);
        ResourceTagAgentClient client = newClient();

        Optional<ResourceTaggedEvent> result = client.requestTagging(uploadedEvent());

        assertThat(result).isPresent();
        assertThat(result.get().resourceId()).isEqualTo(1001L);
        assertThat(result.get().taggingStatus()).isEqualTo("SUGGESTED");
        assertThat(result.get().knowledgePoints()).hasSize(1);
        assertThat(result.get().knowledgePoints().getFirst().knowledgePointId()).isEqualTo(7L);
    }

    @Test
    void requestTaggingReturnsEmptyWhenAgentFails() throws Exception {
        startServer(500, "{\"message\":\"error\"}");
        ResourceTagAgentClient client = newClient();

        Optional<ResourceTaggedEvent> result = client.requestTagging(uploadedEvent());

        assertThat(result).isEmpty();
    }

    @Test
    void requestTaggingReturnsEmptyWhenDisabled() {
        ResourceTagAgentClient client = new ResourceTagAgentClient(new ObjectMapper());
        ReflectionTestUtils.setField(client, "enabled", false);

        Optional<ResourceTaggedEvent> result = client.requestTagging(uploadedEvent());

        assertThat(result).isEmpty();
    }

    private ResourceTagAgentClient newClient() {
        ResourceTagAgentClient client = new ResourceTagAgentClient(new ObjectMapper());
        ReflectionTestUtils.setField(client, "enabled", true);
        ReflectionTestUtils.setField(client, "baseUrl", "http://localhost:" + server.getAddress().getPort() + "/");
        ReflectionTestUtils.setField(client, "timeoutSeconds", 3);
        return client;
    }

    private void startServer(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/internal/resource-tagging/jobs", exchange -> respond(exchange, status, body));
        server.start();
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private ResourceUploadedEvent uploadedEvent() {
        return ResourceUploadedEvent.builder()
                .resourceId(1001L)
                .chapterId(201L)
                .courseId(301L)
                .teacherId(401L)
                .title("函数极限讲义")
                .description("包含函数极限的定义和例题")
                .type("PDF")
                .storageKey("resources/1001.pdf")
                .build();
    }
}
