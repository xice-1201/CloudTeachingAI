package com.cloudteachingai.course.service;

import com.cloudteachingai.course.dto.ResourceTagPreviewRequest;
import com.cloudteachingai.course.dto.ResourceTagSuggestionResponse;
import com.cloudteachingai.course.entity.KnowledgePointEntity;
import com.cloudteachingai.course.entity.ResourceEntity;
import com.cloudteachingai.course.entity.enums.KnowledgePointType;
import com.cloudteachingai.course.repository.KnowledgePointRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class ResourceTagSuggestionService {

    private static final int MAX_TAG_SUGGESTIONS = 8;
    private static final int MAX_LLM_CANDIDATES = 120;

    private final KnowledgePointRepository knowledgePointRepository;
    private final ResourceStorageService resourceStorageService;
    private final ObjectMapper objectMapper;
    private final Tika tika = new Tika();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Value("${ai.tag-preview.enabled:true}")
    private boolean previewEnabled;

    @Value("${ai.tag-preview.provider-enabled:true}")
    private boolean providerEnabled;

    @Value("${ai.tag-preview.api-key:}")
    private String apiKey;

    @Value("${ai.tag-preview.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${ai.tag-preview.model:deepseek-chat}")
    private String model;

    @Value("${ai.tag-preview.max-content-chars:12000}")
    private int maxContentChars;

    @Value("${ai.tag-preview.max-download-bytes:8388608}")
    private int maxDownloadBytes;

    public ResourceTagSuggestionService(
            KnowledgePointRepository knowledgePointRepository,
            ResourceStorageService resourceStorageService,
            ObjectMapper objectMapper
    ) {
        this.knowledgePointRepository = knowledgePointRepository;
        this.resourceStorageService = resourceStorageService;
        this.objectMapper = objectMapper;
    }

    public List<ResourceTagSuggestionResponse> suggestForPreview(ResourceTagPreviewRequest request, MultipartFile file) {
        if (!previewEnabled) {
            return buildRuleSuggestions(request, file == null ? null : tryExtractFromMultipart(file));
        }

        ExtractedContent extractedContent = file == null ? tryExtractFromSourceUrl(request.getSourceUrl(), request.getFileName()) : tryExtractFromMultipart(file);
        return suggest(request, extractedContent);
    }

    public List<ResourceTagSuggestionResponse> suggestForResource(ResourceEntity resource) {
        ResourceTagPreviewRequest request = new ResourceTagPreviewRequest();
        request.setTitle(resource.getTitle());
        request.setDescription(resource.getDescription());
        request.setType(resource.getType() == null ? null : resource.getType().name());
        request.setFileName(extractFileName(resource.getStorageKey()));
        if (!resourceStorageService.isManagedStorageKey(resource.getStorageKey())) {
            request.setSourceUrl(resource.getStorageKey());
        }

        ExtractedContent extractedContent = tryExtractFromStoredResource(resource);
        return suggest(request, extractedContent);
    }

    private List<ResourceTagSuggestionResponse> suggest(ResourceTagPreviewRequest request, ExtractedContent extractedContent) {
        List<KnowledgePointEntity> activeLeafKnowledgePoints = knowledgePointRepository.findByActiveTrueOrderByOrderIndexAscIdAsc().stream()
                .filter(item -> item.getNodeType() == KnowledgePointType.POINT)
                .toList();
        if (activeLeafKnowledgePoints.isEmpty()) {
            return List.of();
        }

        Map<Long, KnowledgePointEntity> knowledgePointMap = loadKnowledgePointMap();
        List<ResourceTagSuggestionResponse> ruleSuggestions = buildRuleSuggestions(request, extractedContent, activeLeafKnowledgePoints, knowledgePointMap);

        List<ResourceTagSuggestionResponse> aiSuggestions = buildAiSuggestions(
                request,
                extractedContent,
                activeLeafKnowledgePoints,
                knowledgePointMap,
                ruleSuggestions
        );
        if (!aiSuggestions.isEmpty()) {
            return aiSuggestions;
        }
        return ruleSuggestions;
    }

    private List<ResourceTagSuggestionResponse> buildAiSuggestions(
            ResourceTagPreviewRequest request,
            ExtractedContent extractedContent,
            List<KnowledgePointEntity> activeLeafKnowledgePoints,
            Map<Long, KnowledgePointEntity> knowledgePointMap,
            List<ResourceTagSuggestionResponse> ruleSuggestions
    ) {
        if (!providerEnabled || !StringUtils.hasText(apiKey)) {
            return List.of();
        }

        String analysisText = buildAnalysisText(request, extractedContent);
        if (!StringUtils.hasText(analysisText)) {
            return List.of();
        }

        List<KnowledgePointEntity> llmCandidates = selectLlmCandidates(
                activeLeafKnowledgePoints,
                knowledgePointMap,
                request,
                extractedContent,
                ruleSuggestions
        );
        if (llmCandidates.isEmpty()) {
            return List.of();
        }

        try {
            Map<String, Object> payload = Map.of(
                    "model", model,
                    "temperature", 0.2,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of(
                                    "role", "system",
                                    "content", "你是课程资源知识点标注助手。请根据资源内容，从候选知识点中选择最匹配的叶子知识点。只返回 JSON 对象，格式为 {\"matches\":[{\"knowledgePointId\":1,\"confidence\":0.91,\"reason\":\"一句中文理由\"}]}。不要输出候选列表之外的知识点。"
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", objectMapper.writeValueAsString(Map.of(
                                            "resource", Map.of(
                                                    "title", Optional.ofNullable(request.getTitle()).orElse(""),
                                                    "description", Optional.ofNullable(request.getDescription()).orElse(""),
                                                    "type", Optional.ofNullable(request.getType()).orElse(""),
                                                    "fileName", Optional.ofNullable(request.getFileName()).orElse(""),
                                                    "sourceUrl", Optional.ofNullable(request.getSourceUrl()).orElse(""),
                                                    "extractedContent", truncate(analysisText, maxContentChars)
                                            ),
                                            "candidateKnowledgePoints", llmCandidates.stream().map(item -> Map.of(
                                                    "knowledgePointId", item.getId(),
                                                    "name", item.getName(),
                                                    "path", buildKnowledgePointPath(item.getId(), knowledgePointMap),
                                                    "keywords", Optional.ofNullable(item.getKeywords()).orElse("")
                                            )).toList()
                                    ))
                            )
                    )
            );

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(baseUrl) + "/chat/completions"))
                    .timeout(Duration.ofSeconds(45))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("AI tag preview request failed: status={}, body={}", response.statusCode(), truncate(response.body(), 1000));
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || !StringUtils.hasText(contentNode.asText())) {
                return List.of();
            }

            JsonNode matches = objectMapper.readTree(contentNode.asText()).path("matches");
            if (!matches.isArray()) {
                return List.of();
            }

            List<ResourceTagSuggestionResponse> suggestions = new ArrayList<>();
            Map<Long, KnowledgePointEntity> candidateMap = llmCandidates.stream()
                    .collect(LinkedHashMap::new, (map, item) -> map.put(item.getId(), item), Map::putAll);
            for (JsonNode match : matches) {
                long knowledgePointId = match.path("knowledgePointId").asLong(-1);
                KnowledgePointEntity knowledgePoint = candidateMap.get(knowledgePointId);
                if (knowledgePoint == null) {
                    continue;
                }
                double confidence = Math.max(0.0D, Math.min(0.99D, match.path("confidence").asDouble(0.0D)));
                if (confidence <= 0.0D) {
                    confidence = 0.72D;
                }
                suggestions.add(ResourceTagSuggestionResponse.builder()
                        .knowledgePointId(knowledgePoint.getId())
                        .knowledgePointName(knowledgePoint.getName())
                        .path(buildKnowledgePointPath(knowledgePoint.getId(), knowledgePointMap))
                        .confidence(Math.round(confidence * 100.0D) / 100.0D)
                        .reason(StringUtils.hasText(match.path("reason").asText()) ? match.path("reason").asText() : "AI 内容分析建议")
                        .build());
            }
            return suggestions.stream()
                    .sorted(Comparator
                            .comparing(ResourceTagSuggestionResponse::getConfidence, Comparator.reverseOrder())
                            .thenComparing(ResourceTagSuggestionResponse::getPath))
                    .limit(MAX_TAG_SUGGESTIONS)
                    .toList();
        } catch (Exception ex) {
            log.warn("AI tag preview failed, fallback to rules", ex);
            return List.of();
        }
    }

    private List<KnowledgePointEntity> selectLlmCandidates(
            List<KnowledgePointEntity> activeLeafKnowledgePoints,
            Map<Long, KnowledgePointEntity> knowledgePointMap,
            ResourceTagPreviewRequest request,
            ExtractedContent extractedContent,
            List<ResourceTagSuggestionResponse> ruleSuggestions
    ) {
        Map<Long, KnowledgePointEntity> byId = activeLeafKnowledgePoints.stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getId(), item), Map::putAll);
        List<KnowledgePointEntity> seeded = new ArrayList<>();
        for (ResourceTagSuggestionResponse suggestion : ruleSuggestions) {
            KnowledgePointEntity knowledgePoint = byId.get(suggestion.getKnowledgePointId());
            if (knowledgePoint != null) {
                seeded.add(knowledgePoint);
            }
        }

        if (seeded.size() >= MAX_LLM_CANDIDATES) {
            return seeded.subList(0, MAX_LLM_CANDIDATES);
        }

        String normalizedTitle = normalizeSuggestionText(request.getTitle());
        String normalizedBody = normalizeSuggestionText(buildAnalysisText(request, extractedContent));
        List<KnowledgePointEntity> ranked = activeLeafKnowledgePoints.stream()
                .sorted(Comparator
                        .comparing((KnowledgePointEntity item) -> candidateScore(item, knowledgePointMap, normalizedTitle, normalizedBody))
                        .reversed()
                        .thenComparing(KnowledgePointEntity::getOrderIndex)
                        .thenComparing(KnowledgePointEntity::getId))
                .toList();

        LinkedHashMap<Long, KnowledgePointEntity> merged = new LinkedHashMap<>();
        for (KnowledgePointEntity knowledgePoint : seeded) {
            merged.putIfAbsent(knowledgePoint.getId(), knowledgePoint);
        }
        for (KnowledgePointEntity knowledgePoint : ranked) {
            merged.putIfAbsent(knowledgePoint.getId(), knowledgePoint);
            if (merged.size() >= MAX_LLM_CANDIDATES) {
                break;
            }
        }
        return new ArrayList<>(merged.values());
    }

    private double candidateScore(
            KnowledgePointEntity knowledgePoint,
            Map<Long, KnowledgePointEntity> knowledgePointMap,
            String normalizedTitle,
            String normalizedBody
    ) {
        double score = 0.0D;
        String pointName = normalizeSuggestionText(knowledgePoint.getName());
        if (StringUtils.hasText(pointName) && normalizedTitle.contains(pointName)) {
            score += 0.7D;
        } else if (StringUtils.hasText(pointName) && normalizedBody.contains(pointName)) {
            score += 0.55D;
        }
        for (String keyword : splitKeywords(knowledgePoint.getKeywords())) {
            String normalizedKeyword = normalizeSuggestionText(keyword);
            if (StringUtils.hasText(normalizedKeyword) && normalizedBody.contains(normalizedKeyword)) {
                score += 0.2D;
            }
        }
        KnowledgePointEntity domain = knowledgePointMap.get(knowledgePoint.getParentId());
        if (domain != null && normalizedBody.contains(normalizeSuggestionText(domain.getName()))) {
            score += 0.08D;
        }
        return score;
    }

    private List<ResourceTagSuggestionResponse> buildRuleSuggestions(ResourceTagPreviewRequest request, ExtractedContent extractedContent) {
        List<KnowledgePointEntity> activeLeafKnowledgePoints = knowledgePointRepository.findByActiveTrueOrderByOrderIndexAscIdAsc().stream()
                .filter(item -> item.getNodeType() == KnowledgePointType.POINT)
                .toList();
        return buildRuleSuggestions(request, extractedContent, activeLeafKnowledgePoints, loadKnowledgePointMap());
    }

    private List<ResourceTagSuggestionResponse> buildRuleSuggestions(
            ResourceTagPreviewRequest request,
            ExtractedContent extractedContent,
            List<KnowledgePointEntity> activeLeafKnowledgePoints,
            Map<Long, KnowledgePointEntity> knowledgePointMap
    ) {
        String normalizedTitle = normalizeSuggestionText(request.getTitle());
        String normalizedFullText = normalizeSuggestionText(buildAnalysisText(request, extractedContent));
        if (!StringUtils.hasText(normalizedFullText)) {
            return List.of();
        }

        List<ResourceTagSuggestionResponse> suggestions = new ArrayList<>();
        for (KnowledgePointEntity knowledgePoint : activeLeafKnowledgePoints) {
            double score = 0D;
            List<String> reasons = new ArrayList<>();
            String pointName = normalizeSuggestionText(knowledgePoint.getName());
            if (StringUtils.hasText(pointName) && normalizedTitle.contains(pointName)) {
                score += 0.75D;
                reasons.add("标题命中知识点名称");
            } else if (StringUtils.hasText(pointName) && normalizedFullText.contains(pointName)) {
                score += 0.65D;
                reasons.add("内容命中知识点名称");
            }

            for (String keyword : splitKeywords(knowledgePoint.getKeywords())) {
                String normalizedKeyword = normalizeSuggestionText(keyword);
                if (StringUtils.hasText(normalizedKeyword) && normalizedFullText.contains(normalizedKeyword)) {
                    score += 0.18D;
                    reasons.add("命中关键词「" + keyword + "」");
                }
            }

            KnowledgePointEntity domain = knowledgePointMap.get(knowledgePoint.getParentId());
            if (domain != null) {
                String normalizedDomain = normalizeSuggestionText(domain.getName());
                if (StringUtils.hasText(normalizedDomain) && normalizedFullText.contains(normalizedDomain)) {
                    score += 0.08D;
                    reasons.add("命中领域");
                }

                KnowledgePointEntity subject = domain.getParentId() == null ? null : knowledgePointMap.get(domain.getParentId());
                if (subject != null) {
                    String normalizedSubject = normalizeSuggestionText(subject.getName());
                    if (StringUtils.hasText(normalizedSubject) && normalizedFullText.contains(normalizedSubject)) {
                        score += 0.04D;
                        reasons.add("命中学科");
                    }
                }
            }

            if (score < 0.18D) {
                continue;
            }

            suggestions.add(ResourceTagSuggestionResponse.builder()
                    .knowledgePointId(knowledgePoint.getId())
                    .knowledgePointName(knowledgePoint.getName())
                    .path(buildKnowledgePointPath(knowledgePoint.getId(), knowledgePointMap))
                    .confidence(Math.round(Math.min(0.99D, score) * 100.0D) / 100.0D)
                    .reason(String.join("; ", reasons.stream().distinct().toList()))
                    .build());
        }

        return suggestions.stream()
                .sorted(Comparator
                        .comparing(ResourceTagSuggestionResponse::getConfidence, Comparator.reverseOrder())
                        .thenComparing(ResourceTagSuggestionResponse::getPath))
                .limit(MAX_TAG_SUGGESTIONS)
                .toList();
    }

    private String buildAnalysisText(ResourceTagPreviewRequest request, ExtractedContent extractedContent) {
        List<String> parts = new ArrayList<>();
        addIfText(parts, request.getTitle());
        addIfText(parts, request.getDescription());
        addIfText(parts, request.getFileName());
        addIfText(parts, request.getSourceUrl());
        if (extractedContent != null) {
            addIfText(parts, extractedContent.metadataSummary());
            addIfText(parts, truncate(extractedContent.text(), maxContentChars));
        }
        return String.join(" ", parts);
    }

    private void addIfText(List<String> target, String value) {
        if (StringUtils.hasText(value)) {
            target.add(value.trim());
        }
    }

    private ExtractedContent tryExtractFromMultipart(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            return extractFromBytes(bytes, file.getOriginalFilename(), file.getContentType());
        } catch (Exception ex) {
            log.warn("Failed to extract content from uploaded file", ex);
            return null;
        }
    }

    private ExtractedContent tryExtractFromStoredResource(ResourceEntity resource) {
        try {
            if (resourceStorageService.isManagedStorageKey(resource.getStorageKey())) {
                Resource managedResource = resourceStorageService.loadAsResource(resource.getStorageKey());
                try (InputStream inputStream = managedResource.getInputStream()) {
                    byte[] bytes = readLimitedBytes(inputStream, maxDownloadBytes);
                    MediaType mediaType = resourceStorageService.resolveMediaType(resource.getStorageKey());
                    return extractFromBytes(bytes, extractFileName(resource.getStorageKey()), mediaType.toString());
                }
            }
            return tryExtractFromSourceUrl(resource.getStorageKey(), extractFileName(resource.getStorageKey()));
        } catch (Exception ex) {
            log.warn("Failed to extract content from stored resource: {}", resource.getId(), ex);
            return null;
        }
    }

    private ExtractedContent tryExtractFromSourceUrl(String sourceUrl, String fileName) {
        if (!StringUtils.hasText(sourceUrl)) {
            return null;
        }
        try {
            URI uri = URI.create(sourceUrl.trim());
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                return null;
            }
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            String resolvedFileName = StringUtils.hasText(fileName) ? fileName : extractFileName(uri.getPath());
            String contentType = response.headers().firstValue("Content-Type").orElse(null);
            try (InputStream body = response.body()) {
                byte[] bytes = readLimitedBytes(body, maxDownloadBytes);
                return extractFromBytes(bytes, resolvedFileName, contentType);
            }
        } catch (Exception ex) {
            log.warn("Failed to fetch external resource for AI preview: {}", sourceUrl, ex);
            return null;
        }
    }

    private ExtractedContent extractFromBytes(byte[] bytes, String fileName, String contentType) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
            Metadata metadata = new Metadata();
            if (StringUtils.hasText(fileName)) {
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
            }
            if (StringUtils.hasText(contentType)) {
                metadata.set(Metadata.CONTENT_TYPE, contentType);
            }
            String text = tika.parseToString(inputStream, metadata);
            List<String> metadataEntries = new ArrayList<>();
            for (String name : metadata.names()) {
                String value = metadata.get(name);
                if (!StringUtils.hasText(value)) {
                    continue;
                }
                if (name.toLowerCase(Locale.ROOT).contains("content")) {
                    continue;
                }
                metadataEntries.add(name + ": " + value);
            }
            return new ExtractedContent(
                    truncate(text, maxContentChars),
                    truncate(String.join("; ", metadataEntries), 1000)
            );
        } catch (Exception ex) {
            log.warn("Failed to parse resource content for AI tag preview", ex);
            return null;
        }
    }

    private Map<Long, KnowledgePointEntity> loadKnowledgePointMap() {
        Map<Long, KnowledgePointEntity> map = new LinkedHashMap<>();
        for (KnowledgePointEntity knowledgePoint : knowledgePointRepository.findAllByOrderByOrderIndexAscIdAsc()) {
            map.put(knowledgePoint.getId(), knowledgePoint);
        }
        return map;
    }

    private String buildKnowledgePointPath(Long knowledgePointId, Map<Long, KnowledgePointEntity> knowledgePointMap) {
        List<String> names = new ArrayList<>();
        Long currentId = knowledgePointId;
        while (currentId != null) {
            KnowledgePointEntity knowledgePoint = knowledgePointMap.get(currentId);
            if (knowledgePoint == null) {
                break;
            }
            names.add(0, knowledgePoint.getName());
            currentId = knowledgePoint.getParentId();
        }
        return String.join(" / ", names);
    }

    private String normalizeSuggestionText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\s]+", " ");
    }

    private List<String> splitKeywords(String keywords) {
        if (!StringUtils.hasText(keywords)) {
            return List.of();
        }
        String[] rawParts = keywords.split("[,;\\n\\r]+");
        List<String> result = new ArrayList<>();
        for (String rawPart : rawParts) {
            if (StringUtils.hasText(rawPart)) {
                result.add(rawPart.trim());
            }
        }
        return result;
    }

    private String truncate(String value, int limit) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        if (value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }

    private byte[] readLimitedBytes(InputStream inputStream, int limit) throws IOException {
        byte[] buffer = inputStream.readNBytes(limit + 1);
        if (buffer.length <= limit) {
            return buffer;
        }
        byte[] trimmed = new byte[limit];
        System.arraycopy(buffer, 0, trimmed, 0, limit);
        return trimmed;
    }

    private String extractFileName(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value;
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        int slashIndex = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
        if (slashIndex >= 0 && slashIndex < normalized.length() - 1) {
            return normalized.substring(slashIndex + 1);
        }
        int managedIndex = normalized.lastIndexOf(':');
        if (managedIndex >= 0 && managedIndex < normalized.length() - 1) {
            return normalized.substring(managedIndex + 1);
        }
        return normalized;
    }

    private String normalizeBaseUrl(String rawBaseUrl) {
        if (!StringUtils.hasText(rawBaseUrl)) {
            return "https://api.deepseek.com";
        }
        return rawBaseUrl.endsWith("/") ? rawBaseUrl.substring(0, rawBaseUrl.length() - 1) : rawBaseUrl;
    }

    private record ExtractedContent(String text, String metadataSummary) {
    }
}
