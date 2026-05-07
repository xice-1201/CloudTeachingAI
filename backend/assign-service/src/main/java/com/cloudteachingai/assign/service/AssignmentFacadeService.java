package com.cloudteachingai.assign.service;

import com.cloudteachingai.assign.client.CourseApiResponse;
import com.cloudteachingai.assign.client.CoursePageResponse;
import com.cloudteachingai.assign.client.CourseServiceClient;
import com.cloudteachingai.assign.client.CourseSummaryResponse;
import com.cloudteachingai.assign.client.CreateNotificationRequest;
import com.cloudteachingai.assign.client.NotifyServiceClient;
import com.cloudteachingai.assign.controller.AssignmentController.UserContext;
import com.cloudteachingai.assign.dto.AssignmentResponse;
import com.cloudteachingai.assign.dto.AssignmentUpsertRequest;
import com.cloudteachingai.assign.dto.PageResponse;
import com.cloudteachingai.assign.dto.SubmissionCreateRequest;
import com.cloudteachingai.assign.dto.SubmissionResponse;
import com.cloudteachingai.assign.dto.SubmissionReviewRequest;
import com.cloudteachingai.assign.entity.AssignmentEntity;
import com.cloudteachingai.assign.entity.SubmissionEntity;
import com.cloudteachingai.assign.entity.enums.SubmissionStatus;
import com.cloudteachingai.assign.entity.enums.SubmitType;
import com.cloudteachingai.assign.exception.BusinessException;
import com.cloudteachingai.assign.repository.AssignmentRepository;
import com.cloudteachingai.assign.repository.SubmissionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AssignmentFacadeService {

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final CourseServiceClient courseServiceClient;
    private final NotifyServiceClient notifyServiceClient;
    private final ObjectMapper objectMapper;

    private static final Pattern SPLIT_PATTERN = Pattern.compile("[\\s,，。；;：:、.!！?？\\n\\r\\t]+");

    public PageResponse<AssignmentResponse> listAssignments(
            Long courseId,
            int page,
            int pageSize,
            String authorization,
            UserContext userContext) {
        ensureCourseAccessible(courseId, authorization);
        Pageable pageable = PageRequest.of(toPageIndex(page), toPageSize(pageSize));
        Page<AssignmentEntity> result = assignmentRepository.findByCourseIdOrderByDeadlineAscIdDesc(courseId, pageable);
        return PageResponse.<AssignmentResponse>builder()
                .items(result.getContent().stream().map(this::toAssignmentResponse).toList())
                .total((int) result.getTotalElements())
                .page(page)
                .pageSize(pageSize)
                .build();
    }

    public AssignmentResponse getAssignment(Long assignmentId, String authorization, UserContext userContext) {
        AssignmentEntity assignment = requireAssignment(assignmentId);
        ensureCourseAccessible(assignment.getCourseId(), authorization);
        return toAssignmentResponse(assignment);
    }

    @Transactional
    public AssignmentResponse createAssignment(
            Long courseId,
            AssignmentUpsertRequest request,
            String authorization,
            UserContext userContext) {
        assertRole(userContext, "TEACHER", "ADMIN");
        CourseSummaryResponse course = ensureCourseManageable(courseId, authorization);

        AssignmentEntity assignment = AssignmentEntity.builder()
                .courseId(courseId)
                .courseTitle(course.getTitle())
                .teacherId(course.getTeacherId())
                .title(request.getTitle().trim())
                .description(request.getDescription().trim())
                .gradingCriteria(normalizeBlank(request.getGradingCriteria()))
                .submitType(parseSubmitType(request.getSubmitType()))
                .maxScore(request.getMaxScore())
                .deadline(request.getDueDate())
                .build();
        AssignmentEntity saved = assignmentRepository.save(assignment);
        notifyAssignmentPublished(saved);
        return toAssignmentResponse(saved);
    }

    @Transactional
    public AssignmentResponse updateAssignment(
            Long assignmentId,
            AssignmentUpsertRequest request,
            String authorization,
            UserContext userContext) {
        assertRole(userContext, "TEACHER", "ADMIN");
        AssignmentEntity assignment = requireAssignment(assignmentId);
        ensureCourseManageable(assignment.getCourseId(), authorization);

        assignment.setTitle(request.getTitle().trim());
        assignment.setDescription(request.getDescription().trim());
        assignment.setGradingCriteria(normalizeBlank(request.getGradingCriteria()));
        assignment.setSubmitType(parseSubmitType(request.getSubmitType()));
        assignment.setMaxScore(request.getMaxScore());
        assignment.setDeadline(request.getDueDate());
        return toAssignmentResponse(assignmentRepository.save(assignment));
    }

    @Transactional
    public void deleteAssignment(Long assignmentId, String authorization, UserContext userContext) {
        assertRole(userContext, "TEACHER", "ADMIN");
        AssignmentEntity assignment = requireAssignment(assignmentId);
        ensureCourseManageable(assignment.getCourseId(), authorization);
        assignmentRepository.delete(assignment);
    }

    @Transactional
    public SubmissionResponse submitAssignment(
            Long assignmentId,
            SubmissionCreateRequest request,
            String authorization,
            UserContext userContext) {
        assertRole(userContext, "STUDENT");
        AssignmentEntity assignment = requireAssignment(assignmentId);
        ensureCourseAccessible(assignment.getCourseId(), authorization);
        if (assignment.getDeadline().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw BusinessException.badRequest("Assignment deadline has passed");
        }
        if (submissionRepository.existsByAssignmentIdAndStudentId(assignmentId, userContext.userId())) {
            throw BusinessException.conflict("Assignment already submitted");
        }

        SubmissionEntity submission = SubmissionEntity.builder()
                .assignmentId(assignmentId)
                .studentId(userContext.userId())
                .content(request.getContent().trim())
                .attachmentsJson(writeAttachments(request.getAttachments()))
                .status(SubmissionStatus.SUBMITTED)
                .submittedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        applyAiReview(assignment, submission);
        SubmissionEntity saved = submissionRepository.save(submission);
        notifyAssignmentSubmitted(assignment, saved);
        notifyGradingCompleted(assignment, saved);
        return toSubmissionResponse(saved);
    }

    public SubmissionResponse getMySubmission(Long assignmentId, String authorization, UserContext userContext) {
        assertRole(userContext, "STUDENT");
        AssignmentEntity assignment = requireAssignment(assignmentId);
        ensureCourseAccessible(assignment.getCourseId(), authorization);
        SubmissionEntity submission = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, userContext.userId())
                .orElseThrow(() -> BusinessException.notFound("Submission not found"));
        return toSubmissionResponse(submission);
    }

    public PageResponse<SubmissionResponse> listSubmissions(
            Long assignmentId,
            int page,
            int pageSize,
            String authorization,
            UserContext userContext) {
        assertRole(userContext, "TEACHER", "ADMIN");
        AssignmentEntity assignment = requireAssignment(assignmentId);
        ensureCourseManageable(assignment.getCourseId(), authorization);
        Pageable pageable = PageRequest.of(toPageIndex(page), toPageSize(pageSize));
        Page<SubmissionEntity> result = submissionRepository.findByAssignmentIdOrderBySubmittedAtDescIdDesc(assignmentId, pageable);
        return PageResponse.<SubmissionResponse>builder()
                .items(result.getContent().stream().map(this::toSubmissionResponse).toList())
                .total((int) result.getTotalElements())
                .page(page)
                .pageSize(pageSize)
                .build();
    }

    @Transactional
    public SubmissionResponse reviewSubmission(
            Long submissionId,
            SubmissionReviewRequest request,
            String authorization,
            UserContext userContext) {
        assertRole(userContext, "TEACHER", "ADMIN");
        SubmissionEntity submission = requireSubmission(submissionId);
        AssignmentEntity assignment = requireAssignment(submission.getAssignmentId());
        ensureCourseManageable(assignment.getCourseId(), authorization);
        if (request.getScore() > assignment.getMaxScore()) {
            throw BusinessException.badRequest("Score cannot exceed max score");
        }

        submission.setFinalScore(request.getScore());
        submission.setFinalFeedback(request.getFeedback().trim());
        submission.setStatus(SubmissionStatus.REVIEWED);
        submission.setGradedAt(OffsetDateTime.now(ZoneOffset.UTC));
        SubmissionEntity saved = submissionRepository.save(submission);
        notifyReviewCompleted(assignment, saved);
        return toSubmissionResponse(saved);
    }

    public List<AssignmentResponse> listPendingAssignments(
            int pageSize,
            String authorization,
            UserContext userContext) {
        assertRole(userContext, "STUDENT");

        List<Long> courseIds = resolveStudentCourseIds(authorization);
        if (courseIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<AssignmentEntity> assignments = assignmentRepository.findByCourseIdInAndDeadlineAfterOrderByDeadlineAscIdAsc(
                courseIds, OffsetDateTime.now(ZoneOffset.UTC));
        if (assignments.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> assignmentIds = assignments.stream().map(AssignmentEntity::getId).toList();
        Set<Long> submittedAssignmentIds = new HashSet<>(
                submissionRepository.findByStudentIdAndAssignmentIdIn(userContext.userId(), assignmentIds)
                        .stream()
                        .map(SubmissionEntity::getAssignmentId)
                        .toList()
        );

        return assignments.stream()
                .filter(assignment -> !submittedAssignmentIds.contains(assignment.getId()))
                .limit(Math.max(pageSize, 1))
                .map(this::toAssignmentResponse)
                .toList();
    }

    private List<Long> resolveStudentCourseIds(String authorization) {
        int page = 1;
        int pageSize = 100;
        List<Long> courseIds = new ArrayList<>();

        while (true) {
            try {
                CourseApiResponse<CoursePageResponse<CourseSummaryResponse>> response = courseServiceClient.listEnrolledCourses(
                        authorization, page, pageSize);
                CoursePageResponse<CourseSummaryResponse> data = response == null ? null : response.getData();
                if (data == null || data.getItems() == null || data.getItems().isEmpty()) {
                    break;
                }
                courseIds.addAll(data.getItems().stream().map(CourseSummaryResponse::getId).toList());
                if (courseIds.size() >= data.getTotal()) {
                    break;
                }
                page++;
            } catch (FeignException.Unauthorized ex) {
                throw BusinessException.unauthorized("Invalid token");
            } catch (FeignException.Forbidden ex) {
                throw BusinessException.forbidden("No access to course assignments");
            } catch (FeignException ex) {
                break;
            }
        }

        return courseIds;
    }

    private List<Long> resolveCourseStudentIds(Long courseId) {
        try {
            CourseApiResponse<List<Long>> response = courseServiceClient.listCourseStudentIds(courseId);
            if (response == null || response.getData() == null) {
                return Collections.emptyList();
            }
            return response.getData();
        } catch (FeignException ex) {
            return Collections.emptyList();
        }
    }

    private void notifyAssignmentPublished(AssignmentEntity assignment) {
        List<Long> studentIds = resolveCourseStudentIds(assignment.getCourseId());
        for (Long studentId : studentIds) {
            sendNotification(
                    studentId,
                    "ASSIGNMENT",
                    "新作业已发布",
                    "课程《" + assignment.getCourseTitle() + "》发布了新作业：" + assignment.getTitle(),
                    "ASSIGNMENT",
                    assignment.getId(),
                    "/assignments/" + assignment.getId()
            );
        }
    }

    private void notifyAssignmentSubmitted(AssignmentEntity assignment, SubmissionEntity submission) {
        sendNotification(
                assignment.getTeacherId(),
                "ASSIGNMENT",
                "收到新的作业提交",
                "课程《" + assignment.getCourseTitle() + "》的作业《" + assignment.getTitle()
                        + "》有学生提交，学生 ID：" + submission.getStudentId(),
                "ASSIGNMENT_SUBMISSIONS",
                assignment.getId(),
                "/assignments/" + assignment.getId() + "/submissions"
        );
    }

    private void notifyGradingCompleted(AssignmentEntity assignment, SubmissionEntity submission) {
        if (submission.getStatus() != SubmissionStatus.AI_GRADED) {
            return;
        }
        sendNotification(
                submission.getStudentId(),
                "GRADE",
                "AI 批改已完成",
                "作业《" + assignment.getTitle() + "》已完成 AI 批改，可查看评分和评语",
                "ASSIGNMENT",
                assignment.getId(),
                "/assignments/" + assignment.getId()
        );
    }

    private void notifyReviewCompleted(AssignmentEntity assignment, SubmissionEntity submission) {
        sendNotification(
                submission.getStudentId(),
                "GRADE",
                "作业成绩已更新",
                "教师已复核作业《" + assignment.getTitle() + "》的成绩和评语",
                "ASSIGNMENT",
                assignment.getId(),
                "/assignments/" + assignment.getId()
        );
    }

    private void sendNotification(
            Long userId,
            String type,
            String title,
            String content,
            String targetType,
            Long targetId,
            String targetUrl) {
        if (userId == null) {
            return;
        }
        try {
            notifyServiceClient.createNotification(CreateNotificationRequest.builder()
                    .userId(userId)
                    .type(type)
                    .title(title)
                    .content(content)
                    .targetType(targetType)
                    .targetId(targetId)
                    .targetUrl(targetUrl)
                    .build());
        } catch (FeignException ignored) {
            // Notification delivery must not block assignment workflows.
        } catch (Exception ignored) {
            // Ignore transient notification issues.
        }
    }

    private CourseSummaryResponse ensureCourseAccessible(Long courseId, String authorization) {
        try {
            CourseApiResponse<CourseSummaryResponse> response = courseServiceClient.getCourse(authorization, courseId);
            if (response == null || response.getData() == null) {
                throw BusinessException.notFound("Course not found");
            }
            return response.getData();
        } catch (FeignException.NotFound ex) {
            throw BusinessException.notFound("Course not found");
        } catch (FeignException.Forbidden ex) {
            throw BusinessException.forbidden("No access to this course");
        } catch (FeignException.Unauthorized ex) {
            throw BusinessException.unauthorized("Invalid token");
        }
    }

    private CourseSummaryResponse ensureCourseManageable(Long courseId, String authorization) {
        return ensureCourseAccessible(courseId, authorization);
    }

    private AssignmentEntity requireAssignment(Long assignmentId) {
        return assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> BusinessException.notFound("Assignment not found"));
    }

    private SubmissionEntity requireSubmission(Long submissionId) {
        return submissionRepository.findById(submissionId)
                .orElseThrow(() -> BusinessException.notFound("Submission not found"));
    }

    private void assertRole(UserContext userContext, String... roles) {
        for (String role : roles) {
            if (role.equals(userContext.role())) {
                return;
            }
        }
        throw BusinessException.forbidden("Current role cannot perform this action");
    }

    private SubmitType parseSubmitType(String submitType) {
        if (!StringUtils.hasText(submitType)) {
            return SubmitType.TEXT;
        }
        try {
            return SubmitType.valueOf(submitType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest("Unsupported submit type");
        }
    }

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int toPageIndex(int page) {
        return Math.max(page, 1) - 1;
    }

    private int toPageSize(int pageSize) {
        return Math.min(Math.max(pageSize, 1), 100);
    }

    private String writeAttachments(List<String> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attachments);
        } catch (JsonProcessingException ex) {
            throw BusinessException.badRequest("Invalid attachment payload");
        }
    }

    private List<String> readAttachments(String attachmentsJson) {
        if (!StringUtils.hasText(attachmentsJson)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(attachmentsJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            return Collections.emptyList();
        }
    }

    private void applyAiReview(AssignmentEntity assignment, SubmissionEntity submission) {
        AiReviewResult result = generateAiReview(assignment, submission.getContent());
        submission.setAiScore(result.score());
        submission.setAiFeedback(result.feedback());
        submission.setStatus(SubmissionStatus.AI_GRADED);
        submission.setGradedAt(OffsetDateTime.now(ZoneOffset.UTC));
    }

    private AiReviewResult generateAiReview(AssignmentEntity assignment, String content) {
        String answer = content == null ? "" : content.trim();
        double maxScore = assignment.getMaxScore() == null ? 100D : assignment.getMaxScore();
        int length = answer.length();

        double lengthScore = Math.min(1D, length / 500D);
        double structureScore = scoreStructure(answer);
        double criteriaScore = scoreCriteriaCoverage(answer, assignment);
        double finalRatio = (lengthScore * 0.35D) + (structureScore * 0.25D) + (criteriaScore * 0.4D);
        double score = Math.round(Math.min(maxScore, Math.max(0D, maxScore * finalRatio)) * 10D) / 10D;

        List<String> strengths = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        if (lengthScore >= 0.75D) {
            strengths.add("作答内容较充分");
        } else {
            suggestions.add("进一步补充关键步骤、依据或示例");
        }
        if (structureScore >= 0.7D) {
            strengths.add("表达结构较清晰");
        } else {
            suggestions.add("按“观点、过程、结论”组织答案");
        }
        if (criteriaScore >= 0.7D) {
            strengths.add("较好覆盖了评分标准中的核心要求");
        } else {
            suggestions.add("对照评分标准补齐遗漏要点");
        }

        String feedback = "AI 批改建议：建议得分 " + score + " / " + maxScore + "。"
                + "优点：" + (strengths.isEmpty() ? "已完成基础提交" : String.join("；", strengths)) + "。"
                + "改进建议：" + String.join("；", suggestions) + "。"
                + "请教师结合课程目标和学生实际情况复核后发布最终成绩。";
        return new AiReviewResult(score, feedback);
    }

    private double scoreStructure(String content) {
        if (!StringUtils.hasText(content)) {
            return 0D;
        }
        int paragraphCount = (int) content.lines().filter(line -> StringUtils.hasText(line.trim())).count();
        boolean hasPunctuation = content.contains("。") || content.contains(".") || content.contains("；") || content.contains(";");
        boolean hasSequence = content.contains("首先") || content.contains("其次") || content.contains("最后")
                || content.contains("第一") || content.contains("第二") || content.matches("(?s).*\\b(1|2|3)[.)、].*");
        double score = 0.35D;
        if (paragraphCount >= 2) {
            score += 0.25D;
        }
        if (hasPunctuation) {
            score += 0.2D;
        }
        if (hasSequence) {
            score += 0.2D;
        }
        return Math.min(1D, score);
    }

    private double scoreCriteriaCoverage(String content, AssignmentEntity assignment) {
        Set<String> keywords = extractKeywords(assignment.getGradingCriteria());
        if (keywords.isEmpty()) {
            keywords = extractKeywords(assignment.getDescription());
        }
        if (keywords.isEmpty()) {
            return StringUtils.hasText(content) ? 0.65D : 0D;
        }

        String normalizedContent = content.toLowerCase(Locale.ROOT);
        long matched = keywords.stream()
                .filter(keyword -> normalizedContent.contains(keyword.toLowerCase(Locale.ROOT)))
                .count();
        return Math.min(1D, matched / (double) Math.min(keywords.size(), 8));
    }

    private Set<String> extractKeywords(String text) {
        if (!StringUtils.hasText(text)) {
            return Collections.emptySet();
        }
        Set<String> keywords = new LinkedHashSet<>();
        for (String token : SPLIT_PATTERN.split(text.trim())) {
            String value = token.trim();
            if (value.length() >= 2 && value.length() <= 20 && !isCommonWord(value)) {
                keywords.add(value);
            }
            if (keywords.size() >= 12) {
                break;
            }
        }
        return keywords;
    }

    private boolean isCommonWord(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return Set.of("要求", "说明", "作业", "完成", "提交", "内容", "需要", "进行", "the", "and", "with").contains(normalized);
    }

    private AssignmentResponse toAssignmentResponse(AssignmentEntity entity) {
        return AssignmentResponse.builder()
                .id(entity.getId())
                .courseId(entity.getCourseId())
                .courseTitle(entity.getCourseTitle())
                .teacherId(entity.getTeacherId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .gradingCriteria(entity.getGradingCriteria())
                .submitType(entity.getSubmitType().name())
                .maxScore(entity.getMaxScore())
                .dueDate(entity.getDeadline().toString())
                .createdAt(entity.getCreatedAt().toString())
                .build();
    }

    private SubmissionResponse toSubmissionResponse(SubmissionEntity entity) {
        Double score = entity.getFinalScore() != null ? entity.getFinalScore() : entity.getAiScore();
        String feedback = entity.getFinalFeedback() != null ? entity.getFinalFeedback() : entity.getAiFeedback();
        return SubmissionResponse.builder()
                .id(entity.getId())
                .assignmentId(entity.getAssignmentId())
                .studentId(entity.getStudentId())
                .content(entity.getContent())
                .attachments(readAttachments(entity.getAttachmentsJson()))
                .aiScore(entity.getAiScore())
                .aiFeedback(entity.getAiFeedback())
                .finalScore(entity.getFinalScore())
                .finalFeedback(entity.getFinalFeedback())
                .score(score)
                .feedback(feedback)
                .status(entity.getStatus().name())
                .submittedAt(entity.getSubmittedAt().toString())
                .gradedAt(entity.getGradedAt() == null ? null : entity.getGradedAt().toString())
                .build();
    }

    private record AiReviewResult(double score, String feedback) {
    }
}
