package com.cloudteachingai.course.controller;

import com.cloudteachingai.course.dto.ApiResponse;
import com.cloudteachingai.course.dto.AnnouncementResponse;
import com.cloudteachingai.course.dto.AnnouncementUpsertRequest;
import com.cloudteachingai.course.dto.ChapterResponse;
import com.cloudteachingai.course.dto.ChapterUpsertRequest;
import com.cloudteachingai.course.dto.CoverUploadResponse;
import com.cloudteachingai.course.dto.CourseResponse;
import com.cloudteachingai.course.dto.CourseUpsertRequest;
import com.cloudteachingai.course.dto.DiscussionPostResponse;
import com.cloudteachingai.course.dto.DiscussionPostUpsertRequest;
import com.cloudteachingai.course.dto.InternalKnowledgePointResponse;
import com.cloudteachingai.course.dto.InternalResourceTaggingContextResponse;
import com.cloudteachingai.course.dto.KnowledgePointNodeResponse;
import com.cloudteachingai.course.dto.KnowledgePointUpsertRequest;
import com.cloudteachingai.course.dto.PageResponse;
import com.cloudteachingai.course.dto.ResourceResponse;
import com.cloudteachingai.course.dto.ResourceTagConfirmRequest;
import com.cloudteachingai.course.dto.ResourceTagPreviewRequest;
import com.cloudteachingai.course.dto.ResourceTagSuggestionResponse;
import com.cloudteachingai.course.dto.ResourceUploadResponse;
import com.cloudteachingai.course.dto.ResourceUpsertRequest;
import com.cloudteachingai.course.entity.enums.ResourceType;
import com.cloudteachingai.course.exception.BusinessException;
import com.cloudteachingai.course.service.CourseCoverStorageService;
import com.cloudteachingai.course.service.CourseFacadeService;
import com.cloudteachingai.course.service.CourseInteractionService;
import com.cloudteachingai.course.service.ResourceStorageService;
import com.cloudteachingai.course.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CourseController {

    private final CourseFacadeService courseFacadeService;
    private final CourseInteractionService courseInteractionService;
    private final CourseCoverStorageService courseCoverStorageService;
    private final ResourceStorageService resourceStorageService;
    private final JwtUtil jwtUtil;

    @GetMapping("/courses")
    public ApiResponse<PageResponse<CourseResponse>> listCourses(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.listCourses(userContext, page, pageSize, keyword, status));
    }

    @GetMapping("/courses/enrolled")
    public ApiResponse<PageResponse<CourseResponse>> listEnrolledCourses(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.listEnrolledCourses(userContext, page, pageSize));
    }

    @GetMapping("/courses/{id}")
    public ApiResponse<CourseResponse> getCourse(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.getCourse(id, userContext));
    }

    @GetMapping("/courses/{courseId}/announcements")
    public ApiResponse<List<AnnouncementResponse>> listAnnouncements(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long courseId) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseInteractionService.listAnnouncements(courseId, userContext));
    }

    @PostMapping("/courses/{courseId}/announcements")
    public ApiResponse<AnnouncementResponse> createAnnouncement(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long courseId,
            @Valid @RequestBody AnnouncementUpsertRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseInteractionService.createAnnouncement(courseId, request, userContext));
    }

    @PutMapping("/announcements/{announcementId}")
    public ApiResponse<AnnouncementResponse> updateAnnouncement(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long announcementId,
            @Valid @RequestBody AnnouncementUpsertRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseInteractionService.updateAnnouncement(announcementId, request, userContext));
    }

    @DeleteMapping("/announcements/{announcementId}")
    public ApiResponse<Void> deleteAnnouncement(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long announcementId) {
        UserContext userContext = extractUserContext(authorization);
        courseInteractionService.deleteAnnouncement(announcementId, userContext);
        return ApiResponse.success(null);
    }

    @GetMapping("/courses/{courseId}/discussions")
    public ApiResponse<List<DiscussionPostResponse>> listDiscussions(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long courseId,
            @RequestParam(required = false) Long resourceId) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseInteractionService.listDiscussions(courseId, resourceId, userContext));
    }

    @PostMapping("/courses/{courseId}/discussions")
    public ApiResponse<DiscussionPostResponse> createDiscussion(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long courseId,
            @Valid @RequestBody DiscussionPostUpsertRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseInteractionService.createDiscussion(courseId, request, userContext));
    }

    @DeleteMapping("/discussions/{discussionId}")
    public ApiResponse<Void> deleteDiscussion(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long discussionId) {
        UserContext userContext = extractUserContext(authorization);
        courseInteractionService.deleteDiscussion(discussionId, userContext);
        return ApiResponse.success(null);
    }

    @PostMapping("/courses")
    public ApiResponse<CourseResponse> createCourse(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody CourseUpsertRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.createCourse(request, userContext));
    }

    @PostMapping(value = "/course-covers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CoverUploadResponse> uploadCourseCover(
            @RequestHeader("Authorization") String authorization,
            @RequestPart("file") MultipartFile file) {
        UserContext userContext = extractUserContext(authorization);
        if (!"TEACHER".equals(userContext.role()) && !"ADMIN".equals(userContext.role())) {
            throw BusinessException.forbidden("Current role cannot upload course covers");
        }
        return ApiResponse.success(CoverUploadResponse.builder()
                .url(courseCoverStorageService.store(file))
                .build());
    }

    @GetMapping("/course-covers/{filename:.+}")
    public ResponseEntity<Resource> getCourseCover(@PathVariable String filename) {
        return ResponseEntity.ok()
                .contentType(courseCoverStorageService.resolveMediaType(filename))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .body(courseCoverStorageService.loadAsResource(filename));
    }

    @PutMapping("/courses/{id}")
    public ApiResponse<CourseResponse> updateCourse(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id,
            @Valid @RequestBody CourseUpsertRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.updateCourse(id, request, userContext));
    }

    @DeleteMapping("/courses/{id}")
    public ApiResponse<Void> deleteCourse(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id) {
        UserContext userContext = extractUserContext(authorization);
        courseFacadeService.deleteCourse(id, userContext);
        return ApiResponse.success(null);
    }

    @PostMapping("/courses/{id}/publish")
    public ApiResponse<CourseResponse> publishCourse(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.publishCourse(id, userContext));
    }

    @PostMapping("/courses/{id}/unpublish")
    public ApiResponse<CourseResponse> unpublishCourse(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.unpublishCourse(id, userContext));
    }

    @PostMapping("/courses/{id}/archive")
    public ApiResponse<CourseResponse> archiveCourse(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.archiveCourse(id, userContext));
    }

    @PostMapping("/courses/{id}/restore")
    public ApiResponse<CourseResponse> restoreCourse(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.restoreCourse(id, userContext));
    }

    @PostMapping("/courses/{id}/enroll")
    public ApiResponse<Void> enrollCourse(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id) {
        UserContext userContext = extractUserContext(authorization);
        courseFacadeService.enrollCourse(id, userContext);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/courses/{id}/enroll")
    public ApiResponse<Void> unenrollCourse(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id) {
        UserContext userContext = extractUserContext(authorization);
        courseFacadeService.unenrollCourse(id, userContext);
        return ApiResponse.success(null);
    }

    @GetMapping("/courses/{courseId}/chapters")
    public ApiResponse<List<ChapterResponse>> listChapters(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long courseId) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.listChapters(courseId, userContext));
    }

    @PostMapping("/courses/{courseId}/chapters")
    public ApiResponse<ChapterResponse> createChapter(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long courseId,
            @Valid @RequestBody ChapterUpsertRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.createChapter(courseId, request, userContext));
    }

    @PutMapping("/courses/{courseId}/chapters/{chapterId}")
    public ApiResponse<ChapterResponse> updateChapter(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long courseId,
            @PathVariable Long chapterId,
            @Valid @RequestBody ChapterUpsertRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.updateChapter(courseId, chapterId, request, userContext));
    }

    @DeleteMapping("/courses/{courseId}/chapters/{chapterId}")
    public ApiResponse<Void> deleteChapter(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long courseId,
            @PathVariable Long chapterId) {
        UserContext userContext = extractUserContext(authorization);
        courseFacadeService.deleteChapter(courseId, chapterId, userContext);
        return ApiResponse.success(null);
    }

    @GetMapping("/chapters/{chapterId}/resources")
    public ApiResponse<List<ResourceResponse>> listResources(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long chapterId) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.listResources(chapterId, userContext));
    }

    @GetMapping("/resources/{resourceId}")
    public ApiResponse<ResourceResponse> getResource(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long resourceId) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.getResource(resourceId, userContext));
    }

    @GetMapping("/knowledge-points/tree")
    public ApiResponse<List<KnowledgePointNodeResponse>> listKnowledgePointTree(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.listKnowledgePointTree(activeOnly, userContext));
    }

    @PostMapping("/knowledge-points")
    public ApiResponse<KnowledgePointNodeResponse> createKnowledgePoint(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody KnowledgePointUpsertRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.createKnowledgePoint(request, userContext));
    }

    @PutMapping("/knowledge-points/{knowledgePointId}")
    public ApiResponse<KnowledgePointNodeResponse> updateKnowledgePoint(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long knowledgePointId,
            @Valid @RequestBody KnowledgePointUpsertRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.updateKnowledgePoint(knowledgePointId, request, userContext));
    }

    @PostMapping("/resource-tags/suggestions/preview")
    public ApiResponse<List<ResourceTagSuggestionResponse>> previewResourceTagSuggestions(
            @RequestHeader("Authorization") String authorization,
            @RequestBody ResourceTagPreviewRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.previewResourceTagSuggestions(request, userContext));
    }

    @GetMapping("/resources/{resourceId}/tag-suggestions")
    public ApiResponse<List<ResourceTagSuggestionResponse>> getResourceTagSuggestions(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long resourceId) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.getResourceTagSuggestions(resourceId, userContext));
    }

    @GetMapping("/resources/{resourceId}/content")
    public ResponseEntity<Resource> getResourceContent(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long resourceId,
            @RequestParam(defaultValue = "false") boolean download) {
        UserContext userContext = extractUserContext(authorization);
        CourseFacadeService.ManagedResourceContent content = courseFacadeService.loadManagedResourceContent(resourceId, userContext);

        ContentDisposition disposition = download
                ? ContentDisposition.attachment().filename(content.title()).build()
                : ContentDisposition.inline().filename(content.title()).build();

        return ResponseEntity.ok()
                .contentType(resourceStorageService.resolveMediaType(content.storageKey()))
                .cacheControl(CacheControl.noStore())
                .header("Content-Disposition", disposition.toString())
                .body(resourceStorageService.loadAsResource(content.storageKey()));
    }

    @PostMapping("/chapters/{chapterId}/resources")
    public ApiResponse<ResourceResponse> createResource(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long chapterId,
            @Valid @RequestBody ResourceUpsertRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.createResource(chapterId, request, userContext));
    }

    @PostMapping(value = "/resource-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ResourceUploadResponse> uploadResourceFile(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("type") String type,
            @RequestPart("file") MultipartFile file) {
        UserContext userContext = extractUserContext(authorization);
        if (!"TEACHER".equals(userContext.role()) && !"ADMIN".equals(userContext.role())) {
            throw BusinessException.forbidden("Current role cannot upload course resources");
        }

        ResourceType resourceType = parseResourceType(type);
        String storageKey = resourceStorageService.store(file, resourceType);
        return ApiResponse.success(ResourceUploadResponse.builder()
                .storageKey(storageKey)
                .fileName(file.getOriginalFilename())
                .size(file.getSize())
                .build());
    }

    @PutMapping("/resources/{resourceId}")
    public ApiResponse<ResourceResponse> updateResource(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long resourceId,
            @Valid @RequestBody ResourceUpsertRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.updateResource(resourceId, request, userContext));
    }

    @DeleteMapping("/resources/{resourceId}")
    public ApiResponse<Void> deleteResource(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long resourceId) {
        UserContext userContext = extractUserContext(authorization);
        courseFacadeService.deleteResource(resourceId, userContext);
        return ApiResponse.success(null);
    }

    @PatchMapping("/resources/{resourceId}/tags")
    public ApiResponse<ResourceResponse> confirmResourceTags(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long resourceId,
            @RequestBody ResourceTagConfirmRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.confirmResourceTags(resourceId, request, userContext));
    }

    @GetMapping("/internal/resources/{resourceId}/tagging-context")
    public ApiResponse<InternalResourceTaggingContextResponse> getResourceTaggingContext(@PathVariable Long resourceId) {
        return ApiResponse.success(courseFacadeService.getResourceTaggingContext(resourceId));
    }

    @GetMapping("/internal/knowledge-points/leaf")
    public ApiResponse<List<InternalKnowledgePointResponse>> listLeafKnowledgePoints() {
        return ApiResponse.success(courseFacadeService.listLeafKnowledgePointsForTagging());
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

    private ResourceType parseResourceType(String type) {
        try {
            return ResourceType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw BusinessException.badRequest("Invalid resource type");
        }
    }
}
