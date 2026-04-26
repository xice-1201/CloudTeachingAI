package com.cloudteachingai.course.service;

import com.cloudteachingai.course.dto.ResourceTagPreviewRequest;
import com.cloudteachingai.course.dto.ResourceTagSuggestionResponse;
import com.cloudteachingai.course.entity.KnowledgePointEntity;
import com.cloudteachingai.course.entity.ResourceEntity;
import com.cloudteachingai.course.entity.enums.KnowledgePointType;
import com.cloudteachingai.course.entity.enums.ResourceType;
import com.cloudteachingai.course.repository.KnowledgePointRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class ResourceTagSuggestionService {

    private static final int MAX_TAG_SUGGESTIONS = 8;
    private static final int MAX_LLM_CANDIDATES = 120;
    private static final String MULTIPART_EOL = "\r\n";
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(".mp4", ".mov", ".m4v", ".webm");

    private final KnowledgePointRepository knowledgePointRepository;
    private final ResourceStorageService resourceStorageService;
    private final ObjectMapper objectMapper;
    private final Tika tika = new Tika();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private volatile Boolean ffmpegAvailable;
    private volatile Boolean ffprobeAvailable;

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

    @Value("${ai.tag-preview.video-transcription.enabled:true}")
    private boolean videoTranscriptionEnabled;

    @Value("${ai.tag-preview.video-transcription.provider-enabled:true}")
    private boolean videoTranscriptionProviderEnabled;

    @Value("${ai.tag-preview.video-transcription.api-key:}")
    private String videoTranscriptionApiKey;

    @Value("${ai.tag-preview.video-transcription.base-url:https://api.openai.com/v1}")
    private String videoTranscriptionBaseUrl;

    @Value("${ai.tag-preview.video-transcription.model:gpt-4o-mini-transcribe}")
    private String videoTranscriptionModel;

    @Value("${ai.tag-preview.video-transcription.clip-duration-seconds:18}")
    private int videoClipDurationSeconds;

    @Value("${ai.tag-preview.video-transcription.max-clips:3}")
    private int videoMaxClips;

    @Value("${ai.tag-preview.video-transcription.max-transcript-chars:4000}")
    private int videoMaxTranscriptChars;

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
        ExtractedContent extractedContent = file == null
                ? tryExtractFromSourceUrl(request.getSourceUrl(), request.getFileName(), request.getType())
                : tryExtractFromMultipart(file, request.getType());
        if (!previewEnabled) {
            return buildRuleSuggestions(request, extractedContent);
        }
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
        return aiSuggestions.isEmpty() ? ruleSuggestions : aiSuggestions;
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
                                    "content", "You label educational resources with knowledge points. Choose only from the provided candidate leaf knowledge points and return JSON in the form {\"matches\":[{\"knowledgePointId\":1,\"confidence\":0.91,\"reason\":\"short reason\"}]}."
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

            Map<Long, KnowledgePointEntity> candidateMap = llmCandidates.stream()
                    .collect(LinkedHashMap::new, (map, item) -> map.put(item.getId(), item), Map::putAll);
            List<ResourceTagSuggestionResponse> suggestions = new ArrayList<>();
            for (JsonNode match : matches) {
                long knowledgePointId = match.path("knowledgePointId").asLong(-1L);
                KnowledgePointEntity knowledgePoint = candidateMap.get(knowledgePointId);
                if (knowledgePoint == null) {
                    continue;
                }
                double confidence = Math.max(0.0D, Math.min(0.99D, match.path("confidence").asDouble(0.72D)));
                suggestions.add(ResourceTagSuggestionResponse.builder()
                        .knowledgePointId(knowledgePoint.getId())
                        .knowledgePointName(knowledgePoint.getName())
                        .path(buildKnowledgePointPath(knowledgePoint.getId(), knowledgePointMap))
                        .confidence(Math.round(confidence * 100.0D) / 100.0D)
                        .reason(StringUtils.hasText(match.path("reason").asText()) ? match.path("reason").asText() : "AI content analysis")
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
                reasons.add("title match");
            } else if (StringUtils.hasText(pointName) && normalizedFullText.contains(pointName)) {
                score += 0.65D;
                reasons.add("content match");
            }

            for (String keyword : splitKeywords(knowledgePoint.getKeywords())) {
                String normalizedKeyword = normalizeSuggestionText(keyword);
                if (StringUtils.hasText(normalizedKeyword) && normalizedFullText.contains(normalizedKeyword)) {
                    score += 0.18D;
                    reasons.add("keyword " + keyword);
                }
            }

            KnowledgePointEntity domain = knowledgePointMap.get(knowledgePoint.getParentId());
            if (domain != null) {
                String normalizedDomain = normalizeSuggestionText(domain.getName());
                if (StringUtils.hasText(normalizedDomain) && normalizedFullText.contains(normalizedDomain)) {
                    score += 0.08D;
                    reasons.add("domain match");
                }

                KnowledgePointEntity subject = domain.getParentId() == null ? null : knowledgePointMap.get(domain.getParentId());
                if (subject != null) {
                    String normalizedSubject = normalizeSuggestionText(subject.getName());
                    if (StringUtils.hasText(normalizedSubject) && normalizedFullText.contains(normalizedSubject)) {
                        score += 0.04D;
                        reasons.add("subject match");
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

    private ExtractedContent tryExtractFromMultipart(MultipartFile file, String declaredType) {
        try {
            if (isLikelyVideo(file.getOriginalFilename(), file.getContentType(), declaredType)) {
                Path tempVideo = createTempFile(file.getOriginalFilename());
                try {
                    file.transferTo(tempVideo);
                    return extractFromVideoFile(tempVideo, file.getOriginalFilename(), file.getContentType());
                } finally {
                    Files.deleteIfExists(tempVideo);
                }
            }
            byte[] bytes = file.getBytes();
            return extractFromBytes(bytes, file.getOriginalFilename(), file.getContentType(), declaredType);
        } catch (Exception ex) {
            log.warn("Failed to extract content from uploaded file", ex);
            return null;
        }
    }

    private ExtractedContent tryExtractFromStoredResource(ResourceEntity resource) {
        try {
            String type = resource.getType() == null ? null : resource.getType().name();
            String fileName = extractFileName(resource.getStorageKey());
            if (resourceStorageService.isManagedStorageKey(resource.getStorageKey())) {
                if (isLikelyVideo(fileName, resourceStorageService.resolveMediaType(resource.getStorageKey()).toString(), type)) {
                    return extractFromVideoFile(
                            resourceStorageService.resolveManagedFilePath(resource.getStorageKey()),
                            fileName,
                            resourceStorageService.resolveMediaType(resource.getStorageKey()).toString()
                    );
                }
                try (InputStream inputStream = Files.newInputStream(resourceStorageService.resolveManagedFilePath(resource.getStorageKey()))) {
                    byte[] bytes = readLimitedBytes(inputStream, maxDownloadBytes);
                    return extractFromBytes(bytes, fileName, resourceStorageService.resolveMediaType(resource.getStorageKey()).toString(), type);
                }
            }
            return tryExtractFromSourceUrl(resource.getStorageKey(), fileName, type);
        } catch (Exception ex) {
            log.warn("Failed to extract content from stored resource: {}", resource.getId(), ex);
            return null;
        }
    }

    private ExtractedContent tryExtractFromSourceUrl(String sourceUrl, String fileName, String declaredType) {
        if (!StringUtils.hasText(sourceUrl)) {
            return null;
        }
        try {
            URI uri = URI.create(sourceUrl.trim());
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                return null;
            }
            String resolvedFileName = StringUtils.hasText(fileName) ? fileName : extractFileName(uri.getPath());
            if (isLikelyVideo(resolvedFileName, null, declaredType)) {
                return new ExtractedContent("", truncate("remote video: " + resolvedFileName + "; source: " + sourceUrl, 1000));
            }

            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            String contentType = response.headers().firstValue("Content-Type").orElse(null);
            try (InputStream body = response.body()) {
                byte[] bytes = readLimitedBytes(body, maxDownloadBytes);
                return extractFromBytes(bytes, resolvedFileName, contentType, declaredType);
            }
        } catch (Exception ex) {
            log.warn("Failed to fetch external resource for AI preview: {}", sourceUrl, ex);
            return null;
        }
    }

    private ExtractedContent extractFromBytes(byte[] bytes, String fileName, String contentType, String declaredType) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        if (isLikelyVideo(fileName, contentType, declaredType)) {
            Path tempVideo = null;
            try {
                tempVideo = createTempFile(fileName);
                Files.write(tempVideo, bytes);
                return extractFromVideoFile(tempVideo, fileName, contentType);
            } catch (Exception ex) {
                log.warn("Failed to parse video resource for AI tag preview", ex);
                return new ExtractedContent("", truncate("video file: " + Optional.ofNullable(fileName).orElse("unknown"), 1000));
            } finally {
                if (tempVideo != null) {
                    try {
                        Files.deleteIfExists(tempVideo);
                    } catch (IOException ignored) {
                    }
                }
            }
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
                if (!StringUtils.hasText(value) || name.toLowerCase(Locale.ROOT).contains("content")) {
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

    private ExtractedContent extractFromVideoFile(Path videoPath, String fileName, String contentType) {
        VideoProbeInfo probeInfo = probeVideo(videoPath);
        String transcript = transcribeVideo(videoPath, probeInfo.durationSeconds());
        String metadataSummary = buildVideoMetadataSummary(fileName, contentType, probeInfo);
        return new ExtractedContent(transcript, metadataSummary);
    }

    private VideoProbeInfo probeVideo(Path videoPath) {
        if (!isCommandAvailable("ffprobe")) {
            return new VideoProbeInfo(0.0D, "");
        }
        try {
            Process process = new ProcessBuilder(
                    "ffprobe",
                    "-v", "error",
                    "-show_entries", "format=duration,size,bit_rate:stream=codec_type,codec_name,width,height",
                    "-of", "json",
                    videoPath.toAbsolutePath().toString()
            ).start();
            String stdout = new String(readLimitedBytes(process.getInputStream(), 32 * 1024), StandardCharsets.UTF_8);
            String stderr = new String(readLimitedBytes(process.getErrorStream(), 8 * 1024), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0 || !StringUtils.hasText(stdout)) {
                log.warn("ffprobe failed for {}: {}", videoPath, truncate(stderr, 500));
                return new VideoProbeInfo(0.0D, "");
            }

            JsonNode root = objectMapper.readTree(stdout);
            JsonNode format = root.path("format");
            double durationSeconds = format.path("duration").asDouble(0.0D);
            List<String> parts = new ArrayList<>();
            if (durationSeconds > 0.0D) {
                parts.add("duration " + Math.round(durationSeconds) + "s");
            }
            long sizeBytes = format.path("size").asLong(0L);
            if (sizeBytes > 0L) {
                parts.add("size " + sizeBytes + "B");
            }
            long bitRate = format.path("bit_rate").asLong(0L);
            if (bitRate > 0L) {
                parts.add("bitrate " + bitRate);
            }

            JsonNode streams = root.path("streams");
            if (streams.isArray()) {
                for (JsonNode stream : streams) {
                    String codecType = stream.path("codec_type").asText();
                    if (!StringUtils.hasText(codecType)) {
                        continue;
                    }
                    if ("video".equals(codecType)) {
                        String resolution = stream.path("width").asInt(0) > 0 && stream.path("height").asInt(0) > 0
                                ? stream.path("width").asInt() + "x" + stream.path("height").asInt()
                                : "";
                        parts.add("video " + joinNonBlank(" ", stream.path("codec_name").asText(""), resolution));
                    } else if ("audio".equals(codecType)) {
                        parts.add("audio " + stream.path("codec_name").asText(""));
                    }
                }
            }
            return new VideoProbeInfo(durationSeconds, truncate(String.join("; ", parts), 1000));
        } catch (Exception ex) {
            log.warn("Failed to inspect video metadata: {}", videoPath, ex);
            return new VideoProbeInfo(0.0D, "");
        }
    }

    private String transcribeVideo(Path videoPath, double durationSeconds) {
        if (!videoTranscriptionEnabled || !videoTranscriptionProviderEnabled || !StringUtils.hasText(videoTranscriptionApiKey)) {
            return "";
        }
        if (!isCommandAvailable("ffmpeg")) {
            return "";
        }

        LinkedHashSet<String> transcriptSegments = new LinkedHashSet<>();
        for (ClipPlan clipPlan : buildClipPlans(durationSeconds)) {
            Path audioClip = null;
            try {
                audioClip = extractAudioClip(videoPath, clipPlan);
                if (audioClip == null) {
                    continue;
                }
                String transcript = requestAudioTranscription(audioClip);
                if (StringUtils.hasText(transcript)) {
                    transcriptSegments.add(transcript.trim());
                }
            } catch (Exception ex) {
                log.warn("Failed to transcribe video clip for {}", videoPath, ex);
            } finally {
                if (audioClip != null) {
                    try {
                        Files.deleteIfExists(audioClip);
                    } catch (IOException ignored) {
                    }
                }
            }
        }
        return truncate(String.join(" ", transcriptSegments), videoMaxTranscriptChars);
    }

    private List<ClipPlan> buildClipPlans(double durationSeconds) {
        int clipDuration = Math.max(8, videoClipDurationSeconds);
        int maxClips = Math.max(1, videoMaxClips);
        List<ClipPlan> plans = new ArrayList<>();

        if (durationSeconds <= 0.0D) {
            plans.add(new ClipPlan(8.0D, clipDuration));
            return plans;
        }

        if (durationSeconds <= 75.0D) {
            plans.add(new ClipPlan(Math.min(6.0D, Math.max(0.0D, durationSeconds / 5.0D)), Math.min(clipDuration, durationSeconds)));
            return plans;
        }

        plans.add(new ClipPlan(8.0D, clipDuration));
        if (durationSeconds > 600.0D && maxClips >= 3) {
            double middleStart = Math.max(0.0D, (durationSeconds / 2.0D) - (clipDuration / 2.0D));
            plans.add(new ClipPlan(middleStart, clipDuration));
        }
        if (maxClips >= 2) {
            double tailStart = Math.max(0.0D, durationSeconds - clipDuration - 12.0D);
            plans.add(new ClipPlan(tailStart, clipDuration));
        }

        List<ClipPlan> deduped = new ArrayList<>();
        for (ClipPlan plan : plans) {
            double safeStart = Math.max(0.0D, Math.min(plan.startSeconds(), Math.max(0.0D, durationSeconds - 3.0D)));
            double safeDuration = Math.min(plan.durationSeconds(), Math.max(3.0D, durationSeconds - safeStart));
            boolean duplicate = deduped.stream().anyMatch(existing -> Math.abs(existing.startSeconds() - safeStart) < 6.0D);
            if (!duplicate) {
                deduped.add(new ClipPlan(safeStart, safeDuration));
            }
            if (deduped.size() >= maxClips) {
                break;
            }
        }
        return deduped;
    }

    private Path extractAudioClip(Path videoPath, ClipPlan clipPlan) throws IOException, InterruptedException {
        Path outputFile = Files.createTempFile("resource-tag-preview-audio-", ".mp3");
        Process process = new ProcessBuilder(
                "ffmpeg",
                "-hide_banner",
                "-loglevel", "error",
                "-y",
                "-ss", formatSeconds(clipPlan.startSeconds()),
                "-t", formatSeconds(clipPlan.durationSeconds()),
                "-i", videoPath.toAbsolutePath().toString(),
                "-vn",
                "-ac", "1",
                "-ar", "16000",
                "-c:a", "mp3",
                outputFile.toAbsolutePath().toString()
        ).start();
        String stderr = new String(readLimitedBytes(process.getErrorStream(), 8 * 1024), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0 || !Files.exists(outputFile) || Files.size(outputFile) == 0L) {
            Files.deleteIfExists(outputFile);
            log.warn("ffmpeg failed to extract audio clip from {}: {}", videoPath, truncate(stderr, 500));
            return null;
        }
        return outputFile;
    }

    private String requestAudioTranscription(Path audioClip) {
        try {
            String boundary = "----CloudTeachingAI" + UUID.randomUUID();
            byte[] requestBody = buildTranscriptionRequestBody(boundary, audioClip);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(videoTranscriptionBaseUrl) + "/audio/transcriptions"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + videoTranscriptionApiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Video transcription request failed: status={}, body={}", response.statusCode(), truncate(response.body(), 1000));
                return "";
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (root.hasNonNull("text")) {
                return truncate(root.path("text").asText(), videoMaxTranscriptChars);
            }
            if (root.isTextual()) {
                return truncate(root.asText(), videoMaxTranscriptChars);
            }
            return "";
        } catch (Exception ex) {
            log.warn("Failed to request video transcription", ex);
            return "";
        }
    }

    private byte[] buildTranscriptionRequestBody(String boundary, Path audioClip) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeFormField(body, boundary, "model", videoTranscriptionModel);
        writeFormField(body, boundary, "response_format", "json");
        byte[] fileBytes = Files.readAllBytes(audioClip);
        writeFileField(body, boundary, "file", audioClip.getFileName().toString(), "audio/mpeg", fileBytes);
        body.write(("--" + boundary + "--" + MULTIPART_EOL).getBytes(StandardCharsets.UTF_8));
        return body.toByteArray();
    }

    private void writeFormField(ByteArrayOutputStream target, String boundary, String fieldName, String value) throws IOException {
        target.write(("--" + boundary + MULTIPART_EOL).getBytes(StandardCharsets.UTF_8));
        target.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"" + MULTIPART_EOL + MULTIPART_EOL).getBytes(StandardCharsets.UTF_8));
        target.write(Optional.ofNullable(value).orElse("").getBytes(StandardCharsets.UTF_8));
        target.write(MULTIPART_EOL.getBytes(StandardCharsets.UTF_8));
    }

    private void writeFileField(
            ByteArrayOutputStream target,
            String boundary,
            String fieldName,
            String fileName,
            String contentType,
            byte[] fileBytes
    ) throws IOException {
        target.write(("--" + boundary + MULTIPART_EOL).getBytes(StandardCharsets.UTF_8));
        target.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"" + MULTIPART_EOL).getBytes(StandardCharsets.UTF_8));
        target.write(("Content-Type: " + contentType + MULTIPART_EOL + MULTIPART_EOL).getBytes(StandardCharsets.UTF_8));
        target.write(fileBytes);
        target.write(MULTIPART_EOL.getBytes(StandardCharsets.UTF_8));
    }

    private String buildVideoMetadataSummary(String fileName, String contentType, VideoProbeInfo probeInfo) {
        List<String> parts = new ArrayList<>();
        addIfText(parts, fileName);
        addIfText(parts, contentType);
        addIfText(parts, probeInfo.metadataSummary());
        return truncate(String.join("; ", parts), 1000);
    }

    private boolean isLikelyVideo(String fileName, String contentType, String declaredType) {
        if (StringUtils.hasText(declaredType) && ResourceType.VIDEO.name().equalsIgnoreCase(declaredType.trim())) {
            return true;
        }
        if (StringUtils.hasText(contentType) && contentType.toLowerCase(Locale.ROOT).startsWith("video/")) {
            return true;
        }
        String extension = extractExtension(fileName);
        return VIDEO_EXTENSIONS.contains(extension);
    }

    private String extractExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0) {
            return "";
        }
        return fileName.substring(lastDot).toLowerCase(Locale.ROOT);
    }

    private Path createTempFile(String fileName) throws IOException {
        String extension = extractExtension(fileName);
        if (!StringUtils.hasText(extension)) {
            extension = ".bin";
        }
        return Files.createTempFile("resource-tag-preview-", extension);
    }

    private boolean isCommandAvailable(String command) {
        if ("ffmpeg".equals(command) && ffmpegAvailable != null) {
            return ffmpegAvailable;
        }
        if ("ffprobe".equals(command) && ffprobeAvailable != null) {
            return ffprobeAvailable;
        }
        boolean available;
        try {
            Process process = new ProcessBuilder(command, "-version").start();
            int exitCode = process.waitFor();
            available = exitCode == 0;
        } catch (Exception ex) {
            available = false;
        }
        if ("ffmpeg".equals(command)) {
            ffmpegAvailable = available;
        } else if ("ffprobe".equals(command)) {
            ffprobeAvailable = available;
        }
        return available;
    }

    private String formatSeconds(double seconds) {
        return String.format(Locale.ROOT, "%.3f", Math.max(0.0D, seconds));
    }

    private String joinNonBlank(String delimiter, String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                parts.add(value.trim());
            }
        }
        return String.join(delimiter, parts);
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

    private record VideoProbeInfo(double durationSeconds, String metadataSummary) {
    }

    private record ClipPlan(double startSeconds, double durationSeconds) {
    }
}
