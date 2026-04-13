package com.cloudteachingai.learn.controller;

import com.cloudteachingai.learn.dto.ApiResponse;
import com.cloudteachingai.learn.dto.CourseProgressResponse;
import com.cloudteachingai.learn.dto.LearningProgressResponse;
import com.cloudteachingai.learn.dto.UpdateLearningProgressRequest;
import com.cloudteachingai.learn.exception.BusinessException;
import com.cloudteachingai.learn.service.LearnFacadeService;
import com.cloudteachingai.learn.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    public ApiResponse<List<Object>> getAbilityMap(@RequestHeader("Authorization") String authorization) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(learnFacadeService.getAbilityMap(userContext));
    }

    @GetMapping("/path")
    public ApiResponse<Object> getLearningPath(@RequestHeader("Authorization") String authorization) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(learnFacadeService.getLearningPath(userContext));
    }

    @PostMapping("/path/generate")
    public ApiResponse<Object> generateLearningPath(@RequestHeader("Authorization") String authorization) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(learnFacadeService.generateLearningPath(userContext));
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
