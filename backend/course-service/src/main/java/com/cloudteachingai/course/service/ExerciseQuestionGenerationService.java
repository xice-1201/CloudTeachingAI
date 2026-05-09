package com.cloudteachingai.course.service;

import com.cloudteachingai.course.dto.ExerciseGenerateRequest;
import com.cloudteachingai.course.dto.ExerciseGenerateResponse;
import com.cloudteachingai.course.dto.ResourceResponse;
import com.cloudteachingai.course.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExerciseQuestionGenerationService {

    private static final int MAX_EXERCISE_QUESTIONS = 20;
    private static final int MIN_OPTION_COUNT = 2;
    private static final int MAX_OPTION_COUNT = 6;
    private static final List<String> OPTION_IDS = List.of("A", "B", "C", "D", "E", "F");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Value("${ai.exercise-generation.enabled:true}")
    private boolean enabled;

    @Value("${ai.exercise-generation.api-key:}")
    private String apiKey;

    @Value("${ai.exercise-generation.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${ai.exercise-generation.model:deepseek-chat}")
    private String model;

    @Value("${ai.exercise-generation.fallback-api-key:}")
    private String fallbackApiKey;

    @Value("${ai.exercise-generation.fallback-base-url:https://api.openai.com/v1}")
    private String fallbackBaseUrl;

    @Value("${ai.exercise-generation.fallback-model:gpt-4.1-mini}")
    private String fallbackModel;

    public ExerciseGenerateResponse generate(ExerciseGenerateRequest request) {
        if (!enabled) {
            throw BusinessException.badRequest("AI exercise generation is disabled");
        }
        TagProvider provider = resolveProvider();
        if (provider == null) {
            throw BusinessException.badRequest("AI exercise generation is not configured");
        }

        int count = Math.max(1, Math.min(MAX_EXERCISE_QUESTIONS, request.getQuestionCount() == null ? 5 : request.getQuestionCount()));
        String title = Optional.ofNullable(request.getTitle()).orElse("").trim();
        String description = Optional.ofNullable(request.getDescription()).orElse("").trim();
        List<String> topics = normalizeTopics(request.getTagLabels());
        if (!StringUtils.hasText(title) && !StringUtils.hasText(description) && topics.isEmpty()) {
            throw BusinessException.badRequest("Please provide a resource title, description, or knowledge points before generating exercises");
        }

        try {
            GeneratedExerciseContent generated = requestAiQuestions(provider, title, description, topics, count);
            if (generated.questions().isEmpty()) {
                throw BusinessException.internal("AI did not return valid exercise questions");
            }
            return ExerciseGenerateResponse.builder()
                    .title(StringUtils.hasText(generated.title()) ? generated.title() : title)
                    .description(StringUtils.hasText(generated.description()) ? generated.description() : description)
                    .questions(generated.questions())
                    .build();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("AI exercise generation failed", ex);
            throw BusinessException.internal("AI exercise generation failed, please try again later", ex);
        }
    }

    private GeneratedExerciseContent requestAiQuestions(
            TagProvider provider,
            String title,
            String description,
            List<String> topics,
            int count
    ) throws Exception {
        Map<String, Object> payload = Map.of(
                "model", provider.model(),
                "temperature", 0.72,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", """
                                        You are an assessment designer for an educational platform.
                                        Generate a concise exercise resource title, a short exercise resource description, and diverse single-choice questions based on the provided resource or context.
                                        Return JSON only: {"title":"exercise resource title","description":"exercise resource description","questions":[{"stem":"...","options":[{"id":"A","text":"..."},{"id":"B","text":"..."},{"id":"C","text":"..."},{"id":"D","text":"..."}],"answer":"A","explanation":"..."}]}.
                                        Requirements: every question must test a different concept or cognitive angle; shuffle the correct answer across A/B/C/D; avoid template-like repeated stems; options must be plausible and mutually exclusive; use the same language as the resource.
                                        """
                        ),
                        Map.of(
                                "role", "user",
                                "content", objectMapper.writeValueAsString(Map.of(
                                        "resource", Map.of(
                                                "title", title,
                                                "description", description,
                                                "knowledgePoints", topics
                                        ),
                                        "questionCount", count,
                                        "questionType", "single choice",
                                        "optionCount", 4
                                ))
                        )
                )
        );

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(normalizeBaseUrl(provider.baseUrl()) + "/chat/completions"))
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + provider.apiKey())
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("AI exercise generation request failed: status={}, body={}", response.statusCode(), truncate(response.body(), 1000));
            throw BusinessException.internal("AI exercise generation provider request failed");
        }

        JsonNode contentNode = objectMapper.readTree(response.body())
                .path("choices")
                .path(0)
                .path("message")
                .path("content");
        if (contentNode.isMissingNode() || !StringUtils.hasText(contentNode.asText())) {
            return new GeneratedExerciseContent(null, null, List.of());
        }
        return parseGeneratedContent(contentNode.asText(), count);
    }

    private GeneratedExerciseContent parseGeneratedContent(String content, int count) throws Exception {
        JsonNode root = objectMapper.readTree(content);
        String title = firstText(root, "title", "resourceTitle", "exerciseTitle");
        String description = firstText(root, "description", "resourceDescription", "exerciseDescription");
        return new GeneratedExerciseContent(title, description, parseQuestions(root, count));
    }

    private List<ResourceResponse.ExerciseQuestionResponse> parseQuestions(String content, int count) throws Exception {
        return parseQuestions(objectMapper.readTree(content), count);
    }

    private List<ResourceResponse.ExerciseQuestionResponse> parseQuestions(JsonNode root, int count) {
        JsonNode questionsNode = root.path("questions");
        if (!questionsNode.isArray()) {
            return List.of();
        }

        List<ResourceResponse.ExerciseQuestionResponse> questions = new ArrayList<>();
        LinkedHashSet<String> stems = new LinkedHashSet<>();
        for (JsonNode questionNode : questionsNode) {
            if (questions.size() >= count) {
                break;
            }
            ResourceResponse.ExerciseQuestionResponse question = parseQuestion(questionNode);
            if (question == null || !stems.add(normalizeQuestionText(question.getStem()))) {
                continue;
            }
            questions.add(question);
        }
        return questions;
    }

    private String firstText(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = root.path(fieldName).asText("").trim();
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private ResourceResponse.ExerciseQuestionResponse parseQuestion(JsonNode questionNode) {
        String stem = questionNode.path("stem").asText("").trim();
        String answer = questionNode.path("answer").asText("").trim().toUpperCase();
        String explanation = questionNode.path("explanation").asText("").trim();
        JsonNode optionsNode = questionNode.path("options");
        if (!StringUtils.hasText(stem) || !optionsNode.isArray()) {
            return null;
        }

        List<ResourceResponse.ExerciseOptionResponse> options = new ArrayList<>();
        LinkedHashSet<String> optionTexts = new LinkedHashSet<>();
        int index = 0;
        for (JsonNode optionNode : optionsNode) {
            if (options.size() >= MAX_OPTION_COUNT || index >= OPTION_IDS.size()) {
                break;
            }
            String text = optionNode.path("text").asText("").trim();
            if (!StringUtils.hasText(text) || !optionTexts.add(normalizeQuestionText(text))) {
                continue;
            }
            String optionId = OPTION_IDS.get(index++);
            options.add(ResourceResponse.ExerciseOptionResponse.builder()
                    .id(optionId)
                    .text(text)
                    .build());
        }

        if (options.size() < MIN_OPTION_COUNT || !OPTION_IDS.subList(0, options.size()).contains(answer)) {
            return null;
        }
        return ResourceResponse.ExerciseQuestionResponse.builder()
                .id(UUID.randomUUID().toString())
                .stem(stem)
                .options(options)
                .answer(answer)
                .explanation(StringUtils.hasText(explanation) ? explanation : null)
                .build();
    }

    private TagProvider resolveProvider() {
        if (StringUtils.hasText(apiKey)) {
            return new TagProvider(apiKey, baseUrl, model);
        }
        if (StringUtils.hasText(fallbackApiKey)) {
            return new TagProvider(fallbackApiKey, fallbackBaseUrl, fallbackModel);
        }
        return null;
    }

    private List<String> normalizeTopics(List<String> tagLabels) {
        if (tagLabels == null || tagLabels.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String tagLabel : tagLabels) {
            if (StringUtils.hasText(tagLabel)) {
                result.add(tagLabel.trim());
            }
        }
        return new ArrayList<>(result);
    }

    private String normalizeQuestionText(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private String normalizeBaseUrl(String rawBaseUrl) {
        if (!StringUtils.hasText(rawBaseUrl)) {
            return "https://api.deepseek.com";
        }
        return rawBaseUrl.endsWith("/") ? rawBaseUrl.substring(0, rawBaseUrl.length() - 1) : rawBaseUrl;
    }

    private String truncate(String value, int limit) {
        if (!StringUtils.hasText(value) || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }

    private record TagProvider(String apiKey, String baseUrl, String model) {
    }

    private record GeneratedExerciseContent(
            String title,
            String description,
            List<ResourceResponse.ExerciseQuestionResponse> questions
    ) {
    }
}
