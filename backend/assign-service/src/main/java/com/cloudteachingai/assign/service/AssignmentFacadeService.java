package com.cloudteachingai.assign.service;

import com.cloudteachingai.assign.client.CourseApiResponse;
import com.cloudteachingai.assign.client.CoursePageResponse;
import com.cloudteachingai.assign.client.CourseServiceClient;
import com.cloudteachingai.assign.client.CourseSummaryResponse;
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
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AssignmentFacadeService {

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final CourseServiceClient courseServiceClient;
    private final ObjectMapper objectMapper;

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
        return toAssignmentResponse(assignmentRepository.save(assignment));
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
        return toSubmissionResponse(submissionRepository.save(submission));
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
        return toSubmissionResponse(submissionRepository.save(submission));
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
                .score(score)
                .feedback(feedback)
                .status(entity.getStatus().name())
                .submittedAt(entity.getSubmittedAt().toString())
                .gradedAt(entity.getGradedAt() == null ? null : entity.getGradedAt().toString())
                .build();
    }
}
