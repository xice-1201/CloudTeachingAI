package com.cloudteachingai.course.controller;

import com.cloudteachingai.course.dto.ApiResponse;
import com.cloudteachingai.course.dto.ChapterResponse;
import com.cloudteachingai.course.dto.ChapterUpsertRequest;
import com.cloudteachingai.course.dto.CourseResponse;
import com.cloudteachingai.course.dto.CourseUpsertRequest;
import com.cloudteachingai.course.dto.PageResponse;
import com.cloudteachingai.course.dto.ResourceResponse;
import com.cloudteachingai.course.dto.ResourceUpsertRequest;
import com.cloudteachingai.course.exception.BusinessException;
import com.cloudteachingai.course.service.CourseFacadeService;
import com.cloudteachingai.course.util.JwtUtil;
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
public class CourseController {

    private final CourseFacadeService courseFacadeService;
    private final JwtUtil jwtUtil;

    @GetMapping("/courses")
    public ApiResponse<PageResponse<CourseResponse>> listCourses(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        extractUserId(authorization);
        return ApiResponse.success(courseFacadeService.listCourses(page, pageSize));
    }

    @GetMapping("/courses/enrolled")
    public ApiResponse<PageResponse<CourseResponse>> listEnrolledCourses(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        extractUserId(authorization);
        return ApiResponse.success(courseFacadeService.listEnrolledCourses(page, pageSize));
    }

    @GetMapping("/courses/{id}")
    public ApiResponse<CourseResponse> getCourse(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id) {
        Long userId = extractUserId(authorization);
        return ApiResponse.success(courseFacadeService.getCourse(id, userId));
    }

    @PostMapping("/courses")
    public ApiResponse<CourseResponse> createCourse(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody CourseUpsertRequest request) {
        Long userId = extractUserId(authorization);
        return ApiResponse.success(courseFacadeService.createCourse(request, userId));
    }

    @PutMapping("/courses/{id}")
    public ApiResponse<CourseResponse> updateCourse(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id,
            @Valid @RequestBody CourseUpsertRequest request) {
        Long userId = extractUserId(authorization);
        return ApiResponse.success(courseFacadeService.updateCourse(id, request, userId));
    }

    @DeleteMapping("/courses/{id}")
    public ApiResponse<Void> deleteCourse(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        extractUserId(authorization);
        return ApiResponse.success(null);
    }

    @PostMapping("/courses/{id}/publish")
    public ApiResponse<CourseResponse> publishCourse(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id) {
        Long userId = extractUserId(authorization);
        return ApiResponse.success(courseFacadeService.publishCourse(id, userId));
    }

    @PostMapping("/courses/{id}/enroll")
    public ApiResponse<Void> enrollCourse(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        extractUserId(authorization);
        return ApiResponse.success(null);
    }

    @GetMapping("/courses/{courseId}/chapters")
    public ApiResponse<List<ChapterResponse>> listChapters(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long courseId) {
        extractUserId(authorization);
        return ApiResponse.success(courseFacadeService.listChapters(courseId));
    }

    @PostMapping("/courses/{courseId}/chapters")
    public ApiResponse<ChapterResponse> createChapter(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long courseId,
            @Valid @RequestBody ChapterUpsertRequest request) {
        extractUserId(authorization);
        return ApiResponse.success(courseFacadeService.createChapter(courseId, request));
    }

    @PutMapping("/courses/{courseId}/chapters/{chapterId}")
    public ApiResponse<ChapterResponse> updateChapter(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long courseId,
            @PathVariable Long chapterId,
            @Valid @RequestBody ChapterUpsertRequest request) {
        extractUserId(authorization);
        return ApiResponse.success(courseFacadeService.updateChapter(courseId, chapterId, request));
    }

    @DeleteMapping("/courses/{courseId}/chapters/{chapterId}")
    public ApiResponse<Void> deleteChapter(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long courseId,
            @PathVariable Long chapterId) {
        extractUserId(authorization);
        return ApiResponse.success(null);
    }

    @GetMapping("/chapters/{chapterId}/resources")
    public ApiResponse<List<ResourceResponse>> listResources(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long chapterId) {
        extractUserId(authorization);
        return ApiResponse.success(courseFacadeService.listResources(chapterId));
    }

    @PostMapping("/chapters/{chapterId}/resources")
    public ApiResponse<ResourceResponse> createResource(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long chapterId,
            @Valid @RequestBody ResourceUpsertRequest request) {
        extractUserId(authorization);
        return ApiResponse.success(courseFacadeService.createResource(chapterId, request));
    }

    @PutMapping("/resources/{resourceId}")
    public ApiResponse<ResourceResponse> updateResource(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long resourceId,
            @Valid @RequestBody ResourceUpsertRequest request) {
        extractUserId(authorization);
        return ApiResponse.success(courseFacadeService.updateResource(resourceId, request));
    }

    @DeleteMapping("/resources/{resourceId}")
    public ApiResponse<Void> deleteResource(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long resourceId) {
        extractUserId(authorization);
        return ApiResponse.success(null);
    }

    private Long extractUserId(String authorization) {
        if (authorization == null || authorization.isBlank() || !authorization.startsWith("Bearer ")) {
            throw BusinessException.unauthorized("Missing or invalid Authorization header");
        }

        String token = authorization.substring(7);
        if (!jwtUtil.validateToken(token)) {
            throw BusinessException.unauthorized("Invalid token");
        }

        return jwtUtil.getUserIdFromToken(token);
    }
}
