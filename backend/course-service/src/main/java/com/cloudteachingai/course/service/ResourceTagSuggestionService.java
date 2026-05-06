package com.cloudteachingai.course.service;

import com.cloudteachingai.course.dto.ResourceTagPreviewRequest;
import com.cloudteachingai.course.dto.ResourceTagSuggestionResponse;
import com.cloudteachingai.course.entity.KnowledgePointEntity;
import com.cloudteachingai.course.entity.ResourceEntity;
import com.cloudteachingai.course.entity.ResourceTagEntity;
import com.cloudteachingai.course.entity.enums.KnowledgePointType;
import com.cloudteachingai.course.entity.enums.ResourceType;
import com.cloudteachingai.course.repository.KnowledgePointRepository;
import com.cloudteachingai.course.repository.ResourceTagRepository;
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
import java.util.HashMap;
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
    private static final Set<String> METADATA_TAG_STOP_WORDS = Set.of(
            "audio", "video", "codec", "bitrate", "duration", "resolution", "frame", "frames", "fps",
            "width", "height", "size", "bytes", "kbps", "mbps", "content", "format", "stream",
            "h264", "h265", "hevc", "avc", "aac", "mp3", "opus", "pcm", "mov", "mp4", "m4v", "webm"
    );
    private static final Set<String> TAG_STOP_WORDS = Set.of(
            "the", "and", "for", "with", "from", "this", "that", "into", "about", "your", "have", "will",
            "video", "document", "slide", "resource", "course", "lesson", "chapter",
            "一个", "一种", "一些", "这个", "那个", "我们", "你们", "他们", "课程", "资源", "视频", "文档", "课件",
            "学习", "内容", "相关", "介绍", "上传", "章节", "知识点"
    );

    private final KnowledgePointRepository knowledgePointRepository;
    private final ResourceTagRepository resourceTagRepository;
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

    @Value("${ai.tag-preview.fallback-api-key:}")
    private String fallbackApiKey;

    @Value("${ai.tag-preview.fallback-base-url:https://api.openai.com/v1}")
    private String fallbackBaseUrl;

    @Value("${ai.tag-preview.fallback-model:gpt-4.1-mini}")
    private String fallbackModel;

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
            ResourceTagRepository resourceTagRepository,
            ResourceStorageService resourceStorageService,
            ObjectMapper objectMapper
    ) {
        this.knowledgePointRepository = knowledgePointRepository;
        this.resourceTagRepository = resourceTagRepository;
        this.resourceStorageService = resourceStorageService;
        this.objectMapper = objectMapper;
    }

    public List<ResourceTagSuggestionResponse> suggestForPreview(ResourceTagPreviewRequest request, MultipartFile file) {
        log.info(
                "Start resource tag suggestion preview: type={}, titlePresent={}, descriptionPresent={}, sourceUrlPresent={}, fileName={}, filePresent={}, fileSize={}",
                request.getType(),
                StringUtils.hasText(request.getTitle()),
                StringUtils.hasText(request.getDescription()),
                StringUtils.hasText(request.getSourceUrl()),
                request.getFileName(),
                file != null && !file.isEmpty(),
                file == null ? 0 : file.getSize()
        );
        ExtractedContent extractedContent = file == null
                ? tryExtractFromSourceUrl(request.getSourceUrl(), request.getFileName(), request.getType())
                : tryExtractFromMultipart(file, request.getType());
        if (!previewEnabled) {
            log.info("AI preview disabled, fallback to rule suggestions only");
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
        Map<Long, KnowledgePointEntity> knowledgePointMap = loadKnowledgePointMap();
        List<TagCandidate> candidates = loadTagCandidates(knowledgePointMap);
        List<ResourceTagSuggestionResponse> ruleSuggestions = buildRuleSuggestions(request, extractedContent, candidates);
        List<ResourceTagSuggestionResponse> aiSuggestions = buildAiSuggestions(
                request,
                extractedContent,
                candidates,
                ruleSuggestions
        );
        log.info(
                "Resource tag suggestion finished: type={}, extractedTextLength={}, metadataLength={}, candidateCount={}, ruleCount={}, aiCount={}",
                request.getType(),
                extractedContent == null ? 0 : extractedContent.text().length(),
                extractedContent == null ? 0 : extractedContent.metadataSummary().length(),
                candidates.size(),
                ruleSuggestions.size(),
                aiSuggestions.size()
        );
        if (!aiSuggestions.isEmpty()) {
            return aiSuggestions;
        }
        if (!ruleSuggestions.isEmpty()) {
            return ruleSuggestions;
        }

        List<ResourceTagSuggestionResponse> generatedFallbackSuggestions = buildGeneratedFallbackSuggestions(request, extractedContent, candidates);
        if (!generatedFallbackSuggestions.isEmpty()) {
            log.info(
                    "Fallback generated tags used: type={}, count={}, fileName={}",
                    request.getType(),
                    generatedFallbackSuggestions.size(),
                    request.getFileName()
            );
        }
        return generatedFallbackSuggestions;
    }

    private List<ResourceTagSuggestionResponse> buildAiSuggestions(
            ResourceTagPreviewRequest request,
            ExtractedContent extractedContent,
            List<TagCandidate> candidates,
            List<ResourceTagSuggestionResponse> ruleSuggestions
    ) {
        TagProvider tagProvider = resolveTagProvider();
        if (!providerEnabled || tagProvider == null) {
            log.info("Skip AI tag preview: provider disabled or no chat provider configured");
            return List.of();
        }

        String analysisText = buildSemanticAnalysisText(request, extractedContent);
        if (!StringUtils.hasText(analysisText)) {
            log.warn(
                    "Skip AI tag preview because semantic analysis text is empty. type={}, fileName={}, sourceUrlPresent={}, extractedTextLength={}, metadataLength={}",
                    request.getType(),
                    request.getFileName(),
                    StringUtils.hasText(request.getSourceUrl()),
                    extractedContent == null ? 0 : extractedContent.text().length(),
                    extractedContent == null ? 0 : extractedContent.metadataSummary().length()
            );
            return List.of();
        }

        List<TagCandidate> llmCandidates = selectLlmCandidates(candidates, request, extractedContent, ruleSuggestions);

        try {
            List<ResourceTagSuggestionResponse> strictSuggestions = requestAiSuggestions(
                    tagProvider,
                    request,
                    analysisText,
                    llmCandidates,
                    false
            );
            if (!strictSuggestions.isEmpty()) {
                return strictSuggestions;
            }

            List<ResourceTagSuggestionResponse> permissiveSuggestions = requestAiSuggestions(
                    tagProvider,
                    request,
                    analysisText,
                    llmCandidates,
                    true
            );
            if (permissiveSuggestions.isEmpty()) {
                log.info(
                        "AI tag preview still returned no suggestions. type={}, fileName={}, analysisLength={}, providerBaseUrl={}, candidateCount={}",
                        request.getType(),
                        request.getFileName(),
                        analysisText.length(),
                        tagProvider.baseUrl(),
                        llmCandidates.size()
                );
            }
            return permissiveSuggestions;
        } catch (Exception ex) {
            log.warn("AI tag preview failed, fallback to rules", ex);
            return List.of();
        }
    }

    private List<ResourceTagSuggestionResponse> requestAiSuggestions(
            TagProvider tagProvider,
            ResourceTagPreviewRequest request,
            String analysisText,
            List<TagCandidate> llmCandidates,
            boolean allowClosestMatches
    ) throws Exception {
        Map<String, Object> payload = Map.of(
                "model", tagProvider.model(),
                "temperature", allowClosestMatches ? 0.35 : 0.2,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", allowClosestMatches
                                        ? "You label educational resources. First choose up to 5 best matches from the provided existing tag candidates. If the candidates are not enough, generate up to 3 additional concise tags in the same language as the resource. Return JSON only in the form {\"matchedExistingTags\":[{\"label\":\"tag\",\"confidence\":0.73,\"reason\":\"short reason\"}],\"generatedTags\":[{\"label\":\"new tag\",\"confidence\":0.58,\"reason\":\"short reason\"}]}. Prefer returning low-confidence approximate matches instead of an empty list."
                                        : "You label educational resources. First choose up to 5 best matches from the provided existing tag candidates. If the candidates are not enough, generate up to 3 additional concise tags in the same language as the resource. Return JSON only in the form {\"matchedExistingTags\":[{\"label\":\"tag\",\"confidence\":0.91,\"reason\":\"short reason\"}],\"generatedTags\":[{\"label\":\"new tag\",\"confidence\":0.66,\"reason\":\"short reason\"}]}. Prefer precise labels and avoid duplicates."
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
                                        "existingTagCandidates", llmCandidates.stream().map(item -> Map.of(
                                                "label", item.label(),
                                                "kind", item.kind(),
                                                "knowledgePointId", item.knowledgePointId(),
                                                "knowledgePointName", Optional.ofNullable(item.knowledgePointName()).orElse(""),
                                                "path", Optional.ofNullable(item.path()).orElse(""),
                                                "keywords", item.keywords()
                                        )).toList()
                                ))
                        )
                )
        );

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(normalizeBaseUrl(tagProvider.baseUrl()) + "/chat/completions"))
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + tagProvider.apiKey())
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

        JsonNode parsed = objectMapper.readTree(contentNode.asText());
        Map<String, TagCandidate> candidateMap = llmCandidates.stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.normalizedLabel(), item), Map::putAll);
        LinkedHashMap<String, ResourceTagSuggestionResponse> suggestions = new LinkedHashMap<>();

        JsonNode matchedExistingTags = parsed.path("matchedExistingTags");
        if (matchedExistingTags.isArray()) {
            for (JsonNode match : matchedExistingTags) {
                String label = normalizeDisplayLabel(match.path("label").asText());
                TagCandidate candidate = candidateMap.get(normalizeSuggestionText(label));
                if (candidate == null) {
                    continue;
                }
                double confidence = Math.max(0.0D, Math.min(0.99D, match.path("confidence").asDouble(allowClosestMatches ? 0.58D : 0.72D)));
                suggestions.putIfAbsent(candidate.normalizedLabel(), toSuggestionResponse(
                        candidate,
                        Math.round(confidence * 100.0D) / 100.0D,
                        StringUtils.hasText(match.path("reason").asText()) ? match.path("reason").asText() : "AI matched existing tag"
                ));
            }
        }

        JsonNode generatedTags = parsed.path("generatedTags");
        if (generatedTags.isArray()) {
            for (JsonNode generated : generatedTags) {
                String label = normalizeDisplayLabel(generated.path("label").asText());
                String normalizedLabel = normalizeSuggestionText(label);
                if (!StringUtils.hasText(normalizedLabel) || suggestions.containsKey(normalizedLabel)) {
                    continue;
                }
                double confidence = Math.max(0.0D, Math.min(0.99D, generated.path("confidence").asDouble(allowClosestMatches ? 0.52D : 0.66D)));
                suggestions.put(normalizedLabel, ResourceTagSuggestionResponse.builder()
                        .label(label)
                        .kind("GENERATED")
                        .confidence(Math.round(confidence * 100.0D) / 100.0D)
                        .reason(StringUtils.hasText(generated.path("reason").asText()) ? generated.path("reason").asText() : "AI generated tag")
                        .build());
            }
        }

        return suggestions.values().stream()
                .sorted(Comparator
                        .comparing(ResourceTagSuggestionResponse::getConfidence, Comparator.reverseOrder())
                        .thenComparing(item -> Optional.ofNullable(item.getLabel()).orElse("")))
                .limit(MAX_TAG_SUGGESTIONS)
                .toList();
    }

    private TagProvider resolveTagProvider() {
        if (StringUtils.hasText(apiKey)) {
            return new TagProvider(apiKey, baseUrl, model);
        }
        if (StringUtils.hasText(fallbackApiKey)) {
            return new TagProvider(fallbackApiKey, fallbackBaseUrl, fallbackModel);
        }
        return null;
    }

    private List<TagCandidate> selectLlmCandidates(
            List<TagCandidate> candidates,
            ResourceTagPreviewRequest request,
            ExtractedContent extractedContent,
            List<ResourceTagSuggestionResponse> ruleSuggestions
    ) {
        Map<String, TagCandidate> byNormalizedLabel = candidates.stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.normalizedLabel(), item), Map::putAll);
        List<TagCandidate> seeded = new ArrayList<>();
        for (ResourceTagSuggestionResponse suggestion : ruleSuggestions) {
            TagCandidate candidate = byNormalizedLabel.get(normalizeSuggestionText(suggestion.getLabel()));
            if (candidate != null) {
                seeded.add(candidate);
            }
        }
        if (seeded.size() >= MAX_LLM_CANDIDATES) {
            return seeded.subList(0, MAX_LLM_CANDIDATES);
        }

        String normalizedTitle = normalizeSuggestionText(request.getTitle());
        String normalizedBody = normalizeSuggestionText(buildSemanticAnalysisText(request, extractedContent));
        List<TagCandidate> ranked = candidates.stream()
                .sorted(Comparator
                        .comparing((TagCandidate item) -> candidateScore(item, normalizedTitle, normalizedBody))
                        .reversed()
                        .thenComparing(TagCandidate::label))
                .toList();

        LinkedHashMap<String, TagCandidate> merged = new LinkedHashMap<>();
        for (TagCandidate candidate : seeded) {
            merged.putIfAbsent(candidate.normalizedLabel(), candidate);
        }
        for (TagCandidate candidate : ranked) {
            merged.putIfAbsent(candidate.normalizedLabel(), candidate);
            if (merged.size() >= MAX_LLM_CANDIDATES) {
                break;
            }
        }
        return new ArrayList<>(merged.values());
    }

    private double candidateScore(TagCandidate candidate, String normalizedTitle, String normalizedBody) {
        double score = 0.0D;
        if (StringUtils.hasText(candidate.normalizedLabel()) && normalizedTitle.contains(candidate.normalizedLabel())) {
            score += 0.7D;
        } else if (StringUtils.hasText(candidate.normalizedLabel()) && normalizedBody.contains(candidate.normalizedLabel())) {
            score += 0.55D;
        }
        for (String keyword : candidate.keywords()) {
            String normalizedKeyword = normalizeSuggestionText(keyword);
            if (StringUtils.hasText(normalizedKeyword) && normalizedBody.contains(normalizedKeyword)) {
                score += 0.2D;
            }
        }
        if (StringUtils.hasText(candidate.path())) {
            for (String segment : candidate.path().split("/")) {
                String normalizedSegment = normalizeSuggestionText(segment);
                if (StringUtils.hasText(normalizedSegment) && normalizedBody.contains(normalizedSegment)) {
                    score += 0.05D;
                }
            }
        }
        return score;
    }

    private List<ResourceTagSuggestionResponse> buildRuleSuggestions(ResourceTagPreviewRequest request, ExtractedContent extractedContent) {
        return buildRuleSuggestions(request, extractedContent, loadTagCandidates(loadKnowledgePointMap()));
    }

    private List<ResourceTagSuggestionResponse> buildRuleSuggestions(
            ResourceTagPreviewRequest request,
            ExtractedContent extractedContent,
            List<TagCandidate> candidates
    ) {
        String normalizedTitle = normalizeSuggestionText(request.getTitle());
        String normalizedFullText = normalizeSuggestionText(buildSemanticAnalysisText(request, extractedContent));
        if (!StringUtils.hasText(normalizedFullText)) {
            return List.of();
        }

        List<ResourceTagSuggestionResponse> suggestions = new ArrayList<>();
        for (TagCandidate candidate : candidates) {
            double score = 0D;
            List<String> reasons = new ArrayList<>();
            if (StringUtils.hasText(candidate.normalizedLabel()) && normalizedTitle.contains(candidate.normalizedLabel())) {
                score += 0.75D;
                reasons.add("title match");
            } else if (StringUtils.hasText(candidate.normalizedLabel()) && normalizedFullText.contains(candidate.normalizedLabel())) {
                score += 0.6D;
                reasons.add("content match");
            }

            for (String keyword : candidate.keywords()) {
                String normalizedKeyword = normalizeSuggestionText(keyword);
                if (StringUtils.hasText(normalizedKeyword) && normalizedFullText.contains(normalizedKeyword)) {
                    score += 0.18D;
                    reasons.add("keyword " + keyword);
                }
            }

            if (StringUtils.hasText(candidate.path())) {
                for (String segment : candidate.path().split("/")) {
                    String normalizedSegment = normalizeSuggestionText(segment);
                    if (StringUtils.hasText(normalizedSegment) && normalizedFullText.contains(normalizedSegment)) {
                        score += 0.04D;
                        reasons.add("path match");
                    }
                }
            }

            if (score < 0.18D) {
                continue;
            }

            suggestions.add(toSuggestionResponse(
                    candidate,
                    Math.round(Math.min(0.99D, score) * 100.0D) / 100.0D,
                    String.join("; ", reasons.stream().distinct().toList())
            ));
        }

        return suggestions.stream()
                .sorted(Comparator
                        .comparing(ResourceTagSuggestionResponse::getConfidence, Comparator.reverseOrder())
                        .thenComparing(item -> Optional.ofNullable(item.getLabel()).orElse("")))
                .limit(MAX_TAG_SUGGESTIONS)
                .toList();
    }

    private List<TagCandidate> loadTagCandidates(Map<Long, KnowledgePointEntity> knowledgePointMap) {
        LinkedHashMap<String, TagCandidate> candidates = new LinkedHashMap<>();

        for (KnowledgePointEntity knowledgePoint : knowledgePointRepository.findByActiveTrueOrderByOrderIndexAscIdAsc()) {
            if (knowledgePoint.getNodeType() != KnowledgePointType.POINT) {
                continue;
            }
            TagCandidate candidate = new TagCandidate(
                    knowledgePoint.getName(),
                    normalizeSuggestionText(knowledgePoint.getName()),
                    "EXISTING",
                    knowledgePoint.getId(),
                    knowledgePoint.getName(),
                    buildKnowledgePointPath(knowledgePoint.getId(), knowledgePointMap),
                    splitKeywords(knowledgePoint.getKeywords())
            );
            if (StringUtils.hasText(candidate.normalizedLabel())) {
                candidates.putIfAbsent(candidate.normalizedLabel(), candidate);
            }
        }

        for (ResourceTagEntity tag : resourceTagRepository.findAllByOrderByNormalizedLabelAscIdAsc()) {
            String normalizedLabel = normalizeSuggestionText(tag.getLabel());
            if (!StringUtils.hasText(normalizedLabel)) {
                continue;
            }
            KnowledgePointEntity knowledgePoint = tag.getKnowledgePointId() == null ? null : knowledgePointMap.get(tag.getKnowledgePointId());
            candidates.putIfAbsent(normalizedLabel, new TagCandidate(
                    tag.getLabel(),
                    normalizedLabel,
                    "EXISTING",
                    tag.getKnowledgePointId(),
                    knowledgePoint == null ? null : knowledgePoint.getName(),
                    knowledgePoint == null ? null : buildKnowledgePointPath(knowledgePoint.getId(), knowledgePointMap),
                    knowledgePoint == null ? List.of() : splitKeywords(knowledgePoint.getKeywords())
            ));
        }

        return new ArrayList<>(candidates.values());
    }

    private List<ResourceTagSuggestionResponse> buildGeneratedFallbackSuggestions(
            ResourceTagPreviewRequest request,
            ExtractedContent extractedContent,
            List<TagCandidate> candidates
    ) {
        String analysisText = buildSemanticAnalysisText(request, extractedContent);
        if (!StringUtils.hasText(analysisText)) {
            log.info(
                    "Skip generated fallback suggestions because semantic analysis text is empty. type={}, fileName={}, transcriptLength={}, metadataLength={}",
                    request.getType(),
                    request.getFileName(),
                    extractedContent == null ? 0 : extractedContent.text().length(),
                    extractedContent == null ? 0 : extractedContent.metadataSummary().length()
            );
            return List.of();
        }

        LinkedHashMap<String, ResourceTagSuggestionResponse> matchedExisting = new LinkedHashMap<>();
        String normalizedBody = normalizeSuggestionText(analysisText);
        for (TagCandidate candidate : candidates) {
            if (!StringUtils.hasText(candidate.normalizedLabel())) {
                continue;
            }
            if (!normalizedBody.contains(candidate.normalizedLabel())) {
                continue;
            }
            double score = 0.68D;
            if (StringUtils.hasText(request.getTitle())
                    && normalizeSuggestionText(request.getTitle()).contains(candidate.normalizedLabel())) {
                score = 0.82D;
            }
            matchedExisting.putIfAbsent(candidate.normalizedLabel(), toSuggestionResponse(
                    candidate,
                    score,
                    "Fallback matched existing tag"
            ));
            if (matchedExisting.size() >= MAX_TAG_SUGGESTIONS) {
                return matchedExisting.values().stream().toList();
            }
        }

        Map<String, Integer> tokenScores = new HashMap<>();
        for (String token : extractFallbackTokens(analysisText)) {
            tokenScores.merge(token, 1, Integer::sum);
        }

        List<ResourceTagSuggestionResponse> generated = tokenScores.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(MAX_TAG_SUGGESTIONS)
                .map(entry -> ResourceTagSuggestionResponse.builder()
                        .label(entry.getKey())
                        .kind("GENERATED")
                        .confidence(Math.min(0.79D, 0.45D + (entry.getValue() * 0.08D)))
                        .reason("Fallback content keyword extraction")
                        .build())
                .toList();

        LinkedHashMap<String, ResourceTagSuggestionResponse> merged = new LinkedHashMap<>(matchedExisting);
        for (ResourceTagSuggestionResponse item : generated) {
            merged.putIfAbsent(normalizeSuggestionText(item.getLabel()), item);
            if (merged.size() >= MAX_TAG_SUGGESTIONS) {
                break;
            }
        }
        return new ArrayList<>(merged.values());
    }

    private List<String> extractFallbackTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }

        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String rawPart : text.split("[\\s,.;:!?()\\[\\]{}<>/\\\\|\"'`~@#$%^&*_+=-]+")) {
            String token = normalizeFallbackToken(rawPart);
            if (isUsefulFallbackToken(token)) {
                result.add(token);
            }
        }

        String compact = text.replaceAll("\\s+", "");
        java.util.regex.Matcher cjkMatcher = java.util.regex.Pattern.compile("[\\p{IsHan}]{2,8}").matcher(compact);
        while (cjkMatcher.find()) {
            String token = normalizeFallbackToken(cjkMatcher.group());
            if (isUsefulFallbackToken(token)) {
                result.add(token);
            }
        }
        return new ArrayList<>(result);
    }

    private String normalizeFallbackToken(String token) {
        if (!StringUtils.hasText(token)) {
            return "";
        }
        return token.trim()
                .replaceAll("^[^\\p{L}\\p{N}\\p{IsHan}]+|[^\\p{L}\\p{N}\\p{IsHan}]+$", "");
    }

    private boolean isUsefulFallbackToken(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        String normalized = token.toLowerCase(Locale.ROOT);
        if (TAG_STOP_WORDS.contains(normalized)) {
            return false;
        }
        if (METADATA_TAG_STOP_WORDS.contains(normalized)) {
            return false;
        }
        if (normalized.matches("\\d+")) {
            return false;
        }
        if (normalized.matches("\\d+[a-z]{1,4}")) {
            return false;
        }
        if (normalized.matches("\\d+x\\d+")) {
            return false;
        }
        if (normalized.matches("[a-z]+\\d+|\\d+[a-z]+")) {
            return false;
        }
        if (normalized.matches("[a-f0-9]{8,}")) {
            return false;
        }
        if (token.length() < 2) {
            return false;
        }
        if (token.length() > 24) {
            return false;
        }
        if (!token.matches(".*[\\p{IsHan}\\p{L}].*")) {
            return false;
        }
        return true;
    }

    private ResourceTagSuggestionResponse toSuggestionResponse(TagCandidate candidate, double confidence, String reason) {
        return ResourceTagSuggestionResponse.builder()
                .label(candidate.label())
                .kind(candidate.kind())
                .knowledgePointId(candidate.knowledgePointId())
                .knowledgePointName(candidate.knowledgePointName())
                .path(candidate.path())
                .confidence(confidence)
                .reason(reason)
                .build();
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

    private String buildSemanticAnalysisText(ResourceTagPreviewRequest request, ExtractedContent extractedContent) {
        List<String> parts = new ArrayList<>();
        addIfText(parts, request.getTitle());
        addIfText(parts, request.getDescription());
        if (!isVideoRequest(request)) {
            addIfText(parts, sanitizeSemanticFileName(request.getFileName()));
        }
        if (extractedContent != null) {
            addIfText(parts, truncate(extractedContent.text(), maxContentChars));
        }
        return String.join(" ", parts);
    }

    private boolean isVideoRequest(ResourceTagPreviewRequest request) {
        return isLikelyVideo(request.getFileName(), null, request.getType())
                || isLikelyVideo(extractFileName(request.getSourceUrl()), null, request.getType());
    }

    private String sanitizeSemanticFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        String withoutExtension = fileName;
        int lastDot = withoutExtension.lastIndexOf('.');
        if (lastDot > 0) {
            withoutExtension = withoutExtension.substring(0, lastDot);
        }
        String normalized = withoutExtension
                .replace('_', ' ')
                .replace('-', ' ')
                .replace('.', ' ')
                .trim();
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        if (normalized.matches("(?i)[a-z0-9]{8,}")) {
            return "";
        }
        return normalized;
    }

    private void addIfText(List<String> target, String value) {
        if (StringUtils.hasText(value)) {
            target.add(value.trim());
        }
    }

    private String normalizeDisplayLabel(String label) {
        return StringUtils.hasText(label) ? label.trim() : "";
    }

    private ExtractedContent tryExtractFromMultipart(MultipartFile file, String declaredType) {
        try {
            if (isLikelyVideo(file.getOriginalFilename(), file.getContentType(), declaredType)) {
                log.info("Preview extraction route: video multipart, fileName={}, contentType={}", file.getOriginalFilename(), file.getContentType());
                Path tempVideo = createTempFile(file.getOriginalFilename());
                try {
                    file.transferTo(tempVideo);
                    return extractFromVideoFile(tempVideo, file.getOriginalFilename(), file.getContentType());
                } finally {
                    Files.deleteIfExists(tempVideo);
                }
            }
            log.info("Preview extraction route: document multipart, fileName={}, contentType={}", file.getOriginalFilename(), file.getContentType());
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
            log.info("Skip sourceUrl extraction because sourceUrl is blank");
            return null;
        }
        try {
            URI uri = URI.create(sourceUrl.trim());
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                log.warn("Skip sourceUrl extraction because scheme is unsupported: {}", uri.getScheme());
                return null;
            }
            String resolvedFileName = StringUtils.hasText(fileName) ? fileName : extractFileName(uri.getPath());
            if (isLikelyVideo(resolvedFileName, null, declaredType)) {
                log.info("Skip remote video download for preview, fallback to metadata only. fileName={}", resolvedFileName);
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
        log.info(
                "Video extraction completed: fileName={}, durationSeconds={}, transcriptLength={}, metadataLength={}",
                fileName,
                probeInfo.durationSeconds(),
                transcript.length(),
                metadataSummary.length()
        );
        if (!StringUtils.hasText(transcript)) {
            log.warn(
                    "Video preview has no transcript content. fileName={}, durationSeconds={}, metadataLength={}",
                    fileName,
                    probeInfo.durationSeconds(),
                    metadataSummary.length()
            );
        }
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
            log.warn(
                    "Skip video transcription because provider is not fully configured. enabled={}, providerEnabled={}, apiKeyPresent={}",
                    videoTranscriptionEnabled,
                    videoTranscriptionProviderEnabled,
                    StringUtils.hasText(videoTranscriptionApiKey)
            );
            return "";
        }
        if (!isCommandAvailable("ffmpeg")) {
            log.warn("Skip video transcription because ffmpeg is unavailable in current runtime");
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
                    log.info(
                            "Video clip transcription completed: startSeconds={}, durationSeconds={}, transcriptLength={}",
                            clipPlan.startSeconds(),
                            clipPlan.durationSeconds(),
                            transcript.length()
                    );
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
            log.info("Requesting audio transcription: fileName={}, size={}", audioClip.getFileName(), Files.size(audioClip));
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

    private record TagCandidate(
            String label,
            String normalizedLabel,
            String kind,
            Long knowledgePointId,
            String knowledgePointName,
            String path,
            List<String> keywords
    ) {
    }

    private record TagProvider(String apiKey, String baseUrl, String model) {
    }
}
