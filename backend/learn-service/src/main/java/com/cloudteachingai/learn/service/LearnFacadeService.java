package com.cloudteachingai.learn.service;

import com.cloudteachingai.learn.client.CourseApiResponse;
import com.cloudteachingai.learn.client.CourseChapterResponse;
import com.cloudteachingai.learn.client.CourseKnowledgePointNodeResponse;
import com.cloudteachingai.learn.client.CourseResourceKnowledgePointResponse;
import com.cloudteachingai.learn.client.CourseResourceResponse;
import com.cloudteachingai.learn.client.CourseServiceClient;
import com.cloudteachingai.learn.controller.LearnController.UserContext;
import com.cloudteachingai.learn.dto.AbilityMapResponse;
import com.cloudteachingai.learn.dto.AbilityTestAnswerRequest;
import com.cloudteachingai.learn.dto.AbilityTestAnswerResponse;
import com.cloudteachingai.learn.dto.AbilityTestQuestionOptionResponse;
import com.cloudteachingai.learn.dto.AbilityTestQuestionResponse;
import com.cloudteachingai.learn.dto.AbilityTestStartRequest;
import com.cloudteachingai.learn.dto.AbilityTestStartResponse;
import com.cloudteachingai.learn.dto.CourseProgressResponse;
import com.cloudteachingai.learn.dto.LearningProgressResponse;
import com.cloudteachingai.learn.dto.UpdateLearningProgressRequest;
import com.cloudteachingai.learn.entity.AbilityMapEntity;
import com.cloudteachingai.learn.entity.AbilityTestQuestionEntity;
import com.cloudteachingai.learn.entity.AbilityTestSessionEntity;
import com.cloudteachingai.learn.entity.LearningProgressEntity;
import com.cloudteachingai.learn.exception.BusinessException;
import com.cloudteachingai.learn.repository.AbilityMapRepository;
import com.cloudteachingai.learn.repository.AbilityTestQuestionRepository;
import com.cloudteachingai.learn.repository.AbilityTestSessionRepository;
import com.cloudteachingai.learn.repository.LearningProgressRepository;
import feign.FeignException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LearnFacadeService {

    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final int DEFAULT_QUESTION_LIMIT = 6;
    private static final int MAX_RADAR_POINTS = 8;
    private static final Map<String, Double> ANSWER_SCORES = Map.of(
            "A", 0.25D,
            "B", 0.5D,
            "C", 0.75D,
            "D", 1D
    );

    private final LearningProgressRepository learningProgressRepository;
    private final AbilityTestSessionRepository abilityTestSessionRepository;
    private final AbilityTestQuestionRepository abilityTestQuestionRepository;
    private final AbilityMapRepository abilityMapRepository;
    private final CourseServiceClient courseServiceClient;

    public LearningProgressResponse getResourceProgress(Long resourceId, UserContext userContext) {
        assertStudent(userContext);
        return learningProgressRepository.findByStudentIdAndResourceId(userContext.userId(), resourceId)
                .map(this::toLearningProgressResponse)
                .orElseGet(() -> LearningProgressResponse.builder()
                        .resourceId(resourceId)
                        .progress(0D)
                        .lastPosition(null)
                        .completed(false)
                        .lastAccessedAt(OffsetDateTime.now(ZoneOffset.UTC).toString())
                        .build());
    }

    @Transactional
    public LearningProgressResponse updateResourceProgress(
            Long resourceId,
            UpdateLearningProgressRequest request,
            String authorization,
            UserContext userContext) {
        assertStudent(userContext);
        resolveTotalResources(request.getCourseId(), authorization);

        double sanitizedProgress = clampProgress(request.getProgress());
        int sanitizedPosition = sanitizeLastPosition(request.getLastPosition());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        LearningProgressEntity progress = learningProgressRepository.findByStudentIdAndResourceId(userContext.userId(), resourceId)
                .orElseGet(() -> LearningProgressEntity.builder()
                        .studentId(userContext.userId())
                        .courseId(request.getCourseId())
                        .resourceId(resourceId)
                        .progress(0D)
                        .completed(false)
                        .build());

        progress.setCourseId(progress.getCourseId() == null ? request.getCourseId() : progress.getCourseId());
        progress.setProgress(Math.max(progress.getProgress(), sanitizedProgress));
        progress.setLastPosition(sanitizedPosition > 0 ? sanitizedPosition : null);
        progress.setLastAccessedAt(now);
        progress.setCompleted(progress.getProgress() >= 0.999D);
        if (Boolean.TRUE.equals(progress.getCompleted()) && progress.getCompletedAt() == null) {
            progress.setCompletedAt(now);
        }

        LearningProgressEntity saved = learningProgressRepository.save(progress);
        return toLearningProgressResponse(saved);
    }

    public CourseProgressResponse getCourseProgress(
            Long courseId,
            String authorization,
            UserContext userContext) {
        assertStudent(userContext);

        List<LearningProgressEntity> progresses = learningProgressRepository.findByStudentIdAndCourseId(userContext.userId(), courseId);
        int trackedResources = progresses.size();
        int totalResources = Math.max(resolveTotalResources(courseId, authorization), trackedResources);
        int completedResources = (int) progresses.stream().filter(progress -> Boolean.TRUE.equals(progress.getCompleted())).count();
        double totalProgress = progresses.stream().mapToDouble(progress -> progress.getProgress() == null ? 0D : progress.getProgress()).sum();
        double courseProgress = totalResources == 0 ? 0D : Math.min(1D, totalProgress / totalResources);
        OffsetDateTime lastLearnedAt = progresses.stream()
                .map(LearningProgressEntity::getLastAccessedAt)
                .filter(value -> value != null)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return CourseProgressResponse.builder()
                .courseId(courseId)
                .progress(courseProgress)
                .totalResources(totalResources)
                .completedResources(completedResources)
                .lastLearnedAt(lastLearnedAt == null ? null : lastLearnedAt.toString())
                .build();
    }

    public AbilityTestStartResponse startAbilityTest(
            AbilityTestStartRequest request,
            String authorization,
            UserContext userContext) {
        assertStudent(userContext);

        KnowledgePointCatalog catalog = loadKnowledgePointCatalog(authorization);
        CourseKnowledgePointNodeResponse root = catalog.nodesById().get(request.getKnowledgePointId());
        if (root == null) {
            throw BusinessException.notFound("Knowledge point not found");
        }

        List<CourseKnowledgePointNodeResponse> targets = collectQuestionTargets(root);
        if (targets.isEmpty()) {
            throw BusinessException.badRequest("Selected knowledge point has no available test targets");
        }

        int questionLimit = Math.min(
                request.getQuestionLimit() == null ? DEFAULT_QUESTION_LIMIT : request.getQuestionLimit(),
                targets.size()
        );
        List<CourseKnowledgePointNodeResponse> selectedTargets = targets.stream()
                .sorted(Comparator.comparing(CourseKnowledgePointNodeResponse::getPath, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(CourseKnowledgePointNodeResponse::getId))
                .limit(questionLimit)
                .toList();

        AbilityTestSessionEntity session = abilityTestSessionRepository.save(AbilityTestSessionEntity.builder()
                .studentId(userContext.userId())
                .rootKnowledgePointId(root.getId())
                .rootKnowledgePointName(root.getName())
                .status(STATUS_IN_PROGRESS)
                .questionCount(selectedTargets.size())
                .answeredCount(0)
                .startedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());

        List<AbilityTestQuestionEntity> questions = new ArrayList<>();
        for (int index = 0; index < selectedTargets.size(); index++) {
            CourseKnowledgePointNodeResponse point = selectedTargets.get(index);
            questions.add(AbilityTestQuestionEntity.builder()
                    .sessionId(session.getId())
                    .knowledgePointId(point.getId())
                    .knowledgePointName(point.getName())
                    .prompt(buildQuestionPrompt(point))
                    .optionA("我对这个知识点几乎不了解，需要从基础开始。")
                    .optionB("我知道基本概念，但独立完成相关任务仍比较吃力。")
                    .optionC("我能完成常规练习或学习任务，偶尔还需要提示。")
                    .optionD("我可以独立应用这个知识点，并向别人解释清楚。")
                    .displayOrder(index + 1)
                    .answered(false)
                    .build());
        }
        List<AbilityTestQuestionEntity> savedQuestions = abilityTestQuestionRepository.saveAll(questions);

        return AbilityTestStartResponse.builder()
                .sessionId(session.getId())
                .rootKnowledgePointName(root.getName())
                .totalQuestions(savedQuestions.size())
                .question(toQuestionResponse(savedQuestions.getFirst(), savedQuestions.size()))
                .build();
    }

    @Transactional
    public AbilityTestAnswerResponse submitAbilityTestAnswer(
            Long sessionId,
            AbilityTestAnswerRequest request,
            String authorization,
            UserContext userContext) {
        assertStudent(userContext);

        AbilityTestSessionEntity session = abilityTestSessionRepository.findByIdAndStudentId(sessionId, userContext.userId())
                .orElseThrow(() -> BusinessException.notFound("Ability test session not found"));
        if (STATUS_COMPLETED.equals(session.getStatus())) {
            return AbilityTestAnswerResponse.builder()
                    .sessionId(sessionId)
                    .answeredCount(session.getAnsweredCount())
                    .totalQuestions(session.getQuestionCount())
                    .completed(true)
                    .abilityMap(getAbilityMap(authorization, userContext))
                    .build();
        }

        AbilityTestQuestionEntity question = abilityTestQuestionRepository.findByIdAndSessionId(request.getQuestionId(), sessionId)
                .orElseThrow(() -> BusinessException.notFound("Ability test question not found"));
        if (Boolean.TRUE.equals(question.getAnswered())) {
            throw BusinessException.badRequest("Question already answered");
        }

        String normalizedAnswer = normalizeAnswer(request.getAnswer());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        question.setAnswered(true);
        question.setSelectedAnswer(normalizedAnswer);
        question.setScore(ANSWER_SCORES.get(normalizedAnswer));
        question.setAnsweredAt(now);
        abilityTestQuestionRepository.save(question);

        session.setAnsweredCount(Math.min(session.getQuestionCount(), session.getAnsweredCount() + 1));
        List<AbilityTestQuestionEntity> allQuestions = abilityTestQuestionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId);
        Optional<AbilityTestQuestionEntity> nextQuestion = allQuestions.stream()
                .filter(item -> !Boolean.TRUE.equals(item.getAnswered()))
                .findFirst();

        if (nextQuestion.isPresent()) {
            abilityTestSessionRepository.save(session);
            return AbilityTestAnswerResponse.builder()
                    .sessionId(sessionId)
                    .answeredCount(session.getAnsweredCount())
                    .totalQuestions(session.getQuestionCount())
                    .completed(false)
                    .nextQuestion(toQuestionResponse(nextQuestion.get(), session.getQuestionCount()))
                    .build();
        }

        session.setStatus(STATUS_COMPLETED);
        session.setCompletedAt(now);
        abilityTestSessionRepository.save(session);

        List<AbilityMapResponse> responses = rebuildAbilityMap(session, allQuestions, authorization);
        return AbilityTestAnswerResponse.builder()
                .sessionId(sessionId)
                .answeredCount(session.getAnsweredCount())
                .totalQuestions(session.getQuestionCount())
                .completed(true)
                .abilityMap(responses)
                .build();
    }

    public List<AbilityMapResponse> getAbilityMap(String authorization, UserContext userContext) {
        assertStudent(userContext);

        List<AbilityMapEntity> storedMaps = abilityMapRepository.findByStudentIdOrderByMasteryLevelDescUpdatedAtDesc(userContext.userId());
        Map<Long, ProgressStats> progressStats = buildProgressStats(userContext.userId(), authorization);

        Map<Long, AbilityMapResponse> responseMap = new LinkedHashMap<>();
        for (AbilityMapEntity entity : storedMaps) {
            ProgressStats stats = progressStats.get(entity.getKnowledgePointId());
            double progressScore = stats == null ? entity.getProgressScore() : stats.averageProgress();
            int resourceCount = stats == null ? entity.getResourceCount() : stats.resourceCount();
            double mastery = stats == null
                    ? entity.getMasteryLevel()
                    : blendMastery(entity.getTestScore(), progressScore);
            responseMap.put(entity.getKnowledgePointId(), AbilityMapResponse.builder()
                    .knowledgePointId(entity.getKnowledgePointId())
                    .knowledgePointName(entity.getKnowledgePointName())
                    .knowledgePointPath(entity.getKnowledgePointPath())
                    .masteryLevel(roundScore(mastery))
                    .confidence(entity.getConfidence())
                    .testScore(roundScore(entity.getTestScore()))
                    .progressScore(roundScore(progressScore))
                    .resourceCount(resourceCount)
                    .source(stats == null ? entity.getSource() : "TEST_AND_PROGRESS")
                    .lastTestedAt(entity.getLastTestedAt() == null ? null : entity.getLastTestedAt().toString())
                    .build());
        }

        for (Map.Entry<Long, ProgressStats> entry : progressStats.entrySet()) {
            if (responseMap.containsKey(entry.getKey())) {
                continue;
            }
            ProgressStats stats = entry.getValue();
            responseMap.put(entry.getKey(), AbilityMapResponse.builder()
                    .knowledgePointId(stats.knowledgePointId())
                    .knowledgePointName(stats.knowledgePointName())
                    .knowledgePointPath(stats.knowledgePointPath())
                    .masteryLevel(roundScore(stats.averageProgress()))
                    .confidence(0.35D)
                    .testScore(0D)
                    .progressScore(roundScore(stats.averageProgress()))
                    .resourceCount(stats.resourceCount())
                    .source("LEARNING_PROGRESS")
                    .lastTestedAt(null)
                    .build());
        }

        return responseMap.values().stream()
                .sorted(Comparator.comparing(AbilityMapResponse::getMasteryLevel).reversed()
                        .thenComparing(AbilityMapResponse::getKnowledgePointName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .limit(MAX_RADAR_POINTS)
                .toList();
    }

    public Object getLearningPath(UserContext userContext) {
        assertStudent(userContext);
        return null;
    }

    public Object generateLearningPath(UserContext userContext) {
        assertStudent(userContext);
        return null;
    }

    private List<AbilityMapResponse> rebuildAbilityMap(
            AbilityTestSessionEntity session,
            List<AbilityTestQuestionEntity> questions,
            String authorization) {
        KnowledgePointCatalog catalog = loadKnowledgePointCatalog(authorization);
        Map<Long, ProgressStats> progressStats = buildProgressStats(session.getStudentId(), authorization);

        List<AbilityMapResponse> responses = new ArrayList<>();
        for (AbilityTestQuestionEntity question : questions) {
            double testScore = question.getScore() == null ? 0D : question.getScore();
            CourseKnowledgePointNodeResponse node = catalog.nodesById().get(question.getKnowledgePointId());
            ProgressStats stats = progressStats.get(question.getKnowledgePointId());
            double progressScore = stats == null ? 0D : stats.averageProgress();
            int resourceCount = stats == null ? 0 : stats.resourceCount();
            double confidence = stats == null ? 0.8D : 0.9D;
            String source = stats == null ? "TEST" : "TEST_AND_PROGRESS";
            String knowledgePointName = node == null ? question.getKnowledgePointName() : node.getName();
            String knowledgePointPath = node == null
                    ? (stats == null ? question.getKnowledgePointName() : stats.knowledgePointPath())
                    : node.getPath();

            AbilityMapEntity entity = abilityMapRepository.findByStudentIdAndKnowledgePointId(session.getStudentId(), question.getKnowledgePointId())
                    .orElseGet(() -> AbilityMapEntity.builder()
                            .studentId(session.getStudentId())
                            .knowledgePointId(question.getKnowledgePointId())
                            .build());

            entity.setKnowledgePointName(knowledgePointName);
            entity.setKnowledgePointPath(knowledgePointPath);
            entity.setTestScore(roundScore(testScore));
            entity.setProgressScore(roundScore(progressScore));
            entity.setResourceCount(resourceCount);
            entity.setConfidence(confidence);
            entity.setSource(source);
            entity.setLastTestedAt(session.getCompletedAt());
            entity.setMasteryLevel(roundScore(blendMastery(testScore, progressScore)));
            AbilityMapEntity saved = abilityMapRepository.save(entity);

            responses.add(toAbilityMapResponse(saved));
        }

        return responses.stream()
                .sorted(Comparator.comparing(AbilityMapResponse::getMasteryLevel).reversed()
                        .thenComparing(AbilityMapResponse::getKnowledgePointName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    private AbilityMapResponse toAbilityMapResponse(AbilityMapEntity entity) {
        return AbilityMapResponse.builder()
                .knowledgePointId(entity.getKnowledgePointId())
                .knowledgePointName(entity.getKnowledgePointName())
                .knowledgePointPath(entity.getKnowledgePointPath())
                .masteryLevel(entity.getMasteryLevel())
                .confidence(entity.getConfidence())
                .testScore(entity.getTestScore())
                .progressScore(entity.getProgressScore())
                .resourceCount(entity.getResourceCount())
                .source(entity.getSource())
                .lastTestedAt(entity.getLastTestedAt() == null ? null : entity.getLastTestedAt().toString())
                .build();
    }

    private AbilityTestQuestionResponse toQuestionResponse(AbilityTestQuestionEntity entity, int totalQuestions) {
        return AbilityTestQuestionResponse.builder()
                .id(entity.getId())
                .knowledgePointId(entity.getKnowledgePointId())
                .knowledgePointName(entity.getKnowledgePointName())
                .orderIndex(entity.getDisplayOrder())
                .totalQuestions(totalQuestions)
                .content(entity.getPrompt())
                .options(List.of(
                        AbilityTestQuestionOptionResponse.builder().key("A").text(entity.getOptionA()).build(),
                        AbilityTestQuestionOptionResponse.builder().key("B").text(entity.getOptionB()).build(),
                        AbilityTestQuestionOptionResponse.builder().key("C").text(entity.getOptionC()).build(),
                        AbilityTestQuestionOptionResponse.builder().key("D").text(entity.getOptionD()).build()
                ))
                .build();
    }

    private String buildQuestionPrompt(CourseKnowledgePointNodeResponse point) {
        String path = point.getPath() == null || point.getPath().isBlank() ? point.getName() : point.getPath();
        return "请结合最近的学习情况，判断你对「" + path + "」的掌握程度。";
    }

    private List<CourseKnowledgePointNodeResponse> collectQuestionTargets(CourseKnowledgePointNodeResponse root) {
        List<CourseKnowledgePointNodeResponse> targets = new ArrayList<>();
        collectLeafKnowledgePoints(root, targets);
        if (targets.isEmpty() && isAvailable(root)) {
            targets.add(root);
        }
        return targets;
    }

    private void collectLeafKnowledgePoints(
            CourseKnowledgePointNodeResponse node,
            List<CourseKnowledgePointNodeResponse> collector) {
        List<CourseKnowledgePointNodeResponse> children = node.getChildren() == null
                ? Collections.emptyList()
                : node.getChildren();
        boolean hasChildren = children.stream().anyMatch(this::isAvailable);
        if (!hasChildren && isAvailable(node)) {
            collector.add(node);
            return;
        }
        children.stream()
                .filter(this::isAvailable)
                .forEach(child -> collectLeafKnowledgePoints(child, collector));
    }

    private boolean isAvailable(CourseKnowledgePointNodeResponse node) {
        return node != null && Boolean.TRUE.equals(node.getActive());
    }

    private KnowledgePointCatalog loadKnowledgePointCatalog(String authorization) {
        try {
            CourseApiResponse<List<CourseKnowledgePointNodeResponse>> response =
                    courseServiceClient.listKnowledgePointTree(authorization, true);
            List<CourseKnowledgePointNodeResponse> tree = response == null || response.getData() == null
                    ? Collections.emptyList()
                    : response.getData();
            Map<Long, CourseKnowledgePointNodeResponse> nodesById = new LinkedHashMap<>();
            flattenKnowledgePoints(tree, nodesById);
            return new KnowledgePointCatalog(tree, nodesById);
        } catch (FeignException.Forbidden ex) {
            throw BusinessException.forbidden("No access to knowledge point tree");
        } catch (FeignException.Unauthorized ex) {
            throw BusinessException.unauthorized("Invalid token");
        } catch (FeignException ex) {
            throw BusinessException.badRequest("Failed to load knowledge point tree");
        }
    }

    private void flattenKnowledgePoints(
            Collection<CourseKnowledgePointNodeResponse> nodes,
            Map<Long, CourseKnowledgePointNodeResponse> collector) {
        for (CourseKnowledgePointNodeResponse node : nodes) {
            collector.put(node.getId(), node);
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                flattenKnowledgePoints(node.getChildren(), collector);
            }
        }
    }

    private Map<Long, ProgressStats> buildProgressStats(Long studentId, String authorization) {
        List<LearningProgressEntity> progresses = learningProgressRepository.findByStudentId(studentId);
        if (progresses.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Long> courseIds = progresses.stream()
                .map(LearningProgressEntity::getCourseId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (courseIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, CourseResourceResponse> resources = loadResourcesByCourseIds(courseIds, authorization);
        Map<Long, ProgressStatsAccumulator> accumulators = new LinkedHashMap<>();
        for (LearningProgressEntity progress : progresses) {
            CourseResourceResponse resource = resources.get(progress.getResourceId());
            if (resource == null || resource.getKnowledgePoints() == null) {
                continue;
            }
            double progressScore = clampProgress(progress.getProgress());
            for (CourseResourceKnowledgePointResponse knowledgePoint : resource.getKnowledgePoints()) {
                accumulators.computeIfAbsent(knowledgePoint.getId(), key -> new ProgressStatsAccumulator(
                                knowledgePoint.getId(),
                                knowledgePoint.getName(),
                                knowledgePoint.getPath()))
                        .add(progressScore);
            }
        }

        return accumulators.values().stream()
                .collect(Collectors.toMap(
                        ProgressStatsAccumulator::knowledgePointId,
                        ProgressStatsAccumulator::toStats,
                        (left, _right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<Long, CourseResourceResponse> loadResourcesByCourseIds(Set<Long> courseIds, String authorization) {
        Map<Long, CourseResourceResponse> resources = new LinkedHashMap<>();
        for (Long courseId : courseIds) {
            for (CourseChapterResponse chapter : listCourseChapters(courseId, authorization)) {
                for (CourseResourceResponse resource : listChapterResources(chapter.getId(), authorization)) {
                    resources.put(resource.getId(), resource);
                }
            }
        }
        return resources;
    }

    private List<CourseChapterResponse> listCourseChapters(Long courseId, String authorization) {
        try {
            CourseApiResponse<List<CourseChapterResponse>> response = courseServiceClient.listChapters(authorization, courseId);
            return response == null || response.getData() == null ? Collections.emptyList() : response.getData();
        } catch (FeignException.NotFound ex) {
            throw BusinessException.notFound("Course not found");
        } catch (FeignException.Forbidden ex) {
            throw BusinessException.forbidden("No access to this course");
        } catch (FeignException.Unauthorized ex) {
            throw BusinessException.unauthorized("Invalid token");
        } catch (FeignException ex) {
            return Collections.emptyList();
        }
    }

    private List<CourseResourceResponse> listChapterResources(Long chapterId, String authorization) {
        try {
            CourseApiResponse<List<CourseResourceResponse>> response = courseServiceClient.listResources(authorization, chapterId);
            return response == null || response.getData() == null ? Collections.emptyList() : response.getData();
        } catch (FeignException.Forbidden ex) {
            throw BusinessException.forbidden("No access to this chapter");
        } catch (FeignException.Unauthorized ex) {
            throw BusinessException.unauthorized("Invalid token");
        } catch (FeignException ex) {
            return Collections.emptyList();
        }
    }

    private int resolveTotalResources(Long courseId, String authorization) {
        return listCourseChapters(courseId, authorization).stream()
                .mapToInt(chapter -> listChapterResources(chapter.getId(), authorization).size())
                .sum();
    }

    private void assertStudent(UserContext userContext) {
        if (!"STUDENT".equals(userContext.role())) {
            throw BusinessException.forbidden("Only students can access learning progress");
        }
    }

    private double clampProgress(Double progress) {
        if (progress == null) {
            return 0D;
        }
        return Math.min(1D, Math.max(0D, progress));
    }

    private int sanitizeLastPosition(Integer lastPosition) {
        if (lastPosition == null) {
            return 0;
        }
        return Math.max(0, lastPosition);
    }

    private String normalizeAnswer(String answer) {
        String normalized = answer == null ? "" : answer.trim().toUpperCase(Locale.ROOT);
        if (!ANSWER_SCORES.containsKey(normalized)) {
            throw BusinessException.badRequest("Invalid ability test answer option");
        }
        return normalized;
    }

    private double blendMastery(double testScore, double progressScore) {
        if (progressScore <= 0D) {
            return roundScore(testScore);
        }
        return roundScore((testScore * 0.75D) + (progressScore * 0.25D));
    }

    private double roundScore(double value) {
        double sanitized = Math.min(1D, Math.max(0D, value));
        return Math.round(sanitized * 100D) / 100D;
    }

    private LearningProgressResponse toLearningProgressResponse(LearningProgressEntity entity) {
        return LearningProgressResponse.builder()
                .resourceId(entity.getResourceId())
                .progress(entity.getProgress())
                .lastPosition(entity.getLastPosition())
                .completed(entity.getCompleted())
                .lastAccessedAt(entity.getLastAccessedAt() == null ? null : entity.getLastAccessedAt().toString())
                .build();
    }

    private record KnowledgePointCatalog(
            List<CourseKnowledgePointNodeResponse> tree,
            Map<Long, CourseKnowledgePointNodeResponse> nodesById
    ) {
    }

    private record ProgressStats(
            Long knowledgePointId,
            String knowledgePointName,
            String knowledgePointPath,
            double averageProgress,
            int resourceCount
    ) {
    }

    private static final class ProgressStatsAccumulator {
        private final Long knowledgePointId;
        private final String knowledgePointName;
        private final String knowledgePointPath;
        private double totalProgress;
        private int resourceCount;

        private ProgressStatsAccumulator(Long knowledgePointId, String knowledgePointName, String knowledgePointPath) {
            this.knowledgePointId = knowledgePointId;
            this.knowledgePointName = knowledgePointName;
            this.knowledgePointPath = knowledgePointPath;
        }

        private void add(double progress) {
            totalProgress += progress;
            resourceCount += 1;
        }

        private Long knowledgePointId() {
            return knowledgePointId;
        }

        private ProgressStats toStats() {
            double average = resourceCount == 0 ? 0D : totalProgress / resourceCount;
            return new ProgressStats(knowledgePointId, knowledgePointName, knowledgePointPath, average, resourceCount);
        }
    }
}
