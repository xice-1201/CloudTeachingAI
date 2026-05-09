package com.cloudteachingai.course.controller;

import com.cloudteachingai.course.dto.ApiResponse;
import com.cloudteachingai.course.dto.AnnouncementResponse;
import com.cloudteachingai.course.dto.AnnouncementUpsertRequest;
import com.cloudteachingai.course.dto.ChapterResponse;
import com.cloudteachingai.course.dto.ChapterUpsertRequest;
import com.cloudteachingai.course.dto.CoverUploadResponse;
import com.cloudteachingai.course.dto.CourseResponse;
import com.cloudteachingai.course.dto.CourseUpsertRequest;
import com.cloudteachingai.course.dto.ExerciseGenerateRequest;
import com.cloudteachingai.course.dto.DiscussionPostResponse;
import com.cloudteachingai.course.dto.DiscussionPostUpsertRequest;
import com.cloudteachingai.course.dto.InternalKnowledgePointResponse;
import com.cloudteachingai.course.dto.InternalResourceTaggingContextResponse;
import com.cloudteachingai.course.dto.KnowledgeGraphResponse;
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
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class CourseController {

    private final CourseFacadeService courseFacadeService;
    private final CourseInteractionService courseInteractionService;
    private final CourseCoverStorageService courseCoverStorageService;
    private final ResourceStorageService resourceStorageService;
    private final JwtUtil jwtUtil;

    @GetMapping("/courses")
    public ApiResponse<PageResponse<CourseResponse>> listCourses(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.listCourses(userContext, page, pageSize, keyword, status));
    }

    @GetMapping("/courses/enrolled")
    public ApiResponse<PageResponse<CourseResponse>> listEnrolledCourses(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.listEnrolledCourses(userContext, page, pageSize));
    }

    @GetMapping("/courses/{id}")
    public ApiResponse<CourseResponse> getCourse(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.getCourse(id, userContext));
    }

    @GetMapping("/courses/{courseId}/announcements")
    public ApiResponse<List<AnnouncementResponse>> listAnnouncements(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long courseId) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseInteractionService.listAnnouncements(courseId, userContext));
    }

    @PostMapping("/courses/{courseId}/announcements")
    public ApiResponse<AnnouncementResponse> createAnnouncement(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long courseId,
            @Valid @RequestBody AnnouncementUpsertRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseInteractionService.createAnnouncement(courseId, request, userContext));
    }

    @PutMapping("/announcements/{announcementId}")
    public ApiResponse<AnnouncementResponse> updateAnnouncement(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long announcementId,
            @Valid @RequestBody AnnouncementUpsertRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseInteractionService.updateAnnouncement(announcementId, request, userContext));
    }

    @DeleteMapping("/announcements/{announcementId}")
    public ApiResponse<Void> deleteAnnouncement(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long announcementId) {
        UserContext userContext = extractUserContext(authorization);
        courseInteractionService.deleteAnnouncement(announcementId, userContext);
        return ApiResponse.success(null);
    }

    @GetMapping("/courses/{courseId}/discussions")
    public ApiResponse<List<DiscussionPostResponse>> listDiscussions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long courseId,
            @RequestParam(required = false) Long resourceId) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseInteractionService.listDiscussions(courseId, resourceId, userContext));
    }

    @PostMapping("/courses/{courseId}/discussions")
    public ApiResponse<DiscussionPostResponse> createDiscussion(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long courseId,
            @Valid @RequestBody DiscussionPostUpsertRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseInteractionService.createDiscussion(courseId, request, userContext));
    }

    @DeleteMapping("/discussions/{discussionId}")
    public ApiResponse<Void> deleteDiscussion(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long discussionId) {
        UserContext userContext = extractUserContext(authorization);
        courseInteractionService.deleteDiscussion(discussionId, userContext);
        return ApiResponse.success(null);
    }

    @PostMapping("/courses")
    public ApiResponse<CourseResponse> createCourse(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CourseUpsertRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.createCourse(request, userContext));
    }

    @PostMapping(value = "/course-covers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CoverUploadResponse> uploadCourseCover(
            @RequestHeader(value = "Authorization", required = false) String authorization,
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
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @Valid @RequestBody CourseUpsertRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.updateCourse(id, request, userContext));
    }

    @DeleteMapping("/courses/{id}")
    public ApiResponse<Void> deleteCourse(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        UserContext userContext = extractUserContext(authorization);
        courseFacadeService.deleteCourse(id, userContext);
        return ApiResponse.success(null);
    }

    @PostMapping("/courses/{id}/publish")
    public ApiResponse<CourseResponse> publishCourse(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.publishCourse(id, userContext));
    }

    @PostMapping("/courses/{id}/unpublish")
    public ApiResponse<CourseResponse> unpublishCourse(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.unpublishCourse(id, userContext));
    }

    @PostMapping("/courses/{id}/archive")
    public ApiResponse<CourseResponse> archiveCourse(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.archiveCourse(id, userContext));
    }

    @PostMapping("/courses/{id}/restore")
    public ApiResponse<CourseResponse> restoreCourse(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.restoreCourse(id, userContext));
    }

    @PostMapping("/courses/{id}/enroll")
    public ApiResponse<Void> enrollCourse(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        UserContext userContext = extractUserContext(authorization);
        courseFacadeService.enrollCourse(id, userContext);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/courses/{id}/enroll")
    public ApiResponse<Void> unenrollCourse(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        UserContext userContext = extractUserContext(authorization);
        courseFacadeService.unenrollCourse(id, userContext);
        return ApiResponse.success(null);
    }

    @GetMapping("/courses/{courseId}/chapters")
    public ApiResponse<List<ChapterResponse>> listChapters(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long courseId) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.listChapters(courseId, userContext));
    }

    @PostMapping("/courses/{courseId}/chapters")
    public ApiResponse<ChapterResponse> createChapter(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long courseId,
            @Valid @RequestBody ChapterUpsertRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.createChapter(courseId, request, userContext));
    }

    @PutMapping("/courses/{courseId}/chapters/{chapterId}")
    public ApiResponse<ChapterResponse> updateChapter(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long courseId,
            @PathVariable Long chapterId,
            @Valid @RequestBody ChapterUpsertRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.updateChapter(courseId, chapterId, request, userContext));
    }

    @DeleteMapping("/courses/{courseId}/chapters/{chapterId}")
    public ApiResponse<Void> deleteChapter(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long courseId,
            @PathVariable Long chapterId) {
        UserContext userContext = extractUserContext(authorization);
        courseFacadeService.deleteChapter(courseId, chapterId, userContext);
        return ApiResponse.success(null);
    }

    @GetMapping("/chapters/{chapterId}/resources")
    public ApiResponse<List<ResourceResponse>> listResources(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long chapterId) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.listResources(chapterId, userContext));
    }

    @GetMapping("/resources/{resourceId}")
    public ApiResponse<ResourceResponse> getResource(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long resourceId) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.getResource(resourceId, userContext));
    }

    @GetMapping("/knowledge-points/tree")
    public ApiResponse<List<KnowledgePointNodeResponse>> listKnowledgePointTree(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.listKnowledgePointTree(activeOnly, userContext));
    }

    @GetMapping("/knowledge-points/graph")
    public ApiResponse<KnowledgeGraphResponse> getKnowledgeGraph(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) Long rootId,
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.getKnowledgeGraph(rootId, activeOnly, userContext));
    }

    @PostMapping("/knowledge-points")
    public ApiResponse<KnowledgePointNodeResponse> createKnowledgePoint(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody KnowledgePointUpsertRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.createKnowledgePoint(request, userContext));
    }

    @PutMapping("/knowledge-points/{knowledgePointId}")
    public ApiResponse<KnowledgePointNodeResponse> updateKnowledgePoint(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long knowledgePointId,
            @Valid @RequestBody KnowledgePointUpsertRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.updateKnowledgePoint(knowledgePointId, request, userContext));
    }

    @PostMapping(value = "/resource-tags/suggestions/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<List<ResourceTagSuggestionResponse>> previewResourceTagSuggestions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ResourceTagPreviewRequest request) {
        UserContext userContext = extractUserContext(authorization);
        log.info(
                "Resource tag preview request received: mode=json, userId={}, type={}, titlePresent={}, descriptionPresent={}, fileName={}, sourceUrlPresent={}",
                userContext.userId(),
                request.getType(),
                request.getTitle() != null && !request.getTitle().isBlank(),
                request.getDescription() != null && !request.getDescription().isBlank(),
                request.getFileName(),
                request.getSourceUrl() != null && !request.getSourceUrl().isBlank()
        );
        return ApiResponse.success(courseFacadeService.previewResourceTagSuggestions(request, null, userContext));
    }

    @PostMapping(value = "/resource-tags/suggestions/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<List<ResourceTagSuggestionResponse>> previewResourceTagSuggestionsWithFile(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String sourceUrl,
            @RequestParam(required = false) String fileName,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        UserContext userContext = extractUserContext(authorization);
        log.info(
                "Resource tag preview request received: mode=multipart, userId={}, type={}, titlePresent={}, descriptionPresent={}, fileName={}, sourceUrlPresent={}, filePresent={}, fileSize={}",
                userContext.userId(),
                type,
                title != null && !title.isBlank(),
                description != null && !description.isBlank(),
                fileName,
                sourceUrl != null && !sourceUrl.isBlank(),
                file != null && !file.isEmpty(),
                file == null ? 0 : file.getSize()
        );
        ResourceTagPreviewRequest request = new ResourceTagPreviewRequest();
        request.setTitle(title);
        request.setDescription(description);
        request.setType(type);
        request.setSourceUrl(sourceUrl);
        request.setFileName(fileName);
        return ApiResponse.success(courseFacadeService.previewResourceTagSuggestions(request, file, userContext));
    }

    @GetMapping("/resources/{resourceId}/tag-suggestions")
    public ApiResponse<List<ResourceTagSuggestionResponse>> getResourceTagSuggestions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long resourceId) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.getResourceTagSuggestions(resourceId, userContext));
    }

    @GetMapping("/resources/{resourceId}/content")
    public ResponseEntity<?> getResourceContent(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            @PathVariable Long resourceId,
            @RequestParam(required = false) String accessToken,
            @RequestParam(defaultValue = "false") boolean download) {
        UserContext userContext = extractUserContext(authorization, accessToken);
        CourseFacadeService.ManagedResourceContent content = courseFacadeService.loadManagedResourceContent(resourceId, userContext);
        Resource resource = resourceStorageService.loadAsResource(content.storageKey());
        MediaType mediaType = resourceStorageService.resolveMediaType(content.storageKey());

        String responseFilename = sanitizeResponseFilename(content.title());
        ContentDisposition disposition = download
                ? ContentDisposition.attachment().filename(responseFilename, StandardCharsets.UTF_8).build()
                : ContentDisposition.inline().filename(responseFilename, StandardCharsets.UTF_8).build();

        if (!download && rangeHeader != null && !rangeHeader.isBlank()) {
            try {
                List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
                if (!ranges.isEmpty()) {
                    PartialResource partialResource = buildPartialResource(resource, ranges.getFirst());
                    return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                            .contentType(mediaType)
                            .contentLength(partialResource.contentLength())
                            .cacheControl(CacheControl.noStore())
                            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                            .header(HttpHeaders.CONTENT_RANGE, partialResource.contentRange())
                            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                            .body(partialResource.resource());
                }
            } catch (IllegalArgumentException ex) {
                log.warn("Invalid resource range request: resourceId={}, range={}", resourceId, rangeHeader);
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .cacheControl(CacheControl.noStore())
                        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                        .build();
            } catch (IOException ex) {
                throw BusinessException.internal("Failed to stream resource file", ex);
            }
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }

    @PostMapping("/chapters/{chapterId}/resources")
    public ApiResponse<ResourceResponse> createResource(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long chapterId,
            @Valid @RequestBody ResourceUpsertRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.createResource(chapterId, request, userContext));
    }

    @PostMapping("/resources/exercises/generate")
    public ApiResponse<List<ResourceResponse.ExerciseQuestionResponse>> generateExerciseQuestions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ExerciseGenerateRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.generateExerciseQuestions(request, userContext));
    }

    @PostMapping(value = "/resource-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ResourceUploadResponse> uploadResourceFile(
            @RequestHeader(value = "Authorization", required = false) String authorization,
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
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long resourceId,
            @Valid @RequestBody ResourceUpsertRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.updateResource(resourceId, request, userContext));
    }

    @DeleteMapping("/resources/{resourceId}")
    public ApiResponse<Void> deleteResource(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long resourceId) {
        UserContext userContext = extractUserContext(authorization);
        courseFacadeService.deleteResource(resourceId, userContext);
        return ApiResponse.success(null);
    }

    @PatchMapping("/resources/{resourceId}/tags")
    public ApiResponse<ResourceResponse> confirmResourceTags(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long resourceId,
            @RequestBody ResourceTagConfirmRequest request) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.confirmResourceTags(resourceId, request, userContext));
    }

    @PostMapping("/resources/{resourceId}/tagging/retry")
    public ApiResponse<ResourceResponse> retryResourceTagging(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long resourceId) {
        UserContext userContext = extractUserContext(authorization);
        return ApiResponse.success(courseFacadeService.retryResourceTagging(resourceId, userContext));
    }

    @GetMapping("/internal/resources/{resourceId}/tagging-context")
    public ApiResponse<InternalResourceTaggingContextResponse> getResourceTaggingContext(@PathVariable Long resourceId) {
        return ApiResponse.success(courseFacadeService.getResourceTaggingContext(resourceId));
    }

    @GetMapping("/internal/knowledge-points/leaf")
    public ApiResponse<List<InternalKnowledgePointResponse>> listLeafKnowledgePoints() {
        return ApiResponse.success(courseFacadeService.listLeafKnowledgePointsForTagging());
    }

    @GetMapping("/internal/knowledge-points/attachable")
    public ApiResponse<List<InternalKnowledgePointResponse>> listAttachableKnowledgePoints() {
        return ApiResponse.success(courseFacadeService.listLeafKnowledgePointsForTagging());
    }

    @GetMapping("/internal/courses/{courseId}/student-ids")
    public ApiResponse<List<Long>> listCourseStudentIds(@PathVariable Long courseId) {
        return ApiResponse.success(courseFacadeService.listCourseStudentIds(courseId));
    }

    private UserContext extractUserContext(String authorization) {
        return extractUserContext(authorization, null);
    }

    private String sanitizeResponseFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "resource";
        }
        String sanitized = filename.replaceAll("[\\r\\n\"]", "_").trim();
        return sanitized.isBlank() ? "resource" : sanitized;
    }

    private PartialResource buildPartialResource(Resource resource, HttpRange range) throws IOException {
        long totalLength = resource.contentLength();
        long start = range.getRangeStart(totalLength);
        long end = Math.min(range.getRangeEnd(totalLength), totalLength - 1);
        if (start >= totalLength || start > end) {
            throw new IllegalArgumentException("Requested range is not satisfiable");
        }

        long rangeLength = end - start + 1;
        InputStream inputStream = resource.getInputStream();
        skipFully(inputStream, start);
        InputStreamResource body = new InputStreamResource(new LimitedInputStream(inputStream, rangeLength));
        return new PartialResource(body, rangeLength, "bytes " + start + "-" + end + "/" + totalLength);
    }

    private void skipFully(InputStream inputStream, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = inputStream.skip(remaining);
            if (skipped <= 0) {
                if (inputStream.read() == -1) {
                    throw new IOException("Cannot skip to requested resource range");
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    private UserContext extractUserContext(String authorization, String accessToken) {
        if ((authorization == null || authorization.isBlank()) && accessToken != null && !accessToken.isBlank()) {
            authorization = "Bearer " + accessToken.trim();
        }
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

    private record PartialResource(InputStreamResource resource, long contentLength, String contentRange) {
    }

    private static class LimitedInputStream extends FilterInputStream {
        private long remaining;

        LimitedInputStream(InputStream inputStream, long limit) {
            super(inputStream);
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int value = super.read();
            if (value != -1) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int limitedLength = (int) Math.min(length, remaining);
            int read = super.read(buffer, offset, limitedLength);
            if (read != -1) {
                remaining -= read;
            }
            return read;
        }
    }

    private ResourceType parseResourceType(String type) {
        try {
            return ResourceType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw BusinessException.badRequest("Invalid resource type");
        }
    }
}
