package com.cloudteachingai.assign.controller;

import com.cloudteachingai.assign.dto.ApiResponse;
import com.cloudteachingai.assign.dto.AssignmentResponse;
import com.cloudteachingai.assign.dto.AssignmentUpsertRequest;
import com.cloudteachingai.assign.dto.PageResponse;
import com.cloudteachingai.assign.dto.SubmissionCreateRequest;
import com.cloudteachingai.assign.dto.SubmissionResponse;
import com.cloudteachingai.assign.dto.SubmissionReviewRequest;
import com.cloudteachingai.assign.exception.BusinessException;
import com.cloudteachingai.assign.service.AssignmentFacadeService;
import com.cloudteachingai.assign.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentFacadeService assignmentFacadeService;
    private final JwtUtil jwtUtil;

    @GetMapping("/courses/{courseId}/assignments")
    public ApiResponse<PageResponse<AssignmentResponse>> listAssignments(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long courseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(assignmentFacadeService.listAssignments(courseId, page, pageSize, authorization, userContext));
    }

    @PostMapping("/courses/{courseId}/assignments")
    public ApiResponse<AssignmentResponse> createAssignment(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long courseId,
            @Valid @RequestBody AssignmentUpsertRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(assignmentFacadeService.createAssignment(courseId, request, authorization, userContext));
    }

    @GetMapping("/assignments/{assignmentId}")
    public ApiResponse<AssignmentResponse> getAssignment(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long assignmentId) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(assignmentFacadeService.getAssignment(assignmentId, authorization, userContext));
    }

    @PutMapping("/assignments/{assignmentId}")
    public ApiResponse<AssignmentResponse> updateAssignment(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long assignmentId,
            @Valid @RequestBody AssignmentUpsertRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(assignmentFacadeService.updateAssignment(assignmentId, request, authorization, userContext));
    }

    @DeleteMapping("/assignments/{assignmentId}")
    public ApiResponse<Void> deleteAssignment(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long assignmentId) {
        UserContext userContext = extractUserContext(authorization);
        assignmentFacadeService.deleteAssignment(assignmentId, authorization, userContext);
        return ApiResponse.success(null);
    }

    @PostMapping("/assignments/{assignmentId}/submissions")
    public ApiResponse<SubmissionResponse> submitAssignment(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long assignmentId,
            @Valid @RequestBody SubmissionCreateRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(assignmentFacadeService.submitAssignment(assignmentId, request, authorization, userContext));
    }

    @GetMapping("/assignments/{assignmentId}/submissions/me")
    public ApiResponse<SubmissionResponse> getMySubmission(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long assignmentId) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(assignmentFacadeService.getMySubmission(assignmentId, authorization, userContext));
    }

    @GetMapping("/assignments/{assignmentId}/submissions")
    public ApiResponse<PageResponse<SubmissionResponse>> listSubmissions(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long assignmentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(assignmentFacadeService.listSubmissions(assignmentId, page, pageSize, authorization, userContext));
    }

    @PutMapping("/submissions/{submissionId}/review")
    public ApiResponse<SubmissionResponse> reviewSubmission(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long submissionId,
            @Valid @RequestBody SubmissionReviewRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(assignmentFacadeService.reviewSubmission(submissionId, request, authorization, userContext));
    }

    @GetMapping("/assignments/pending")
    public ApiResponse<List<AssignmentResponse>> listPendingAssignments(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(defaultValue = "5") int pageSize) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(assignmentFacadeService.listPendingAssignments(pageSize, authorization, userContext));
    }

    private UserContext extractUserContext(String authorization) {
        if (authorization == null || authorization.isBlank() || !authorization.startsWith("Bearer ")) {
            throw BusinessException.unauthorized("Missing or invalid Authorization header");
        }

        String token = authorization.substring(7);
        if (!jwtUtil.validateToken(token)) {
            throw BusinessException.unauthorized("Invalid token");
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        String role = jwtUtil.getRoleFromToken(token);
        if (userId == null || role == null || role.isBlank()) {
            throw BusinessException.unauthorized("Invalid token claims");
        }
        return new UserContext(userId, role);
    }

    public record UserContext(Long userId, String role) {
    }
}
