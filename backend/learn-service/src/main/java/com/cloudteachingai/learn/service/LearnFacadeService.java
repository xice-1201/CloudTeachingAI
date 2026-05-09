package com.cloudteachingai.learn.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cloudteachingai.learn.client.CourseApiResponse;
import com.cloudteachingai.learn.client.CourseChapterResponse;
import com.cloudteachingai.learn.client.CourseKnowledgePointNodeResponse;
import com.cloudteachingai.learn.client.CourseResourceKnowledgePointResponse;
import com.cloudteachingai.learn.client.CourseResourceResponse;
import com.cloudteachingai.learn.client.CourseResourceTagResponse;
import com.cloudteachingai.learn.client.CourseServiceClient;
import com.cloudteachingai.learn.client.CourseSummaryResponse;
import com.cloudteachingai.learn.client.PageResponse;
import com.cloudteachingai.learn.controller.LearnController.UserContext;
import com.cloudteachingai.learn.dto.AbilityMapResponse;
import com.cloudteachingai.learn.dto.AbilityTestAnswerRequest;
import com.cloudteachingai.learn.dto.AbilityTestAnswerResponse;
import com.cloudteachingai.learn.dto.AbilityTestQuestionOptionResponse;
import com.cloudteachingai.learn.dto.AbilityTestQuestionResponse;
import com.cloudteachingai.learn.dto.AbilityTestStartRequest;
import com.cloudteachingai.learn.dto.AbilityTestStartResponse;
import com.cloudteachingai.learn.dto.CourseProgressResponse;
import com.cloudteachingai.learn.dto.LearningPathFocusResponse;
import com.cloudteachingai.learn.dto.LearningPathResourceResponse;
import com.cloudteachingai.learn.dto.LearningPathResponse;
import com.cloudteachingai.learn.dto.LearningProgressResponse;
import com.cloudteachingai.learn.dto.TeacherCourseAnalyticsResponse;
import com.cloudteachingai.learn.dto.TeacherDashboardResponse;
import com.cloudteachingai.learn.dto.TeacherKnowledgePointAnalyticsResponse;
import com.cloudteachingai.learn.dto.TeacherStudentRiskResponse;
import com.cloudteachingai.learn.dto.UpdateLearningProgressRequest;
import com.cloudteachingai.learn.event.AbilityUpdatedEvent;
import com.cloudteachingai.learn.event.EventTopics;
import com.cloudteachingai.learn.event.LearningPathGeneratedEvent;
import com.cloudteachingai.learn.event.NotificationSendEvent;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LearnFacadeService {

    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String PATH_STATUS_NOT_STARTED = "NOT_STARTED";
    private static final String PATH_STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String PATH_STATUS_COMPLETED = "COMPLETED";
    private static final String KNOWLEDGE_NODE_TYPE_SUBJECT = "SUBJECT";
    private static final int DEFAULT_QUESTION_LIMIT = 6;
    private static final int MAX_RADAR_POINTS = 8;
    private static final int MAX_PATH_RESOURCES = 6;
    private static final int MAX_PATH_FOCUS_POINTS = 3;
    private static final double WEAK_MASTERY_THRESHOLD = 0.7D;
    private static final Set<String> VALID_ANSWERS = Set.of("A", "B", "C", "D");

    private final LearningProgressRepository learningProgressRepository;
    private final AbilityTestSessionRepository abilityTestSessionRepository;
    private final AbilityTestQuestionRepository abilityTestQuestionRepository;
    private final AbilityMapRepository abilityMapRepository;
    private final CourseServiceClient courseServiceClient;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Value("${ai.ability-test.enabled:true}")
    private boolean abilityTestAiEnabled;

    @Value("${ai.ability-test.api-key:}")
    private String abilityTestAiApiKey;

    @Value("${ai.ability-test.base-url:https://api.deepseek.com}")
    private String abilityTestAiBaseUrl;

    @Value("${ai.ability-test.model:deepseek-chat}")
    private String abilityTestAiModel;

    @Value("${ai.ability-test.timeout-seconds:45}")
    private int abilityTestAiTimeoutSeconds;

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
        boolean wasCompleted = Boolean.TRUE.equals(progress.getCompleted());

        progress.setCourseId(progress.getCourseId() == null ? request.getCourseId() : progress.getCourseId());
        progress.setProgress(Math.max(progress.getProgress(), sanitizedProgress));
        progress.setLastPosition(sanitizedPosition > 0 ? sanitizedPosition : null);
        progress.setLastAccessedAt(now);
        progress.setCompleted(progress.getProgress() >= 0.999D);
        if (Boolean.TRUE.equals(progress.getCompleted()) && progress.getCompletedAt() == null) {
            progress.setCompletedAt(now);
        }

        LearningProgressEntity saved = learningProgressRepository.save(progress);
        if (!wasCompleted && Boolean.TRUE.equals(saved.getCompleted())) {
            List<AbilityMapResponse> updatedAbilities = syncAbilityMapFromCompletedResource(saved, authorization);
            publishAbilityUpdatedEvent(saved.getStudentId(), updatedAbilities, now);
        }
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
        if (!KNOWLEDGE_NODE_TYPE_SUBJECT.equalsIgnoreCase(root.getNodeType())) {
            throw BusinessException.badRequest("Ability test range must be a subject");
        }

        List<CourseKnowledgePointNodeResponse> targets = collectQuestionTargets(root);
        if (targets.isEmpty()) {
            throw BusinessException.badRequest("Selected knowledge point has no available test targets");
        }

        int questionLimit = resolveQuestionLimit(request.getQuestionLimit());
        List<CourseKnowledgePointNodeResponse> sortedTargets = targets.stream()
                .sorted(Comparator.comparing(CourseKnowledgePointNodeResponse::getPath, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(CourseKnowledgePointNodeResponse::getId))
                .toList();
        AbilityQuestionBuildResult questionBuild = buildAdaptiveQuestionSpec(
                root,
                sortedTargets,
                questionLimit,
                1,
                "MEDIUM",
                List.of()
        );
        AbilityQuestionSpec firstQuestion = questionBuild.questions().getFirst();

        AbilityTestSessionEntity session = abilityTestSessionRepository.save(AbilityTestSessionEntity.builder()
                .studentId(userContext.userId())
                .rootKnowledgePointId(root.getId())
                .rootKnowledgePointName(root.getName())
                .status(STATUS_IN_PROGRESS)
                .questionCount(questionLimit)
                .answeredCount(0)
                .startedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());

        AbilityTestQuestionEntity savedQuestion = abilityTestQuestionRepository.save(toQuestionEntity(session.getId(), firstQuestion, 1));

        return AbilityTestStartResponse.builder()
                .sessionId(session.getId())
                .rootKnowledgePointName(root.getName())
                .totalQuestions(questionLimit)
                .question(toQuestionResponse(savedQuestion, questionLimit))
                .generationMode(questionBuild.generationMode())
                .generationMessage(questionBuild.generationMessage())
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
        question.setScore(normalizedAnswer.equals(question.getCorrectAnswer()) ? 1D : 0D);
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
                    .generationMode("PREGENERATED")
                    .generationMessage("Returned an existing unanswered question from this session.")
                    .build();
        }

        if (session.getAnsweredCount() < session.getQuestionCount()) {
            KnowledgePointCatalog catalog = loadKnowledgePointCatalog(authorization);
            CourseKnowledgePointNodeResponse root = catalog.nodesById().get(session.getRootKnowledgePointId());
            if (root == null) {
                throw BusinessException.notFound("Knowledge point not found");
            }
            List<CourseKnowledgePointNodeResponse> targets = collectQuestionTargets(root);
            if (targets.isEmpty()) {
                throw BusinessException.badRequest("Selected knowledge point has no available test targets");
            }
            List<CourseKnowledgePointNodeResponse> sortedTargets = targets.stream()
                    .sorted(Comparator.comparing(CourseKnowledgePointNodeResponse::getPath, Comparator.nullsLast(String::compareToIgnoreCase))
                            .thenComparing(CourseKnowledgePointNodeResponse::getId))
                    .toList();
            int nextOrder = session.getAnsweredCount() + 1;
            String difficulty = decideAdaptiveDifficulty(allQuestions);
            AbilityQuestionBuildResult questionBuild = buildAdaptiveQuestionSpec(
                    root,
                    sortedTargets,
                    session.getQuestionCount(),
                    nextOrder,
                    difficulty,
                    allQuestions
            );
            AbilityTestQuestionEntity generatedQuestion = abilityTestQuestionRepository.save(
                    toQuestionEntity(sessionId, questionBuild.questions().getFirst(), nextOrder)
            );
            abilityTestSessionRepository.save(session);
            return AbilityTestAnswerResponse.builder()
                    .sessionId(sessionId)
                    .answeredCount(session.getAnsweredCount())
                    .totalQuestions(session.getQuestionCount())
                    .completed(false)
                    .nextQuestion(toQuestionResponse(generatedQuestion, session.getQuestionCount()))
                    .generationMode(questionBuild.generationMode())
                    .generationMessage(questionBuild.generationMessage())
                    .build();
        }

        session.setStatus(STATUS_COMPLETED);
        session.setCompletedAt(now);
        abilityTestSessionRepository.save(session);

        List<AbilityMapResponse> responses = rebuildAbilityMap(session, allQuestions, authorization);
        publishAbilityUpdatedEvent(session, responses);
        LearningPathResponse learningPath = buildLearningPath(session.getStudentId(), authorization, responses);
        publishLearningPathGeneratedEvent(session.getStudentId(), learningPath);
        publishLearningPathNotification(session.getStudentId(), learningPath);
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
        return buildAbilityMapResponses(userContext.userId(), authorization);
    }

    public LearningPathResponse getLearningPath(String authorization, UserContext userContext) {
        assertStudent(userContext);
        return buildLearningPath(userContext.userId(), authorization, null);
    }

    public LearningPathResponse generateLearningPath(String authorization, UserContext userContext) {
        assertStudent(userContext);
        LearningPathResponse response = buildLearningPath(userContext.userId(), authorization, null);
        publishLearningPathGeneratedEvent(userContext.userId(), response);
        publishLearningPathNotification(userContext.userId(), response);
        return response;
    }

    public TeacherDashboardResponse getTeacherDashboard(String authorization, UserContext userContext) {
        assertTeacher(userContext);

        List<CourseSummaryResponse> teacherCourses = loadTeacherCourses(authorization);
        if (teacherCourses.isEmpty()) {
            return TeacherDashboardResponse.builder()
                    .totalCourses(0)
                    .publishedCourses(0)
                    .totalResources(0)
                    .activeStudents(0)
                    .averageProgress(0D)
                    .courses(List.of())
                    .weakKnowledgePoints(List.of())
                    .studentRisks(List.of())
                    .build();
        }

        List<Long> courseIds = teacherCourses.stream().map(CourseSummaryResponse::getId).toList();
        Map<Long, CourseContext> courseContexts = loadCourseContexts(new LinkedHashSet<>(courseIds), authorization);
        List<LearningProgressEntity> progresses = learningProgressRepository.findByCourseIdIn(courseIds);

        Map<Long, List<LearningProgressEntity>> progressesByCourse = progresses.stream()
                .collect(Collectors.groupingBy(LearningProgressEntity::getCourseId, LinkedHashMap::new, Collectors.toList()));

        Map<Long, CourseResourceResponse> resourceById = new LinkedHashMap<>();
        courseContexts.values().forEach(context ->
                context.resources().forEach(resource -> resourceById.put(resource.getId(), resource)));

        List<TeacherCourseAnalyticsResponse> courses = teacherCourses.stream()
                .map(course -> toTeacherCourseAnalytics(course, courseContexts.get(course.getId()), progressesByCourse.getOrDefault(course.getId(), List.of())))
                .sorted(Comparator.comparing(TeacherCourseAnalyticsResponse::getAverageProgress).reversed()
                        .thenComparing(TeacherCourseAnalyticsResponse::getCourseTitle, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        List<TeacherKnowledgePointAnalyticsResponse> weakKnowledgePoints =
                buildTeacherKnowledgePointAnalytics(progresses, resourceById).stream()
                        .limit(6)
                        .toList();
        List<TeacherStudentRiskResponse> studentRisks = buildTeacherStudentRisks(
                teacherCourses,
                courseContexts,
                progressesByCourse).stream()
                .limit(8)
                .toList();

        double averageProgress = progresses.isEmpty()
                ? 0D
                : roundScore(progresses.stream()
                .mapToDouble(item -> item.getProgress() == null ? 0D : item.getProgress())
                .average()
                .orElse(0D));

        return TeacherDashboardResponse.builder()
                .totalCourses(teacherCourses.size())
                .publishedCourses((int) teacherCourses.stream().filter(course -> "PUBLISHED".equals(course.getStatus())).count())
                .totalResources(courseContexts.values().stream().mapToInt(context -> context.resources().size()).sum())
                .activeStudents((int) progresses.stream().map(LearningProgressEntity::getStudentId).distinct().count())
                .averageProgress(averageProgress)
                .courses(courses)
                .weakKnowledgePoints(weakKnowledgePoints)
                .studentRisks(studentRisks)
                .build();
    }

    private List<AbilityMapResponse> rebuildAbilityMap(
            AbilityTestSessionEntity session,
            List<AbilityTestQuestionEntity> questions,
            String authorization) {
        KnowledgePointCatalog catalog = loadKnowledgePointCatalog(authorization);
        Map<Long, ProgressStats> progressStats = buildProgressStats(session.getStudentId(), authorization);

        Map<Long, List<AbilityTestQuestionEntity>> questionsByKnowledgePoint = questions.stream()
                .collect(Collectors.groupingBy(
                        AbilityTestQuestionEntity::getKnowledgePointId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<AbilityMapResponse> responses = new ArrayList<>();
        for (Map.Entry<Long, List<AbilityTestQuestionEntity>> entry : questionsByKnowledgePoint.entrySet()) {
            Long knowledgePointId = entry.getKey();
            List<AbilityTestQuestionEntity> pointQuestions = entry.getValue();
            AbilityTestQuestionEntity firstQuestion = pointQuestions.getFirst();
            double testScore = pointQuestions.stream()
                    .mapToDouble(question -> question.getScore() == null ? 0D : question.getScore())
                    .average()
                    .orElse(0D);
            CourseKnowledgePointNodeResponse node = catalog.nodesById().get(knowledgePointId);
            ProgressStats stats = progressStats.get(knowledgePointId);
            double progressScore = stats == null ? 0D : stats.averageProgress();
            int resourceCount = stats == null ? 0 : stats.resourceCount();
            double confidence = stats == null ? 0.8D : 0.9D;
            String source = stats == null ? "TEST" : "TEST_AND_PROGRESS";
            String knowledgePointName = node == null ? firstQuestion.getKnowledgePointName() : node.getName();
            String knowledgePointPath = node == null
                    ? (stats == null ? firstQuestion.getKnowledgePointName() : stats.knowledgePointPath())
                    : node.getPath();

            AbilityMapEntity entity = abilityMapRepository.findByStudentIdAndKnowledgePointId(session.getStudentId(), knowledgePointId)
                    .orElseGet(() -> AbilityMapEntity.builder()
                            .studentId(session.getStudentId())
                            .knowledgePointId(knowledgePointId)
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

    private List<AbilityMapResponse> buildAbilityMapResponses(Long studentId, String authorization) {
        List<AbilityMapEntity> storedMaps = abilityMapRepository.findByStudentIdOrderByMasteryLevelDescUpdatedAtDesc(studentId);
        Map<Long, ProgressStats> progressStats = buildProgressStats(studentId, authorization);

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
                .toList();
    }

    private LearningPathResponse buildLearningPath(
            Long studentId,
            String authorization,
            List<AbilityMapResponse> freshAbilityMap) {
        Map<Long, LearningProgressEntity> progressByResourceId = learningProgressRepository.findByStudentId(studentId).stream()
                .collect(Collectors.toMap(
                        LearningProgressEntity::getResourceId,
                        entity -> entity,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Set<Long> courseIds = collectCandidateCourseIds(progressByResourceId.values(), authorization);
        if (courseIds.isEmpty()) {
            return null;
        }

        Map<Long, CourseContext> courseContexts = loadCourseContexts(courseIds, authorization);
        List<AbilityMapResponse> focusCandidates = resolveLearningPathFocusCandidates(
                studentId,
                authorization,
                freshAbilityMap,
                progressByResourceId,
                courseContexts);
        if (focusCandidates.isEmpty()) {
            return null;
        }

        List<AbilityMapResponse> focusPoints = selectFocusKnowledgePoints(focusCandidates);
        List<PathCandidate> candidates = buildPathCandidates(focusPoints, progressByResourceId, courseContexts);
        if (candidates.isEmpty()) {
            focusPoints = selectFocusKnowledgePoints(buildCourseTagFocusCandidates(progressByResourceId, courseContexts).stream()
                    .sorted(Comparator.comparing(AbilityMapResponse::getMasteryLevel)
                            .thenComparing(AbilityMapResponse::getKnowledgePointName, Comparator.nullsLast(String::compareToIgnoreCase)))
                    .toList());
            candidates = buildPathCandidates(focusPoints, progressByResourceId, courseContexts);
            if (candidates.isEmpty()) {
                return null;
            }
        }

        List<LearningPathResourceResponse> resources = new ArrayList<>();
        for (int index = 0; index < Math.min(MAX_PATH_RESOURCES, candidates.size()); index++) {
            PathCandidate candidate = candidates.get(index);
            resources.add(LearningPathResourceResponse.builder()
                    .resourceId(candidate.resourceId())
                    .courseId(candidate.courseId())
                    .chapterId(candidate.chapterId())
                    .resourceTitle(candidate.resourceTitle())
                    .chapterTitle(candidate.chapterTitle())
                    .courseTitle(candidate.courseTitle())
                    .reason(candidate.reason())
                    .orderIndex(index + 1)
                    .currentProgress(roundScore(candidate.currentProgress()))
                    .learningStatus(resolvePathStatus(candidate.currentProgress()))
                    .statusLabel(resolvePathStatusLabel(candidate.currentProgress()))
                    .actionLabel(resolvePathActionLabel(candidate.currentProgress()))
                    .recommendationScore(roundScore(candidate.score()))
                    .focusKnowledgePointId(candidate.focusKnowledgePointId())
                    .focusKnowledgePointName(candidate.focusKnowledgePointName())
                    .build());
        }

        return LearningPathResponse.builder()
                .studentId(studentId)
                .generatedAt(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .focusKnowledgePoints(focusPoints.stream()
                        .map(item -> LearningPathFocusResponse.builder()
                                .knowledgePointId(item.getKnowledgePointId())
                                .knowledgePointName(item.getKnowledgePointName())
                                .knowledgePointPath(item.getKnowledgePointPath())
                                .masteryLevel(item.getMasteryLevel())
                                .build())
                        .toList())
                .resources(resources)
                .build();
    }

    private List<AbilityMapResponse> resolveLearningPathFocusCandidates(
            Long studentId,
            String authorization,
            List<AbilityMapResponse> freshAbilityMap,
            Map<Long, LearningProgressEntity> progressByResourceId,
            Map<Long, CourseContext> courseContexts) {
        List<AbilityMapResponse> abilityMap = freshAbilityMap == null || freshAbilityMap.isEmpty()
                ? buildAbilityMapResponses(studentId, authorization)
                : freshAbilityMap;
        if (abilityMap != null && !abilityMap.isEmpty()) {
            return abilityMap.stream()
                    .sorted(Comparator.comparing(AbilityMapResponse::getMasteryLevel)
                            .thenComparing(AbilityMapResponse::getKnowledgePointName, Comparator.nullsLast(String::compareToIgnoreCase)))
                    .toList();
        }

        return buildCourseTagFocusCandidates(progressByResourceId, courseContexts).stream()
                .sorted(Comparator.comparing(AbilityMapResponse::getMasteryLevel)
                        .thenComparing(AbilityMapResponse::getKnowledgePointName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    private List<AbilityMapResponse> buildCourseTagFocusCandidates(
            Map<Long, LearningProgressEntity> progressByResourceId,
            Map<Long, CourseContext> courseContexts) {
        Map<Long, PathFocusAccumulator> accumulators = new LinkedHashMap<>();
        for (CourseContext context : courseContexts.values()) {
            for (CourseResourceResponse resource : context.resources()) {
                if (resource.getKnowledgePoints() == null || resource.getKnowledgePoints().isEmpty()) {
                    continue;
                }
                LearningProgressEntity progress = progressByResourceId.get(resource.getId());
                double progressValue = progress == null ? 0D : clampProgress(progress.getProgress());
                for (CourseResourceKnowledgePointResponse knowledgePoint : resource.getKnowledgePoints()) {
                    accumulators.computeIfAbsent(
                                    knowledgePoint.getId(),
                                    key -> new PathFocusAccumulator(
                                            knowledgePoint.getId(),
                                            knowledgePoint.getName(),
                                            knowledgePoint.getPath()))
                            .add(progressValue, progress != null);
                }
            }
        }

        return accumulators.values().stream()
                .map(PathFocusAccumulator::toAbilityMapResponse)
                .toList();
    }

    private void publishAbilityUpdatedEvent(
            AbilityTestSessionEntity session,
            List<AbilityMapResponse> responses) {
        outboxService.enqueue(EventTopics.ABILITY_UPDATED, AbilityUpdatedEvent.builder()
                .studentId(session.getStudentId())
                .sessionId(session.getId())
                .knowledgePointIds(responses.stream()
                        .map(AbilityMapResponse::getKnowledgePointId)
                        .filter(Objects::nonNull)
                        .toList())
                .updatedAt(session.getCompletedAt() == null ? null : session.getCompletedAt().toString())
                .build());
    }

    private void publishAbilityUpdatedEvent(
            Long studentId,
            List<AbilityMapResponse> responses,
            OffsetDateTime updatedAt) {
        if (responses == null || responses.isEmpty()) {
            return;
        }

        outboxService.enqueue(EventTopics.ABILITY_UPDATED, AbilityUpdatedEvent.builder()
                .studentId(studentId)
                .sessionId(null)
                .knowledgePointIds(responses.stream()
                        .map(AbilityMapResponse::getKnowledgePointId)
                        .filter(Objects::nonNull)
                        .toList())
                .updatedAt(updatedAt == null ? null : updatedAt.toString())
                .build());
    }

    private void publishLearningPathGeneratedEvent(Long studentId, LearningPathResponse response) {
        if (response == null) {
            return;
        }

        outboxService.enqueue(EventTopics.LEARNING_PATH_GENERATED, LearningPathGeneratedEvent.builder()
                .studentId(studentId)
                .generatedAt(response.getGeneratedAt())
                .focusKnowledgePointIds(response.getFocusKnowledgePoints().stream()
                        .map(LearningPathFocusResponse::getKnowledgePointId)
                        .filter(Objects::nonNull)
                        .toList())
                .resourceIds(response.getResources().stream()
                        .map(LearningPathResourceResponse::getResourceId)
                        .filter(Objects::nonNull)
                        .toList())
                .build());
    }

    private void publishLearningPathNotification(Long studentId, LearningPathResponse response) {
        if (response == null || response.getResources() == null || response.getResources().isEmpty()) {
            return;
        }

        String firstResourceTitle = response.getResources().getFirst().getResourceTitle();
        outboxService.enqueue(EventTopics.NOTIFICATION_SEND, NotificationSendEvent.builder()
                .userId(studentId)
                .type("COURSE")
                .title("学习路线已更新")
                .content("系统已为你生成新的学习路线，建议先学习《" + firstResourceTitle + "》。")
                .build());
    }

    private List<CourseSummaryResponse> loadTeacherCourses(String authorization) {
        try {
            CourseApiResponse<PageResponse<CourseSummaryResponse>> response =
                    courseServiceClient.listCourses(authorization, 1, 100);
            PageResponse<CourseSummaryResponse> page = response == null ? null : response.getData();
            return page == null || page.getItems() == null ? List.of() : page.getItems();
        } catch (FeignException.Forbidden ex) {
            throw BusinessException.forbidden("No access to teacher course list");
        } catch (FeignException.Unauthorized ex) {
            throw BusinessException.unauthorized("Invalid token");
        } catch (FeignException ex) {
            throw BusinessException.badRequest("Failed to load teacher courses");
        }
    }

    private TeacherCourseAnalyticsResponse toTeacherCourseAnalytics(
            CourseSummaryResponse course,
            CourseContext context,
            List<LearningProgressEntity> progresses) {
        int totalResources = context == null ? 0 : context.resources().size();
        double averageProgress = progresses.isEmpty()
                ? 0D
                : roundScore(progresses.stream()
                .mapToDouble(item -> item.getProgress() == null ? 0D : item.getProgress())
                .average()
                .orElse(0D));
        double completionRate = progresses.isEmpty()
                ? 0D
                : roundScore(progresses.stream().filter(item -> Boolean.TRUE.equals(item.getCompleted())).count() / (double) progresses.size());
        String lastLearnedAt = progresses.stream()
                .map(LearningProgressEntity::getLastAccessedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .map(OffsetDateTime::toString)
                .orElse(null);

        Map<Long, Long> learningCountByResource = progresses.stream()
                .collect(Collectors.groupingBy(LearningProgressEntity::getResourceId, LinkedHashMap::new, Collectors.counting()));
        Long hottestResourceId = learningCountByResource.entrySet().stream()
                .max(Map.Entry.<Long, Long>comparingByValue()
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .orElse(null);
        String hottestResourceTitle = null;
        int hottestResourceLearningCount = 0;
        if (hottestResourceId != null && context != null) {
            hottestResourceLearningCount = learningCountByResource.getOrDefault(hottestResourceId, 0L).intValue();
            hottestResourceTitle = context.resources().stream()
                    .filter(resource -> Objects.equals(resource.getId(), hottestResourceId))
                    .map(CourseResourceResponse::getTitle)
                    .findFirst()
                    .orElse(null);
        }

        return TeacherCourseAnalyticsResponse.builder()
                .courseId(course.getId())
                .courseTitle(course.getTitle())
                .courseStatus(course.getStatus())
                .totalResources(totalResources)
                .activeStudents((int) progresses.stream().map(LearningProgressEntity::getStudentId).distinct().count())
                .averageProgress(averageProgress)
                .completionRate(completionRate)
                .learningRecordCount(progresses.size())
                .hottestResourceTitle(hottestResourceTitle)
                .hottestResourceLearningCount(hottestResourceLearningCount)
                .lastLearnedAt(lastLearnedAt)
                .build();
    }

    private List<TeacherKnowledgePointAnalyticsResponse> buildTeacherKnowledgePointAnalytics(
            List<LearningProgressEntity> progresses,
            Map<Long, CourseResourceResponse> resourceById) {
        Map<Long, KnowledgePointAnalyticsAccumulator> accumulators = new LinkedHashMap<>();
        for (LearningProgressEntity progress : progresses) {
            CourseResourceResponse resource = resourceById.get(progress.getResourceId());
            if (resource == null || resource.getKnowledgePoints() == null) {
                continue;
            }
            double progressValue = clampProgress(progress.getProgress());
            for (CourseResourceKnowledgePointResponse knowledgePoint : resource.getKnowledgePoints()) {
                accumulators.computeIfAbsent(
                                knowledgePoint.getId(),
                                key -> new KnowledgePointAnalyticsAccumulator(
                                        knowledgePoint.getId(),
                                        knowledgePoint.getName(),
                                        knowledgePoint.getPath()))
                        .add(progressValue, progress.getStudentId(), resource.getId());
            }
        }

        return accumulators.values().stream()
                .map(KnowledgePointAnalyticsAccumulator::toResponse)
                .sorted(Comparator.comparing(TeacherKnowledgePointAnalyticsResponse::getAverageProgress)
                        .thenComparing(TeacherKnowledgePointAnalyticsResponse::getKnowledgePointName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    private List<TeacherStudentRiskResponse> buildTeacherStudentRisks(
            List<CourseSummaryResponse> teacherCourses,
            Map<Long, CourseContext> courseContexts,
            Map<Long, List<LearningProgressEntity>> progressesByCourse) {
        OffsetDateTime inactiveBefore = OffsetDateTime.now(ZoneOffset.UTC).minusDays(14);
        return teacherCourses.stream()
                .map(course -> toTeacherStudentRisk(
                        course,
                        courseContexts.get(course.getId()),
                        progressesByCourse.getOrDefault(course.getId(), List.of()),
                        inactiveBefore))
                .sorted(Comparator.comparing(this::riskWeight).reversed()
                        .thenComparing(TeacherStudentRiskResponse::getCourseTitle, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    private TeacherStudentRiskResponse toTeacherStudentRisk(
            CourseSummaryResponse course,
            CourseContext context,
            List<LearningProgressEntity> progresses,
            OffsetDateTime inactiveBefore) {
        int totalResources = Math.max(context == null ? 0 : context.resources().size(), 1);
        Map<Long, List<LearningProgressEntity>> progressesByStudent = progresses.stream()
                .collect(Collectors.groupingBy(LearningProgressEntity::getStudentId, LinkedHashMap::new, Collectors.toList()));

        int lowProgressStudents = 0;
        int inactiveStudents = 0;
        int completedStudents = 0;
        for (List<LearningProgressEntity> studentProgresses : progressesByStudent.values()) {
            double totalProgress = studentProgresses.stream()
                    .mapToDouble(item -> item.getProgress() == null ? 0D : item.getProgress())
                    .sum();
            double courseProgress = Math.min(1D, totalProgress / totalResources);
            OffsetDateTime lastLearnedAt = studentProgresses.stream()
                    .map(LearningProgressEntity::getLastAccessedAt)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(null);

            if (courseProgress < 0.3D) {
                lowProgressStudents += 1;
            }
            if (lastLearnedAt == null || lastLearnedAt.isBefore(inactiveBefore)) {
                inactiveStudents += 1;
            }
            if (courseProgress >= 0.999D) {
                completedStudents += 1;
            }
        }

        int activeStudents = progressesByStudent.size();
        String riskLevel = resolveRiskLevel(activeStudents, lowProgressStudents, inactiveStudents);
        return TeacherStudentRiskResponse.builder()
                .courseId(course.getId())
                .courseTitle(course.getTitle())
                .activeStudents(activeStudents)
                .lowProgressStudents(lowProgressStudents)
                .inactiveStudents(inactiveStudents)
                .completedStudents(completedStudents)
                .riskLevel(riskLevel)
                .insight(buildRiskInsight(activeStudents, lowProgressStudents, inactiveStudents, completedStudents))
                .build();
    }

    private String resolveRiskLevel(int activeStudents, int lowProgressStudents, int inactiveStudents) {
        if (activeStudents == 0) {
            return "NO_DATA";
        }
        double riskRatio = (lowProgressStudents + inactiveStudents) / (double) (activeStudents * 2);
        if (riskRatio >= 0.45D) {
            return "HIGH";
        }
        if (riskRatio >= 0.2D) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String buildRiskInsight(int activeStudents, int lowProgressStudents, int inactiveStudents, int completedStudents) {
        if (activeStudents == 0) {
            return "暂无学生学习记录，建议先发布学习资源或提醒学生进入课程。";
        }
        if (lowProgressStudents == 0 && inactiveStudents == 0) {
            return "当前学习状态稳定，可继续关注知识点薄弱趋势。";
        }
        List<String> insights = new ArrayList<>();
        if (lowProgressStudents > 0) {
            insights.add(lowProgressStudents + " 名学生整体进度偏低");
        }
        if (inactiveStudents > 0) {
            insights.add(inactiveStudents + " 名学生超过 14 天未学习");
        }
        if (completedStudents > 0) {
            insights.add(completedStudents + " 名学生已接近完成");
        }
        return String.join("，", insights) + "。";
    }

    private int riskWeight(TeacherStudentRiskResponse risk) {
        return switch (risk.getRiskLevel()) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
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

    private int resolveQuestionLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_QUESTION_LIMIT;
        }
        return Math.max(2, Math.min(10, requestedLimit));
    }

    private AbilityTestQuestionEntity toQuestionEntity(Long sessionId, AbilityQuestionSpec spec, int displayOrder) {
        return AbilityTestQuestionEntity.builder()
                .sessionId(sessionId)
                .knowledgePointId(spec.point().getId())
                .knowledgePointName(spec.point().getName())
                .prompt(spec.prompt())
                .optionA(spec.optionA())
                .optionB(spec.optionB())
                .optionC(spec.optionC())
                .optionD(spec.optionD())
                .correctAnswer(spec.correctAnswer())
                .explanation(spec.explanation())
                .displayOrder(displayOrder)
                .answered(false)
                .build();
    }

    private String decideAdaptiveDifficulty(List<AbilityTestQuestionEntity> answeredQuestions) {
        List<AbilityTestQuestionEntity> scoredQuestions = answeredQuestions.stream()
                .filter(item -> Boolean.TRUE.equals(item.getAnswered()))
                .filter(item -> item.getScore() != null)
                .sorted(Comparator.comparing(AbilityTestQuestionEntity::getDisplayOrder))
                .toList();
        if (scoredQuestions.isEmpty()) {
            return "MEDIUM";
        }
        double averageScore = scoredQuestions.stream()
                .mapToDouble(AbilityTestQuestionEntity::getScore)
                .average()
                .orElse(0D);
        long recentWrongCount = scoredQuestions.stream()
                .skip(Math.max(0, scoredQuestions.size() - 2))
                .filter(item -> item.getScore() < 0.5D)
                .count();
        if (recentWrongCount >= 2 || averageScore < 0.45D) {
            return "EASY";
        }
        if (averageScore >= 0.75D) {
            return "HARD";
        }
        return "MEDIUM";
    }

    private AbilityQuestionBuildResult buildAdaptiveQuestionSpec(
            CourseKnowledgePointNodeResponse root,
            List<CourseKnowledgePointNodeResponse> targets,
            int totalQuestionCount,
            int questionNumber,
            String difficulty,
            List<AbilityTestQuestionEntity> answeredQuestions) {
        CourseKnowledgePointNodeResponse target = targets.get(Math.floorMod(questionNumber - 1, targets.size()));
        AiQuestionGenerationResult aiResult = requestAdaptiveAiQuestionSpec(
                root,
                targets,
                target,
                totalQuestionCount,
                questionNumber,
                difficulty,
                answeredQuestions
        );
        if (!aiResult.questions().isEmpty()) {
            return new AbilityQuestionBuildResult(
                    List.of(aiResult.questions().getFirst()),
                    "AI",
                    "Adaptive AI generated question " + questionNumber + "/" + totalQuestionCount
                            + " with difficulty=" + difficulty + ". " + aiResult.message()
            );
        }
        return new AbilityQuestionBuildResult(
                List.of(buildAdaptiveFallbackQuestionSpec(target, difficulty, questionNumber)),
                "FALLBACK_RULE",
                "Adaptive rule fallback generated question " + questionNumber + "/" + totalQuestionCount
                        + " with difficulty=" + difficulty + ". " + aiResult.message()
        );
    }

    private AbilityQuestionBuildResult buildQuestionSpecs(
            CourseKnowledgePointNodeResponse root,
            List<CourseKnowledgePointNodeResponse> targets,
            int questionLimit) {
        AiQuestionGenerationResult aiResult = requestAiQuestionSpecs(root, targets, questionLimit);
        List<AbilityQuestionSpec> aiQuestions = aiResult.questions();
        if (aiQuestions.size() >= questionLimit) {
            return new AbilityQuestionBuildResult(
                    aiQuestions.stream().limit(questionLimit).toList(),
                    "AI",
                    "AI generated " + questionLimit + " valid questions."
            );
        }

        List<AbilityQuestionSpec> specs = new ArrayList<>();
        specs.addAll(aiQuestions);
        for (int index = 0; index < questionLimit; index++) {
            if (specs.size() >= questionLimit) {
                break;
            }
            CourseKnowledgePointNodeResponse point = targets.get(index % targets.size());
            specs.add(buildQuestionSpec(point, index % 6));
        }
        if (aiQuestions.isEmpty()) {
            return new AbilityQuestionBuildResult(
                    specs,
                    "FALLBACK_RULE",
                    aiResult.message()
            );
        }
        return new AbilityQuestionBuildResult(
                specs,
                "MIXED",
                "AI generated " + aiQuestions.size() + " valid questions; rule fallback filled "
                        + (questionLimit - aiQuestions.size()) + " remaining questions. " + aiResult.message()
        );
    }

    private AiQuestionGenerationResult requestAiQuestionSpecs(
            CourseKnowledgePointNodeResponse root,
            List<CourseKnowledgePointNodeResponse> targets,
            int questionLimit) {
        if (!abilityTestAiEnabled) {
            return new AiQuestionGenerationResult(List.of(), "AI ability test generation is disabled by AI_ABILITY_TEST_ENABLED=false.");
        }
        if (normalizeBlank(abilityTestAiApiKey) == null) {
            return new AiQuestionGenerationResult(List.of(), "AI ability test API key is missing. Set DEEPSEEK_API_KEY or OPENAI_API_KEY for learn-service.");
        }

        try {
            Map<Long, CourseKnowledgePointNodeResponse> targetById = targets.stream()
                    .collect(LinkedHashMap::new, (map, item) -> map.put(item.getId(), item), Map::putAll);
            Map<String, Object> payload = Map.of(
                    "model", abilityTestAiModel,
                    "temperature", 0.35,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of(
                                    "role", "system",
                                    "content", """
                                            You generate objective multiple-choice diagnostic questions for students.
                                            Return JSON only: {"questions":[{"knowledgePointId":1,"prompt":"...","options":{"A":"...","B":"...","C":"...","D":"..."},"correctAnswer":"A","explanation":"..."}]}.
                                            Requirements:
                                            - Use the same language as the knowledge point content, usually Chinese.
                                            - Questions must test knowledge, understanding, application, misconception, or transfer, not ask students to self-rate.
                                            - Exactly one option must be correct.
                                            - Use only A/B/C/D as option keys and correctAnswer.
                                            - Prefer concrete, course-relevant wording based on name, path, description, and keywords.
                                            - Do not reveal the answer in the prompt.
                                            - Generate the requested number of questions.
                                            """
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", objectMapper.writeValueAsString(Map.of(
                                            "requestedQuestionCount", questionLimit,
                                            "rootKnowledgePoint", toAiKnowledgePoint(root),
                                            "candidateKnowledgePoints", targets.stream()
                                                    .map(this::toAiKnowledgePoint)
                                                    .toList()
                                    ))
                            )
                    )
            );

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(abilityTestAiBaseUrl) + "/chat/completions"))
                    .timeout(Duration.ofSeconds(Math.max(5, abilityTestAiTimeoutSeconds)))
                    .header("Authorization", "Bearer " + abilityTestAiApiKey)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("AI ability test generation failed: status={}, body={}", response.statusCode(), abbreviate(response.body(), 1000));
                return new AiQuestionGenerationResult(
                        List.of(),
                        "AI ability test request failed with HTTP " + response.statusCode() + ". Body: " + abbreviate(response.body(), 300)
                );
            }

            JsonNode rootNode = objectMapper.readTree(response.body());
            String content = rootNode.path("choices").path(0).path("message").path("content").asText("");
            if (normalizeBlank(content) == null) {
                return new AiQuestionGenerationResult(List.of(), "AI ability test response did not contain choices[0].message.content.");
            }
            JsonNode parsed = objectMapper.readTree(content);
            JsonNode questions = parsed.path("questions");
            if (!questions.isArray()) {
                return new AiQuestionGenerationResult(List.of(), "AI ability test response JSON did not contain a questions array.");
            }

            List<AbilityQuestionSpec> specs = new ArrayList<>();
            for (JsonNode question : questions) {
                AbilityQuestionSpec spec = toAiQuestionSpec(question, targetById);
                if (spec != null) {
                    specs.add(spec);
                }
                if (specs.size() >= questionLimit) {
                    break;
                }
            }
            if (specs.isEmpty()) {
                return new AiQuestionGenerationResult(List.of(), "AI returned questions, but none passed validation.");
            }
            return new AiQuestionGenerationResult(specs, "AI returned " + specs.size() + " valid questions.");
        } catch (Exception ex) {
            log.warn("AI ability test generation failed, fallback to rule questions", ex);
            return new AiQuestionGenerationResult(
                    List.of(),
                    "AI ability test generation threw " + ex.getClass().getSimpleName() + ": " + Optional.ofNullable(ex.getMessage()).orElse("")
            );
        }
    }

    private AiQuestionGenerationResult requestAdaptiveAiQuestionSpec(
            CourseKnowledgePointNodeResponse root,
            List<CourseKnowledgePointNodeResponse> targets,
            CourseKnowledgePointNodeResponse target,
            int totalQuestionCount,
            int questionNumber,
            String difficulty,
            List<AbilityTestQuestionEntity> answeredQuestions) {
        if (!abilityTestAiEnabled) {
            return new AiQuestionGenerationResult(List.of(), "AI ability test generation is disabled by AI_ABILITY_TEST_ENABLED=false.");
        }
        if (normalizeBlank(abilityTestAiApiKey) == null) {
            return new AiQuestionGenerationResult(List.of(), "AI ability test API key is missing. Set DEEPSEEK_API_KEY or OPENAI_API_KEY for learn-service.");
        }

        try {
            Map<Long, CourseKnowledgePointNodeResponse> targetById = targets.stream()
                    .collect(LinkedHashMap::new, (map, item) -> map.put(item.getId(), item), Map::putAll);
            Map<String, Object> payload = Map.of(
                    "model", abilityTestAiModel,
                    "temperature", switch (difficulty) {
                        case "EASY" -> 0.2;
                        case "HARD" -> 0.45;
                        default -> 0.3;
                    },
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of(
                                    "role", "system",
                                    "content", """
                                            You generate exactly one adaptive objective multiple-choice diagnostic question for a student.
                                            Return JSON only: {"questions":[{"knowledgePointId":1,"prompt":"...","options":{"A":"...","B":"...","C":"...","D":"..."},"correctAnswer":"A","explanation":"..."}]}.
                                            Requirements:
                                            - Use the same language as the knowledge point content, usually Chinese.
                                            - The requested difficulty is mandatory: EASY tests basic recognition, MEDIUM tests understanding/application, HARD tests transfer, edge cases, or misconception diagnosis.
                                            - The first question of a session is always MEDIUM.
                                            - Questions after the first must reflect the student's previous answers and cumulative score trend.
                                            - Do not ask students to self-rate their mastery.
                                            - Exactly one option must be correct.
                                            - Use only A/B/C/D as option keys and correctAnswer.
                                            - Do not reveal the answer in the prompt.
                                            """
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", objectMapper.writeValueAsString(Map.of(
                                            "questionNumber", questionNumber,
                                            "totalQuestionCount", totalQuestionCount,
                                            "requestedDifficulty", difficulty,
                                            "rootKnowledgePoint", toAiKnowledgePoint(root),
                                            "targetKnowledgePoint", toAiKnowledgePoint(target),
                                            "candidateKnowledgePoints", targets.stream()
                                                    .map(this::toAiKnowledgePoint)
                                                    .toList(),
                                            "previousAnswers", toAiAnswerHistory(answeredQuestions),
                                            "scoreSummary", toAiScoreSummary(answeredQuestions)
                                    ))
                            )
                    )
            );

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(abilityTestAiBaseUrl) + "/chat/completions"))
                    .timeout(Duration.ofSeconds(Math.max(5, abilityTestAiTimeoutSeconds)))
                    .header("Authorization", "Bearer " + abilityTestAiApiKey)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Adaptive AI ability test generation failed: status={}, body={}", response.statusCode(), abbreviate(response.body(), 1000));
                return new AiQuestionGenerationResult(
                        List.of(),
                        "AI ability test request failed with HTTP " + response.statusCode() + ". Body: " + abbreviate(response.body(), 300)
                );
            }

            JsonNode rootNode = objectMapper.readTree(response.body());
            String content = rootNode.path("choices").path(0).path("message").path("content").asText("");
            if (normalizeBlank(content) == null) {
                return new AiQuestionGenerationResult(List.of(), "AI ability test response did not contain choices[0].message.content.");
            }
            JsonNode parsed = objectMapper.readTree(content);
            JsonNode questions = parsed.path("questions");
            if (!questions.isArray()) {
                return new AiQuestionGenerationResult(List.of(), "AI ability test response JSON did not contain a questions array.");
            }
            for (JsonNode question : questions) {
                AbilityQuestionSpec spec = toAiQuestionSpec(question, targetById);
                if (spec != null) {
                    return new AiQuestionGenerationResult(List.of(spec), "AI returned 1 valid adaptive question.");
                }
            }
            return new AiQuestionGenerationResult(List.of(), "AI returned questions, but none passed validation.");
        } catch (Exception ex) {
            log.warn("Adaptive AI ability test generation failed, fallback to rule question", ex);
            return new AiQuestionGenerationResult(
                    List.of(),
                    "AI ability test generation threw " + ex.getClass().getSimpleName() + ": " + Optional.ofNullable(ex.getMessage()).orElse("")
            );
        }
    }

    private List<Map<String, Object>> toAiAnswerHistory(List<AbilityTestQuestionEntity> answeredQuestions) {
        return answeredQuestions.stream()
                .filter(item -> Boolean.TRUE.equals(item.getAnswered()))
                .sorted(Comparator.comparing(AbilityTestQuestionEntity::getDisplayOrder))
                .map(item -> Map.<String, Object>of(
                        "questionNumber", item.getDisplayOrder(),
                        "knowledgePointId", item.getKnowledgePointId(),
                        "knowledgePointName", item.getKnowledgePointName(),
                        "selectedAnswer", Optional.ofNullable(item.getSelectedAnswer()).orElse(""),
                        "correctAnswer", item.getCorrectAnswer(),
                        "score", Optional.ofNullable(item.getScore()).orElse(0D)
                ))
                .toList();
    }

    private Map<String, Object> toAiScoreSummary(List<AbilityTestQuestionEntity> answeredQuestions) {
        List<AbilityTestQuestionEntity> scoredQuestions = answeredQuestions.stream()
                .filter(item -> Boolean.TRUE.equals(item.getAnswered()))
                .filter(item -> item.getScore() != null)
                .toList();
        int answeredCount = scoredQuestions.size();
        double correctCount = scoredQuestions.stream().mapToDouble(AbilityTestQuestionEntity::getScore).sum();
        double accuracy = answeredCount == 0 ? 0D : correctCount / answeredCount;
        return Map.of(
                "answeredCount", answeredCount,
                "correctCount", correctCount,
                "accuracy", accuracy
        );
    }

    private Map<String, Object> toAiKnowledgePoint(CourseKnowledgePointNodeResponse point) {
        return Map.of(
                "id", point.getId(),
                "name", Optional.ofNullable(point.getName()).orElse(""),
                "path", Optional.ofNullable(point.getPath()).orElse(""),
                "description", Optional.ofNullable(point.getDescription()).orElse(""),
                "keywords", Optional.ofNullable(point.getKeywords()).orElse(""),
                "nodeType", Optional.ofNullable(point.getNodeType()).orElse("")
        );
    }

    private AbilityQuestionSpec toAiQuestionSpec(
            JsonNode question,
            Map<Long, CourseKnowledgePointNodeResponse> targetById) {
        Long knowledgePointId = question.path("knowledgePointId").canConvertToLong()
                ? question.path("knowledgePointId").asLong()
                : null;
        CourseKnowledgePointNodeResponse point = knowledgePointId == null ? null : targetById.get(knowledgePointId);
        if (point == null && !targetById.isEmpty()) {
            point = targetById.values().iterator().next();
        }
        String prompt = normalizeBlank(question.path("prompt").asText(""));
        JsonNode options = question.path("options");
        String optionA = normalizeBlank(options.path("A").asText(""));
        String optionB = normalizeBlank(options.path("B").asText(""));
        String optionC = normalizeBlank(options.path("C").asText(""));
        String optionD = normalizeBlank(options.path("D").asText(""));
        String correctAnswer = normalizeBlank(question.path("correctAnswer").asText(""));
        if (point == null
                || prompt == null
                || optionA == null
                || optionB == null
                || optionC == null
                || optionD == null
                || correctAnswer == null
                || !VALID_ANSWERS.contains(correctAnswer.toUpperCase(Locale.ROOT))) {
            return null;
        }

        return new AbilityQuestionSpec(
                point,
                prompt,
                optionA,
                optionB,
                optionC,
                optionD,
                correctAnswer.toUpperCase(Locale.ROOT),
                Optional.ofNullable(normalizeBlank(question.path("explanation").asText("")))
                        .orElse("系统将根据正确答案自动评分。")
        );
    }

    private AbilityQuestionSpec buildAdaptiveFallbackQuestionSpec(
            CourseKnowledgePointNodeResponse point,
            String difficulty,
            int questionNumber) {
        String topic = displayKnowledgePoint(point);
        String name = point.getName() == null || point.getName().isBlank() ? topic : point.getName();
        return switch (difficulty) {
            case "EASY" -> new AbilityQuestionSpec(
                    point,
                    "第 " + questionNumber + " 题（简单）：学习「" + topic + "」时，最应该先确认哪一项？",
                    "这个知识点的名称和核心定义。",
                    "页面按钮的颜色。",
                    "与本知识点无关的术语。",
                    "任意一个看起来熟悉的答案。",
                    "A",
                    "简单题主要确认基础概念是否清楚。"
            );
            case "HARD" -> new AbilityQuestionSpec(
                    point,
                    "第 " + questionNumber + " 题（困难）：如果要把「" + name + "」迁移到一个新问题中，最可靠的做法是什么？",
                    "直接复用上一次的答案，不检查条件。",
                    "先识别问题条件，再判断该知识点是否适用并解释原因。",
                    "只根据题目长度选择解法。",
                    "跳过概念分析，先猜一个选项。",
                    "B",
                    "困难题关注迁移应用、适用条件和原因解释。"
            );
            default -> new AbilityQuestionSpec(
                    point,
                    "第 " + questionNumber + " 题（中等）：以下哪项最准确概括「" + topic + "」的学习目标？",
                    "只记住这个知识点的名称即可。",
                    "能解释核心概念、适用条件，并完成典型任务。",
                    "跳过基础定义，直接做任意练习。",
                    "只看一遍材料，不需要复盘。",
                    "B",
                    "中等题关注理解核心概念并完成典型应用。"
            );
        };
    }

    private AbilityQuestionSpec buildQuestionSpec(CourseKnowledgePointNodeResponse point, int aspectIndex) {
        String topic = displayKnowledgePoint(point);
        String name = point.getName() == null || point.getName().isBlank() ? topic : point.getName();
        String description = normalizeBlank(point.getDescription());
        String descriptionOption = description == null
                ? "围绕核心概念、适用条件和典型例子建立理解"
                : abbreviate(description, 80);
        String keywordOption = buildKeywordOption(point);

        return switch (aspectIndex) {
            case 0 -> new AbilityQuestionSpec(
                    point,
                    "以下哪项最准确概括「" + topic + "」的学习目标？",
                    "只记住这个知识点的名称即可。",
                    "能解释核心概念、适用条件，并完成典型任务。",
                    "跳过基础定义，直接做任意练习。",
                    "只看一遍材料，不需要复盘。",
                    "B",
                    "掌握一个知识点需要理解概念、适用条件，并能完成典型任务。"
            );
            case 1 -> new AbilityQuestionSpec(
                    point,
                    "学习「" + topic + "」时，以下哪组信息最值得优先关注？",
                    keywordOption,
                    "页面按钮位置、系统登录入口和个人头像设置。",
                    "课程发布时间、封面颜色和资料文件大小。",
                    "与当前知识点无关的术语堆砌。",
                    "A",
                    "关键词和核心概念能帮助你抓住该知识点的学习重点。"
            );
            case 2 -> new AbilityQuestionSpec(
                    point,
                    "如果一道题或案例需要运用「" + name + "」，最合理的第一步是什么？",
                    "先判断问题特征是否符合该知识点的适用条件。",
                    "直接套用上一次看到的答案。",
                    "只看题目长度来决定解法。",
                    "先跳过所有概念分析。",
                    "A",
                    "解题前先识别适用条件，能避免机械套用。"
            );
            case 3 -> new AbilityQuestionSpec(
                    point,
                    "关于「" + topic + "」的理解，哪种做法最可靠？",
                    "只背一个孤立结论。",
                    "完全依赖猜测，不检查概念边界。",
                    descriptionOption,
                    "只关注和知识点无关的材料格式。",
                    "C",
                    "结合定义、边界和例子理解，通常比孤立记忆更可靠。"
            );
            case 4 -> new AbilityQuestionSpec(
                    point,
                    "完成「" + topic + "」相关练习后，哪种复盘最能提升掌握？",
                    "只记录自己做完了，不检查答案。",
                    "把错误原因、适用条件和改进方法整理出来。",
                    "删除做错的题目，避免再次看到。",
                    "只比较做题速度，不分析理解偏差。",
                    "B",
                    "有效复盘应关注错误原因、适用条件和后续改进。"
            );
            default -> new AbilityQuestionSpec(
                    point,
                    "以下哪种表现最能说明已经掌握「" + topic + "」？",
                    "看到名称觉得熟悉。",
                    "只会照抄材料中的一句话。",
                    "能在相同例子中重复步骤，但无法解释原因。",
                    "能解释概念、举出例子，并迁移到相近问题。",
                    "D",
                    "能解释、举例并迁移应用，说明掌握更扎实。"
            );
        };
    }

    private String displayKnowledgePoint(CourseKnowledgePointNodeResponse point) {
        if (point.getPath() != null && !point.getPath().isBlank()) {
            return point.getPath();
        }
        return point.getName();
    }

    private String buildKeywordOption(CourseKnowledgePointNodeResponse point) {
        String keywords = normalizeBlank(point.getKeywords());
        if (keywords == null) {
            return "围绕“" + point.getName() + "”的核心概念、适用条件和典型例子。";
        }
        List<String> parts = List.of(keywords.split("[,，;；、\\s]+")).stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .limit(4)
                .toList();
        if (parts.isEmpty()) {
            return "围绕“" + point.getName() + "”的核心概念、适用条件和典型例子。";
        }
        return "重点关注：" + String.join("、", parts) + "。";
    }

    private String abbreviate(String value, int maxLength) {
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength) + "...";
    }

    private String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeKnowledgePath(String value) {
        String normalized = normalizeBlank(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized
                .replace("\\", "/")
                .replace(">", "/")
                .replace("／", "/")
                .replace("》", "/")
                .replace(" - ", "/");
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalizeBlank(normalized);
    }

    private String normalizeBaseUrl(String value) {
        String normalized = Optional.ofNullable(value)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .orElse("https://api.deepseek.com");
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private List<AbilityMapResponse> selectFocusKnowledgePoints(List<AbilityMapResponse> abilityMap) {
        List<AbilityMapResponse> weakPoints = abilityMap.stream()
                .filter(item -> item.getMasteryLevel() != null && item.getMasteryLevel() < WEAK_MASTERY_THRESHOLD)
                .limit(MAX_PATH_FOCUS_POINTS)
                .toList();
        if (!weakPoints.isEmpty()) {
            return weakPoints;
        }
        return abilityMap.stream()
                .limit(MAX_PATH_FOCUS_POINTS)
                .toList();
    }

    private Set<Long> collectCandidateCourseIds(
            Collection<LearningProgressEntity> progresses,
            String authorization) {
        Set<Long> courseIds = progresses.stream()
                .map(LearningProgressEntity::getCourseId)
                .filter(value -> value != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        try {
            CourseApiResponse<PageResponse<CourseSummaryResponse>> response =
                    courseServiceClient.listEnrolledCourses(authorization, 1, 100);
            PageResponse<CourseSummaryResponse> page = response == null ? null : response.getData();
            if (page != null && page.getItems() != null) {
                page.getItems().stream()
                        .map(CourseSummaryResponse::getId)
                        .filter(value -> value != null)
                        .forEach(courseIds::add);
            }
        } catch (FeignException.Forbidden ex) {
            throw BusinessException.forbidden("No access to enrolled courses");
        } catch (FeignException.Unauthorized ex) {
            throw BusinessException.unauthorized("Invalid token");
        } catch (FeignException ex) {
            if (courseIds.isEmpty()) {
                throw BusinessException.badRequest("Failed to load enrolled courses");
            }
        }

        try {
            CourseApiResponse<PageResponse<CourseSummaryResponse>> response =
                    courseServiceClient.listCourses(authorization, 1, 100);
            PageResponse<CourseSummaryResponse> page = response == null ? null : response.getData();
            if (page != null && page.getItems() != null) {
                page.getItems().stream()
                        .map(CourseSummaryResponse::getId)
                        .filter(value -> value != null)
                        .forEach(courseIds::add);
            }
        } catch (FeignException.Forbidden ex) {
            throw BusinessException.forbidden("No access to visible courses");
        } catch (FeignException.Unauthorized ex) {
            throw BusinessException.unauthorized("Invalid token");
        } catch (FeignException ex) {
            if (courseIds.isEmpty()) {
                throw BusinessException.badRequest("Failed to load visible courses");
            }
        }

        return courseIds;
    }

    private Map<Long, CourseContext> loadCourseContexts(Set<Long> courseIds, String authorization) {
        Map<Long, CourseContext> contexts = new LinkedHashMap<>();
        for (Long courseId : courseIds) {
            CourseSummaryResponse course = getCourseSummary(courseId, authorization);
            if (course == null) {
                continue;
            }

            List<CourseChapterResponse> chapters = listCourseChapters(courseId, authorization);
            Map<Long, CourseChapterResponse> chaptersById = chapters.stream()
                    .collect(Collectors.toMap(
                            CourseChapterResponse::getId,
                            chapter -> chapter,
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));

            List<CourseResourceResponse> resources = new ArrayList<>();
            for (CourseChapterResponse chapter : chapters) {
                resources.addAll(listChapterResources(chapter.getId(), authorization));
            }
            contexts.put(courseId, new CourseContext(course, chaptersById, resources));
        }
        return contexts;
    }

    private List<AbilityMapResponse> syncAbilityMapFromCompletedResource(
            LearningProgressEntity completedProgress,
            String authorization) {
        CourseResourceResponse completedResource = findCourseResource(
                completedProgress.getCourseId(),
                completedProgress.getResourceId(),
                authorization);
        if (completedResource == null
                || completedResource.getKnowledgePoints() == null
                || completedResource.getKnowledgePoints().isEmpty()) {
            return List.of();
        }

        Map<Long, ProgressStats> progressStats = buildProgressStats(completedProgress.getStudentId(), authorization);
        List<AbilityMapResponse> responses = new ArrayList<>();
        for (CourseResourceKnowledgePointResponse knowledgePoint : completedResource.getKnowledgePoints()) {
            if (knowledgePoint.getId() == null) {
                continue;
            }
            ProgressStats stats = progressStats.get(knowledgePoint.getId());
            double progressScore = stats == null ? clampProgress(completedProgress.getProgress()) : stats.averageProgress();
            int resourceCount = stats == null ? 1 : stats.resourceCount();

            AbilityMapEntity entity = abilityMapRepository
                    .findByStudentIdAndKnowledgePointId(completedProgress.getStudentId(), knowledgePoint.getId())
                    .orElseGet(() -> AbilityMapEntity.builder()
                            .studentId(completedProgress.getStudentId())
                            .knowledgePointId(knowledgePoint.getId())
                            .testScore(0D)
                            .lastTestedAt(null)
                            .build());

            double testScore = entity.getTestScore() == null ? 0D : entity.getTestScore();
            boolean hasTestSignal = entity.getLastTestedAt() != null || testScore > 0D;
            entity.setKnowledgePointName(resolveKnowledgePointName(knowledgePoint, stats));
            entity.setKnowledgePointPath(resolveKnowledgePointPath(knowledgePoint, stats));
            entity.setProgressScore(roundScore(progressScore));
            entity.setResourceCount(resourceCount);
            entity.setSource(hasTestSignal ? "TEST_AND_PROGRESS" : "LEARNING_PROGRESS");
            entity.setConfidence(hasTestSignal ? 0.9D : 0.45D);
            entity.setMasteryLevel(roundScore(hasTestSignal ? blendMastery(testScore, progressScore) : progressScore));

            responses.add(toAbilityMapResponse(abilityMapRepository.save(entity)));
        }
        return responses;
    }

    private CourseResourceResponse findCourseResource(Long courseId, Long resourceId, String authorization) {
        if (courseId == null || resourceId == null) {
            return null;
        }

        for (CourseChapterResponse chapter : listCourseChapters(courseId, authorization)) {
            for (CourseResourceResponse resource : listChapterResources(chapter.getId(), authorization)) {
                if (resourceId.equals(resource.getId())) {
                    return resource;
                }
            }
        }
        return null;
    }

    private String resolveKnowledgePointName(CourseResourceKnowledgePointResponse knowledgePoint, ProgressStats stats) {
        if (stats != null && stats.knowledgePointName() != null && !stats.knowledgePointName().isBlank()) {
            return stats.knowledgePointName();
        }
        if (knowledgePoint.getName() != null && !knowledgePoint.getName().isBlank()) {
            return knowledgePoint.getName();
        }
        return "知识点 " + knowledgePoint.getId();
    }

    private String resolveKnowledgePointPath(CourseResourceKnowledgePointResponse knowledgePoint, ProgressStats stats) {
        if (stats != null && stats.knowledgePointPath() != null && !stats.knowledgePointPath().isBlank()) {
            return stats.knowledgePointPath();
        }
        return knowledgePoint.getPath();
    }

    private List<PathCandidate> buildPathCandidates(
            List<AbilityMapResponse> focusPoints,
            Map<Long, LearningProgressEntity> progressByResourceId,
            Map<Long, CourseContext> courseContexts) {
        Map<Long, PathCandidate> candidates = new LinkedHashMap<>();

        for (AbilityMapResponse focusPoint : focusPoints) {
            for (Map.Entry<Long, CourseContext> courseEntry : courseContexts.entrySet()) {
                Long courseId = courseEntry.getKey();
                CourseContext context = courseEntry.getValue();
                for (CourseResourceResponse resource : context.resources()) {
                    if (!matchesFocusPoint(resource, focusPoint)) {
                        continue;
                    }

                    LearningProgressEntity progress = progressByResourceId.get(resource.getId());
                    double currentProgress = progress == null ? 0D : clampProgress(progress.getProgress());
                    if (progress != null && Boolean.TRUE.equals(progress.getCompleted())) {
                        continue;
                    }

                    CourseChapterResponse chapter = context.chaptersById().get(resource.getChapterId());
                    PathCandidate candidate = new PathCandidate(
                            resource.getId(),
                            courseId,
                            resource.getChapterId(),
                            resource.getTitle(),
                            context.course().getTitle(),
                            chapter == null ? null : chapter.getTitle(),
                            focusPoint.getKnowledgePointId(),
                            focusPoint.getKnowledgePointName(),
                            currentProgress,
                            scorePathCandidate(focusPoint, currentProgress),
                            buildPathReason(focusPoint, currentProgress, resource.getTitle())
                    );
                    candidates.merge(resource.getId(), candidate, (left, right) ->
                            left.score() >= right.score() ? left : right);
                }
            }
        }

        return candidates.values().stream()
                .sorted(Comparator.comparing(PathCandidate::score).reversed()
                        .thenComparing(PathCandidate::courseTitle, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(PathCandidate::resourceTitle, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    private boolean matchesFocusPoint(CourseResourceResponse resource, AbilityMapResponse focusPoint) {
        boolean hasKnowledgePoints = resource.getKnowledgePoints() != null && !resource.getKnowledgePoints().isEmpty();
        boolean hasTags = resource.getTags() != null && !resource.getTags().isEmpty();
        if (!hasKnowledgePoints && !hasTags) {
            return false;
        }
        Long knowledgePointId = focusPoint.getKnowledgePointId();
        String focusName = normalizeBlank(focusPoint.getKnowledgePointName());
        String focusPath = normalizeKnowledgePath(focusPoint.getKnowledgePointPath());
        boolean matchedKnowledgePoint = hasKnowledgePoints && resource.getKnowledgePoints().stream()
                .anyMatch(knowledgePoint -> {
                    if (knowledgePointId != null && knowledgePointId.equals(knowledgePoint.getId())) {
                        return true;
                    }
                    String resourceName = normalizeBlank(knowledgePoint.getName());
                    if (focusName != null && resourceName != null && focusName.equalsIgnoreCase(resourceName)) {
                        return true;
                    }
                    String resourcePath = normalizeKnowledgePath(knowledgePoint.getPath());
                    return focusPath != null
                            && resourcePath != null
                            && (resourcePath.equalsIgnoreCase(focusPath)
                            || resourcePath.toLowerCase(Locale.ROOT).startsWith(focusPath.toLowerCase(Locale.ROOT) + "/")
                            || focusPath.toLowerCase(Locale.ROOT).startsWith(resourcePath.toLowerCase(Locale.ROOT) + "/"));
                });
        if (matchedKnowledgePoint) {
            return true;
        }
        return hasTags && resource.getTags().stream()
                .anyMatch(tag -> matchesFocusTag(tag, knowledgePointId, focusName, focusPath));
    }

    private boolean matchesFocusTag(
            CourseResourceTagResponse tag,
            Long knowledgePointId,
            String focusName,
            String focusPath) {
        if (knowledgePointId != null && knowledgePointId.equals(tag.getKnowledgePointId())) {
            return true;
        }
        String tagLabel = normalizeBlank(tag.getLabel());
        if (focusName != null && tagLabel != null && focusName.equalsIgnoreCase(tagLabel)) {
            return true;
        }
        String tagPath = normalizeKnowledgePath(tag.getKnowledgePointPath());
        return focusPath != null
                && tagPath != null
                && (tagPath.equalsIgnoreCase(focusPath)
                || tagPath.toLowerCase(Locale.ROOT).startsWith(focusPath.toLowerCase(Locale.ROOT) + "/")
                || focusPath.toLowerCase(Locale.ROOT).startsWith(tagPath.toLowerCase(Locale.ROOT) + "/"));
    }

    private double scorePathCandidate(AbilityMapResponse focusPoint, double currentProgress) {
        double weaknessScore = 1D - (focusPoint.getMasteryLevel() == null ? 0D : focusPoint.getMasteryLevel());
        double remainingProgress = 1D - currentProgress;
        return roundScore((weaknessScore * 0.8D) + (remainingProgress * 0.2D));
    }

    private String resolvePathStatus(double currentProgress) {
        if (currentProgress >= 0.999D) {
            return PATH_STATUS_COMPLETED;
        }
        return currentProgress > 0D ? PATH_STATUS_IN_PROGRESS : PATH_STATUS_NOT_STARTED;
    }

    private String resolvePathStatusLabel(double currentProgress) {
        if (currentProgress >= 0.999D) {
            return "已完成";
        }
        return currentProgress > 0D ? "学习中" : "未开始";
    }

    private String resolvePathActionLabel(double currentProgress) {
        if (currentProgress >= 0.999D) {
            return "复习资源";
        }
        return currentProgress > 0D ? "继续学习" : "开始学习";
    }

    private String buildPathReason(AbilityMapResponse focusPoint, double currentProgress, String resourceTitle) {
        int masteryPercent = (int) Math.round((focusPoint.getMasteryLevel() == null ? 0D : focusPoint.getMasteryLevel()) * 100D);
        int progressPercent = Math.round((float) (currentProgress * 100D));
        if (currentProgress > 0D) {
            return "你在「" + focusPoint.getKnowledgePointName() + "」当前掌握度约为 "
                    + masteryPercent + "%，而资源《" + resourceTitle + "》已学习 "
                    + progressPercent + "%，适合继续完成以巩固薄弱点。";
        }
        return "你在「" + focusPoint.getKnowledgePointName() + "」当前掌握度约为 "
                + masteryPercent + "%，该资源已标注覆盖这个知识点，适合作为下一步补强内容。";
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
                .filter(value -> value != null)
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
                        (left, right) -> left,
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

    private CourseSummaryResponse getCourseSummary(Long courseId, String authorization) {
        try {
            CourseApiResponse<CourseSummaryResponse> response = courseServiceClient.getCourse(authorization, courseId);
            return response == null ? null : response.getData();
        } catch (FeignException.NotFound ex) {
            return null;
        } catch (FeignException.Forbidden ex) {
            throw BusinessException.forbidden("No access to this course");
        } catch (FeignException.Unauthorized ex) {
            throw BusinessException.unauthorized("Invalid token");
        } catch (FeignException ex) {
            return null;
        }
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

    private void assertTeacher(UserContext userContext) {
        if (!"TEACHER".equals(userContext.role()) && !"ADMIN".equals(userContext.role())) {
            throw BusinessException.forbidden("Only teachers can access dashboard analytics");
        }
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
        if (!VALID_ANSWERS.contains(normalized)) {
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

    private record AbilityQuestionBuildResult(
            List<AbilityQuestionSpec> questions,
            String generationMode,
            String generationMessage
    ) {
    }

    private record AiQuestionGenerationResult(
            List<AbilityQuestionSpec> questions,
            String message
    ) {
    }

    private record AbilityQuestionSpec(
            CourseKnowledgePointNodeResponse point,
            String prompt,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            String correctAnswer,
            String explanation
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

    private record CourseContext(
            CourseSummaryResponse course,
            Map<Long, CourseChapterResponse> chaptersById,
            List<CourseResourceResponse> resources
    ) {
    }

    private record PathCandidate(
            Long resourceId,
            Long courseId,
            Long chapterId,
            String resourceTitle,
            String courseTitle,
            String chapterTitle,
            Long focusKnowledgePointId,
            String focusKnowledgePointName,
            double currentProgress,
            double score,
            String reason
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

    private static final class PathFocusAccumulator {
        private final Long knowledgePointId;
        private final String knowledgePointName;
        private final String knowledgePointPath;
        private double totalProgress;
        private int resourceCount;
        private int trackedResourceCount;

        private PathFocusAccumulator(Long knowledgePointId, String knowledgePointName, String knowledgePointPath) {
            this.knowledgePointId = knowledgePointId;
            this.knowledgePointName = knowledgePointName;
            this.knowledgePointPath = knowledgePointPath;
        }

        private void add(double progress, boolean tracked) {
            totalProgress += progress;
            resourceCount += 1;
            if (tracked) {
                trackedResourceCount += 1;
            }
        }

        private AbilityMapResponse toAbilityMapResponse() {
            double average = resourceCount == 0 ? 0D : totalProgress / resourceCount;
            String source = trackedResourceCount > 0 ? "LEARNING_PROGRESS" : "COURSE_TAGS";
            double confidence = trackedResourceCount > 0 ? 0.45D : 0.3D;
            double roundedProgress = Math.round(Math.min(1D, Math.max(0D, average)) * 100D) / 100D;
            return AbilityMapResponse.builder()
                    .knowledgePointId(knowledgePointId)
                    .knowledgePointName(knowledgePointName)
                    .knowledgePointPath(knowledgePointPath)
                    .masteryLevel(roundedProgress)
                    .confidence(confidence)
                    .testScore(0D)
                    .progressScore(roundedProgress)
                    .resourceCount(resourceCount)
                    .source(source)
                    .lastTestedAt(null)
                    .build();
        }
    }

    private static final class KnowledgePointAnalyticsAccumulator {
        private final Long knowledgePointId;
        private final String knowledgePointName;
        private final String knowledgePointPath;
        private double totalProgress;
        private int recordCount;
        private final Set<Long> studentIds = new LinkedHashSet<>();
        private final Set<Long> resourceIds = new LinkedHashSet<>();

        private KnowledgePointAnalyticsAccumulator(Long knowledgePointId, String knowledgePointName, String knowledgePointPath) {
            this.knowledgePointId = knowledgePointId;
            this.knowledgePointName = knowledgePointName;
            this.knowledgePointPath = knowledgePointPath;
        }

        private void add(double progress, Long studentId, Long resourceId) {
            totalProgress += progress;
            recordCount += 1;
            if (studentId != null) {
                studentIds.add(studentId);
            }
            if (resourceId != null) {
                resourceIds.add(resourceId);
            }
        }

        private TeacherKnowledgePointAnalyticsResponse toResponse() {
            double average = recordCount == 0 ? 0D : totalProgress / recordCount;
            return TeacherKnowledgePointAnalyticsResponse.builder()
                    .knowledgePointId(knowledgePointId)
                    .knowledgePointName(knowledgePointName)
                    .knowledgePointPath(knowledgePointPath)
                    .averageProgress(Math.round(Math.min(1D, Math.max(0D, average)) * 100D) / 100D)
                    .activeStudents(studentIds.size())
                    .relatedResources(resourceIds.size())
                    .build();
        }
    }
}
