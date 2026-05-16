package com.cloudteachingai.learn.controller;

import com.cloudteachingai.learn.dto.ApiResponse;
import com.cloudteachingai.learn.dto.AbilityMapResponse;
import com.cloudteachingai.learn.dto.AbilityTestAnswerRequest;
import com.cloudteachingai.learn.dto.AbilityTestAnswerResponse;
import com.cloudteachingai.learn.dto.AbilityTestStartRequest;
import com.cloudteachingai.learn.dto.AbilityTestStartResponse;
import com.cloudteachingai.learn.dto.CourseProgressResponse;
import com.cloudteachingai.learn.dto.LearningProgressResponse;
import com.cloudteachingai.learn.dto.LearningPathResponse;
import com.cloudteachingai.learn.dto.MentorAdviceGenerateRequest;
import com.cloudteachingai.learn.dto.MentorAdviceGenerateResponse;
import com.cloudteachingai.learn.dto.TeacherDashboardResponse;
import com.cloudteachingai.learn.dto.UpdateLearningProgressRequest;
import com.cloudteachingai.learn.exception.BusinessException;
import com.cloudteachingai.learn.service.LearnFacadeService;
import com.cloudteachingai.learn.service.LearningAccountCleanupService;
import com.cloudteachingai.learn.util.JwtUtil;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/learn")
@RequiredArgsConstructor
public class LearnController {

    private final LearnFacadeService learnFacadeService;
    private final LearningAccountCleanupService learningAccountCleanupService;
    private final JwtUtil jwtUtil;

    @GetMapping("/progress/{resourceId}")
    public ApiResponse<LearningProgressResponse> getProgress(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long resourceId) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(learnFacadeService.getResourceProgress(resourceId, userContext));
    }

    @PutMapping("/progress/{resourceId}")
    public ApiResponse<LearningProgressResponse> updateProgress(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long resourceId,
            @Valid @RequestBody UpdateLearningProgressRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(learnFacadeService.updateResourceProgress(resourceId, request, authorization, userContext));
    }

    @GetMapping("/courses/{courseId}/progress")
    public ApiResponse<CourseProgressResponse> getCourseProgress(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long courseId) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(learnFacadeService.getCourseProgress(courseId, authorization, userContext));
    }

    @GetMapping("/ability-map")
    public ApiResponse<List<AbilityMapResponse>> getAbilityMap(@RequestHeader("Authorization") String authorization) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(learnFacadeService.getAbilityMap(authorization, userContext));
    }

    @GetMapping("/students/{studentId}/ability-map")
    public ApiResponse<List<AbilityMapResponse>> getMentoredStudentAbilityMap(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long studentId) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(learnFacadeService.getMentoredStudentAbilityMap(studentId, authorization, userContext));
    }

    @PostMapping("/students/{studentId}/advice/generate")
    public ApiResponse<MentorAdviceGenerateResponse> generateMentorAdvice(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long studentId,
            @Valid @RequestBody(required = false) MentorAdviceGenerateRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(learnFacadeService.generateMentorAdvice(studentId, request, userContext));
    }

    @PostMapping("/ability-test/start")
    public ApiResponse<AbilityTestStartResponse> startAbilityTest(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody AbilityTestStartRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(learnFacadeService.startAbilityTest(request, authorization, userContext));
    }

    @PostMapping("/ability-test/{sessionId}/answer")
    public ApiResponse<AbilityTestAnswerResponse> submitAbilityTestAnswer(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long sessionId,
            @Valid @RequestBody AbilityTestAnswerRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(learnFacadeService.submitAbilityTestAnswer(sessionId, request, authorization, userContext));
    }

    @GetMapping("/path")
    public ApiResponse<LearningPathResponse> getLearningPath(@RequestHeader("Authorization") String authorization) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(learnFacadeService.getLearningPath(authorization, userContext));
    }

    @PostMapping("/path/generate")
    public ApiResponse<LearningPathResponse> generateLearningPath(@RequestHeader("Authorization") String authorization) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(learnFacadeService.generateLearningPath(authorization, userContext));
    }

    @GetMapping("/teacher/dashboard")
    public ApiResponse<TeacherDashboardResponse> getTeacherDashboard(@RequestHeader("Authorization") String authorization) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(learnFacadeService.getTeacherDashboard(authorization, userContext));
    }

    @DeleteMapping("/internal/users/{userId}/learning-data")
    public ApiResponse<Void> deleteUserLearningData(@PathVariable Long userId) {
        learningAccountCleanupService.deleteStudentLearningData(userId);
        return ApiResponse.success(null);
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
