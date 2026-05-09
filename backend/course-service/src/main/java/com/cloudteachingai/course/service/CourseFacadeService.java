package com.cloudteachingai.course.service;

import com.cloudteachingai.course.client.CreateAdminAuditLogRequest;
import com.cloudteachingai.course.client.UserServiceClient;
import com.cloudteachingai.course.client.UserServiceResponse;
import com.cloudteachingai.course.client.ResourceTagAgentClient;
import com.cloudteachingai.course.controller.CourseController.UserContext;
import com.cloudteachingai.course.dto.ChapterResponse;
import com.cloudteachingai.course.dto.ChapterUpsertRequest;
import com.cloudteachingai.course.dto.CourseResponse;
import com.cloudteachingai.course.dto.CourseUpsertRequest;
import com.cloudteachingai.course.dto.ExerciseGenerateRequest;
import com.cloudteachingai.course.dto.InternalKnowledgePointResponse;
import com.cloudteachingai.course.dto.InternalResourceTaggingContextResponse;
import com.cloudteachingai.course.dto.KnowledgeGraphEdgeResponse;
import com.cloudteachingai.course.dto.KnowledgeGraphNodeResponse;
import com.cloudteachingai.course.dto.KnowledgeGraphResponse;
import com.cloudteachingai.course.dto.KnowledgePointNodeResponse;
import com.cloudteachingai.course.dto.KnowledgePointUpsertRequest;
import com.cloudteachingai.course.dto.PageResponse;
import com.cloudteachingai.course.dto.ResourceKnowledgePointResponse;
import com.cloudteachingai.course.dto.ResourceResponse;
import com.cloudteachingai.course.dto.ResourceTagConfirmRequest;
import com.cloudteachingai.course.dto.ResourceTagPreviewRequest;
import com.cloudteachingai.course.dto.ResourceTagResponse;
import com.cloudteachingai.course.dto.ResourceTagSuggestionResponse;
import com.cloudteachingai.course.dto.ResourceUpsertRequest;
import com.cloudteachingai.course.event.CourseUpdatedEvent;
import com.cloudteachingai.course.event.EventTopics;
import com.cloudteachingai.course.event.KnowledgePointUpdatedEvent;
import com.cloudteachingai.course.event.NotificationSendEvent;
import com.cloudteachingai.course.event.ResourceTaggedKnowledgePointEvent;
import com.cloudteachingai.course.event.ResourceTaggedEvent;
import com.cloudteachingai.course.event.ResourceUploadedEvent;
import com.cloudteachingai.course.entity.ChapterEntity;
import com.cloudteachingai.course.entity.CourseEntity;
import com.cloudteachingai.course.entity.CourseVisibleStudentEntity;
import com.cloudteachingai.course.entity.EnrollmentEntity;
import com.cloudteachingai.course.entity.KnowledgePointEntity;
import com.cloudteachingai.course.entity.ResourceEntity;
import com.cloudteachingai.course.entity.ResourceKnowledgePointEntity;
import com.cloudteachingai.course.entity.ResourceTagEntity;
import com.cloudteachingai.course.entity.enums.CourseStatus;
import com.cloudteachingai.course.entity.enums.CourseVisibilityType;
import com.cloudteachingai.course.entity.enums.KnowledgePointType;
import com.cloudteachingai.course.entity.enums.ResourceStatus;
import com.cloudteachingai.course.entity.enums.ResourceTagSource;
import com.cloudteachingai.course.entity.enums.ResourceTaggingStatus;
import com.cloudteachingai.course.entity.enums.ResourceType;
import com.cloudteachingai.course.exception.BusinessException;
import com.cloudteachingai.course.repository.ChapterRepository;
import com.cloudteachingai.course.repository.CourseRepository;
import com.cloudteachingai.course.repository.CourseVisibleStudentRepository;
import com.cloudteachingai.course.repository.EnrollmentRepository;
import com.cloudteachingai.course.repository.KnowledgePointRepository;
import com.cloudteachingai.course.repository.ResourceKnowledgePointRepository;
import com.cloudteachingai.course.repository.ResourceRepository;
import com.cloudteachingai.course.repository.ResourceTagRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseFacadeService {

    private static final int MAX_TAG_SUGGESTIONS = 8;
    private static final int MAX_EXERCISE_QUESTIONS = 20;
    private static final String AUDIT_TARGET_COURSE = "COURSE";
    private static final String EXERCISE_STORAGE_KEY = "exercise://configured";

    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final ResourceRepository resourceRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseVisibleStudentRepository courseVisibleStudentRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final ResourceKnowledgePointRepository resourceKnowledgePointRepository;
    private final ResourceTagRepository resourceTagRepository;
    private final UserServiceClient userServiceClient;
    private final CourseCoverStorageService courseCoverStorageService;
    private final ResourceStorageService resourceStorageService;
    private final ResourceTagSuggestionService resourceTagSuggestionService;
    private final ResourceTagAgentClient resourceTagAgentClient;
    private final OutboxService outboxService;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;

    public PageResponse<CourseResponse> listCourses(UserContext userContext, int page, int pageSize, String keyword, String status) {
        Pageable pageable = PageRequest.of(toPageIndex(page), toPageSize(pageSize), Sort.by(Sort.Direction.DESC, "updatedAt"));
        Specification<CourseEntity> specification = Specification.where(withKeyword(keyword))
                .and(withStatus(status))
                .and(withVisibility(userContext));
        Page<CourseEntity> result = courseRepository.findAll(specification, pageable);

        Map<Long, String> teacherNames = resolveTeacherNames(result.getContent());
        Map<Long, List<Long>> visibleStudentIdsByCourse = loadVisibleStudentIds(result.getContent());

        return PageResponse.<CourseResponse>builder()
                .items(result.getContent().stream()
                        .map(course -> toCourseResponse(course, teacherNames, visibleStudentIdsByCourse, canManageCourse(course, userContext)))
                        .toList())
                .total((int) result.getTotalElements())
                .page(page)
                .pageSize(pageSize)
                .build();
    }

    public PageResponse<CourseResponse> listEnrolledCourses(UserContext userContext, int page, int pageSize) {
        assertRole(userContext, "STUDENT");

        Pageable pageable = PageRequest.of(toPageIndex(page), toPageSize(pageSize), Sort.by(Sort.Direction.DESC, "enrolledAt"));
        Page<EnrollmentEntity> enrollmentPage = enrollmentRepository.findByStudentIdAndCourseStatus(
                userContext.userId(),
                CourseStatus.PUBLISHED,
                pageable
        );
        List<Long> courseIds = enrollmentPage.getContent().stream().map(EnrollmentEntity::getCourseId).toList();
        List<CourseEntity> courses = courseIds.isEmpty()
                ? Collections.emptyList()
                : courseRepository.findAllByIdInOrderByUpdatedAtDesc(courseIds);
        Map<Long, CourseEntity> courseById = courses.stream()
                .collect(java.util.stream.Collectors.toMap(CourseEntity::getId, course -> course));
        Map<Long, String> teacherNames = resolveTeacherNames(courses);
        Map<Long, List<Long>> visibleStudentIdsByCourse = loadVisibleStudentIds(courses);

        List<CourseResponse> items = courseIds.stream()
                .map(courseById::get)
                .filter(Objects::nonNull)
                .map(course -> toCourseResponse(course, teacherNames, visibleStudentIdsByCourse, false))
                .toList();

        return PageResponse.<CourseResponse>builder()
                .items(items)
                .total((int) enrollmentPage.getTotalElements())
                .page(page)
                .pageSize(pageSize)
                .build();
    }

    public CourseResponse getCourse(Long id, UserContext userContext) {
        CourseEntity course = requireSummaryVisibleCourse(id, userContext);
        Map<Long, List<Long>> visibleStudentIdsByCourse = loadVisibleStudentIds(List.of(course));
        return toCourseResponse(
                course,
                Map.of(course.getTeacherId(), resolveTeacherName(course.getTeacherId())),
                visibleStudentIdsByCourse,
                canManageCourse(course, userContext)
        );
    }

    @Transactional
    public CourseResponse createCourse(CourseUpsertRequest request, UserContext userContext) {
        assertRole(userContext, "TEACHER", "ADMIN");

        CourseVisibilityType visibilityType = parseVisibilityType(request.getVisibilityType());
        List<Long> visibleStudentIds = normalizeVisibleStudentIds(request.getVisibleStudentIds());
        validateVisibleStudents(visibilityType, visibleStudentIds);

        CourseEntity course = CourseEntity.builder()
                .teacherId(userContext.userId())
                .title(request.getTitle().trim())
                .description(request.getDescription().trim())
                .coverKey(normalizeBlank(request.getCoverImage()))
                .status(CourseStatus.DRAFT)
                .visibilityType(visibilityType)
                .build();

        CourseEntity saved = courseRepository.save(course);
        syncVisibleStudents(saved.getId(), visibilityType, visibleStudentIds);
        publishCourseUpdatedEvent(saved, "CREATED");
        recordCourseAudit(userContext, saved, "COURSE_CREATED", "Created course");

        return toCourseResponse(
                saved,
                Map.of(saved.getTeacherId(), resolveTeacherName(saved.getTeacherId())),
                Map.of(saved.getId(), visibleStudentIds),
                true
        );
    }

    @Transactional
    public CourseResponse updateCourse(Long id, CourseUpsertRequest request, UserContext userContext) {
        CourseEntity course = requireManageableCourse(id, userContext);
        CourseVisibilityType visibilityType = parseVisibilityType(request.getVisibilityType());
        List<Long> visibleStudentIds = normalizeVisibleStudentIds(request.getVisibleStudentIds());
        validateVisibleStudents(visibilityType, visibleStudentIds);

        course.setTitle(request.getTitle().trim());
        course.setDescription(request.getDescription().trim());
        course.setVisibilityType(visibilityType);

        String coverImage = normalizeBlank(request.getCoverImage());
        if (!Objects.equals(course.getCoverKey(), coverImage)) {
            courseCoverStorageService.deleteIfManaged(course.getCoverKey());
            course.setCoverKey(coverImage);
        }

        CourseEntity saved = courseRepository.save(course);
        syncVisibleStudents(saved.getId(), visibilityType, visibleStudentIds);
        publishCourseUpdatedEvent(saved, "UPDATED");
        recordCourseAudit(userContext, saved, "COURSE_UPDATED", "Updated course");

        return toCourseResponse(
                saved,
                Map.of(saved.getTeacherId(), resolveTeacherName(saved.getTeacherId())),
                Map.of(saved.getId(), visibleStudentIds),
                true
        );
    }

    @Transactional
    public void deleteCourse(Long id, UserContext userContext) {
        CourseEntity course = requireManageableCourse(id, userContext);
        String title = course.getTitle();
        deleteManagedResourcesForCourse(id);
        courseVisibleStudentRepository.deleteByCourseId(id);
        courseRepository.delete(course);
        courseCoverStorageService.deleteIfManaged(course.getCoverKey());
        recordCourseAudit(userContext, id, title, "COURSE_DELETED", "Deleted course");
    }

    @Transactional
    public CourseResponse publishCourse(Long id, UserContext userContext) {
        CourseEntity course = requireManageableCourse(id, userContext);
        ensurePublishable(course);
        course.setStatus(CourseStatus.PUBLISHED);
        CourseEntity saved = courseRepository.save(course);
        publishCourseUpdatedEvent(saved, "PUBLISHED");
        publishCoursePublishedNotifications(saved);
        recordCourseAudit(userContext, saved, "COURSE_PUBLISHED", "Published course");
        return toCourseResponseAfterMutation(saved);
    }

    @Transactional
    public CourseResponse unpublishCourse(Long id, UserContext userContext) {
        CourseEntity course = requireManageableCourse(id, userContext);
        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw BusinessException.badRequest("Only published courses can be unpublished");
        }
        course.setStatus(CourseStatus.DRAFT);
        CourseEntity saved = courseRepository.save(course);
        publishCourseUpdatedEvent(saved, "UNPUBLISHED");
        recordCourseAudit(userContext, saved, "COURSE_UNPUBLISHED", "Unpublished course");
        return toCourseResponseAfterMutation(saved);
    }

    @Transactional
    public CourseResponse archiveCourse(Long id, UserContext userContext) {
        CourseEntity course = requireManageableCourse(id, userContext);
        if (course.getStatus() == CourseStatus.ARCHIVED) {
            throw BusinessException.badRequest("Course is already archived");
        }
        course.setStatus(CourseStatus.ARCHIVED);
        CourseEntity saved = courseRepository.save(course);
        publishCourseUpdatedEvent(saved, "ARCHIVED");
        recordCourseAudit(userContext, saved, "COURSE_ARCHIVED", "Archived course");
        return toCourseResponseAfterMutation(saved);
    }

    @Transactional
    public CourseResponse restoreCourse(Long id, UserContext userContext) {
        CourseEntity course = requireManageableCourse(id, userContext);
        if (course.getStatus() != CourseStatus.ARCHIVED) {
            throw BusinessException.badRequest("Only archived courses can be restored");
        }
        course.setStatus(CourseStatus.DRAFT);
        CourseEntity saved = courseRepository.save(course);
        publishCourseUpdatedEvent(saved, "RESTORED");
        recordCourseAudit(userContext, saved, "COURSE_RESTORED", "Restored course");
        return toCourseResponseAfterMutation(saved);
    }

    @Transactional
    public void enrollCourse(Long courseId, UserContext userContext) {
        assertRole(userContext, "STUDENT");

        CourseEntity course = requireCourse(courseId);
        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw BusinessException.badRequest("Course is not published yet");
        }
        if (!canStudentViewCourseSummary(course, userContext.userId())) {
            throw BusinessException.forbidden("No access to enroll in this course");
        }
        if (enrollmentRepository.existsByStudentIdAndCourseId(userContext.userId(), courseId)) {
            throw BusinessException.conflict("Course is already enrolled");
        }

        enrollmentRepository.save(EnrollmentEntity.builder()
                .studentId(userContext.userId())
                .courseId(courseId)
                .build());
    }

    @Transactional
    public void unenrollCourse(Long courseId, UserContext userContext) {
        assertRole(userContext, "STUDENT");
        EnrollmentEntity enrollment = enrollmentRepository.findByStudentIdAndCourseId(userContext.userId(), courseId)
                .orElseThrow(() -> BusinessException.notFound("Enrollment record not found"));
        enrollmentRepository.delete(enrollment);
    }

    public List<ChapterResponse> listChapters(Long courseId, UserContext userContext) {
        CourseEntity course = requireContentAccessibleCourse(courseId, userContext);
        return chapterRepository.findByCourseIdOrderByOrderIndexAscIdAsc(course.getId()).stream()
                .map(this::toChapterResponse)
                .toList();
    }

    @Transactional
    public ChapterResponse createChapter(Long courseId, ChapterUpsertRequest request, UserContext userContext) {
        CourseEntity course = requireManageableCourse(courseId, userContext);
        ChapterEntity chapter = ChapterEntity.builder()
                .courseId(course.getId())
                .title(request.getTitle().trim())
                .description(normalizeBlank(request.getDescription()))
                .orderIndex(resolveChapterOrderIndex(courseId, request.getOrderIndex()))
                .build();
        return toChapterResponse(chapterRepository.save(chapter));
    }

    @Transactional
    public ChapterResponse updateChapter(Long courseId, Long chapterId, ChapterUpsertRequest request, UserContext userContext) {
        requireManageableCourse(courseId, userContext);
        ChapterEntity chapter = requireChapter(chapterId);
        if (!chapter.getCourseId().equals(courseId)) {
            throw BusinessException.badRequest("Chapter does not belong to the current course");
        }
        chapter.setTitle(request.getTitle().trim());
        chapter.setDescription(normalizeBlank(request.getDescription()));
        chapter.setOrderIndex(resolveOrderIndex(request.getOrderIndex(), chapter.getOrderIndex()));
        return toChapterResponse(chapterRepository.save(chapter));
    }

    @Transactional
    public void deleteChapter(Long courseId, Long chapterId, UserContext userContext) {
        requireManageableCourse(courseId, userContext);
        ChapterEntity chapter = requireChapter(chapterId);
        if (!chapter.getCourseId().equals(courseId)) {
            throw BusinessException.badRequest("Chapter does not belong to the current course");
        }
        deleteManagedResourcesByChapterIds(List.of(chapterId));
        chapterRepository.delete(chapter);
    }

    public List<ResourceResponse> listResources(Long chapterId, UserContext userContext) {
        ChapterEntity chapter = requireChapter(chapterId);
        requireContentAccessibleCourse(chapter.getCourseId(), userContext);
        List<ResourceEntity> resources = resourceRepository.findByChapterIdOrderByOrderIndexAscIdAsc(chapterId);
        Map<Long, List<ResourceKnowledgePointResponse>> resourceTags = loadResourceKnowledgePointMap(resources.stream()
                .map(ResourceEntity::getId)
                .toList());
        Map<Long, List<ResourceTagResponse>> resourceLabelMap = loadResourceTagMap(resources.stream()
                .map(ResourceEntity::getId)
                .toList());
        return resources.stream()
                .map(resource -> toResourceResponse(
                        resource,
                        resourceTags.getOrDefault(resource.getId(), List.of()),
                        resourceLabelMap.getOrDefault(resource.getId(), List.of())
                ))
                .toList();
    }

    public ResourceResponse getResource(Long resourceId, UserContext userContext) {
        ResourceEntity resource = requireResource(resourceId);
        ChapterEntity chapter = requireChapter(resource.getChapterId());
        requireContentAccessibleCourse(chapter.getCourseId(), userContext);
        Map<Long, List<ResourceKnowledgePointResponse>> resourceTags = loadResourceKnowledgePointMap(List.of(resourceId));
        Map<Long, List<ResourceTagResponse>> resourceLabelMap = loadResourceTagMap(List.of(resourceId));
        return toResourceResponse(
                resource,
                resourceTags.getOrDefault(resourceId, List.of()),
                resourceLabelMap.getOrDefault(resourceId, List.of())
        );
    }

    public InternalResourceTaggingContextResponse getResourceTaggingContext(Long resourceId) {
        ResourceEntity resource = requireResource(resourceId);
        ChapterEntity chapter = requireChapter(resource.getChapterId());
        CourseEntity course = requireCourse(chapter.getCourseId());
        return InternalResourceTaggingContextResponse.builder()
                .resourceId(resource.getId())
                .chapterId(chapter.getId())
                .courseId(course.getId())
                .teacherId(course.getTeacherId())
                .title(resource.getTitle())
                .description(resource.getDescription())
                .type(resource.getType().name())
                .storageKey(resource.getStorageKey())
                .build();
    }

    public List<InternalKnowledgePointResponse> listLeafKnowledgePointsForTagging() {
        Map<Long, KnowledgePointEntity> knowledgePointMap = loadKnowledgePointMap();
        return knowledgePointRepository.findByActiveTrueOrderByOrderIndexAscIdAsc().stream()
                .filter(this::isAttachableKnowledgePoint)
                .map(item -> InternalKnowledgePointResponse.builder()
                        .id(item.getId())
                        .parentId(item.getParentId())
                        .name(item.getName())
                        .description(item.getDescription())
                        .keywords(item.getKeywords())
                        .nodeType(item.getNodeType().name())
                        .path(buildKnowledgePointPath(item.getId(), knowledgePointMap))
                        .build())
                .toList();
    }

    public List<Long> listCourseStudentIds(Long courseId) {
        requireCourse(courseId);
        return enrollmentRepository.findByCourseId(courseId).stream()
                .map(EnrollmentEntity::getStudentId)
                .distinct()
                .toList();
    }

    @Transactional
    public ResourceResponse createResource(Long chapterId, ResourceUpsertRequest request, UserContext userContext) {
        ChapterEntity chapter = requireChapter(chapterId);
        CourseEntity course = requireManageableCourse(chapter.getCourseId(), userContext);

        ResourceType resourceType = parseResourceType(request.getType());
        String exerciseContent = null;
        String storageKey = normalizeBlank(request.getUrl());
        if (resourceType == ResourceType.EXERCISE) {
            exerciseContent = serializeExerciseQuestions(request.getExerciseQuestions());
            storageKey = EXERCISE_STORAGE_KEY;
        } else if (!StringUtils.hasText(storageKey)) {
            throw BusinessException.badRequest("Please upload a resource file or provide an external URL");
        }

        List<Long> manualKnowledgePointIds = normalizeKnowledgePointIds(request.getKnowledgePointIds());
        List<String> manualTagLabels = normalizeTagLabels(request.getTagLabels());
        ResourceStatus initialStatus = resourceType == ResourceType.EXERCISE || (!manualKnowledgePointIds.isEmpty() || !manualTagLabels.isEmpty())
                ? ResourceStatus.PUBLISHED
                : ResourceStatus.PROCESSING;

        ResourceEntity resource = ResourceEntity.builder()
                .chapterId(chapterId)
                .title(request.getTitle().trim())
                .type(resourceType)
                .storageKey(storageKey)
                .fileSize(request.getSize())
                .durationSeconds(request.getDuration())
                .description(normalizeBlank(request.getDescription()))
                .exerciseContent(exerciseContent)
                .orderIndex(resolveResourceOrderIndex(chapterId, request.getOrderIndex()))
                .status(initialStatus)
                .build();

        ResourceEntity saved = resourceRepository.save(resource);
        replaceManualResourceTags(saved, manualKnowledgePointIds, manualTagLabels);
        if (resourceType != ResourceType.EXERCISE) {
            publishResourceUploadedEventIfNeeded(saved, chapter, course, manualKnowledgePointIds, manualTagLabels);
        }
        Map<Long, List<ResourceKnowledgePointResponse>> resourceTags = loadResourceKnowledgePointMap(List.of(saved.getId()));
        Map<Long, List<ResourceTagResponse>> resourceLabelMap = loadResourceTagMap(List.of(saved.getId()));
        return toResourceResponse(
                saved,
                resourceTags.getOrDefault(saved.getId(), List.of()),
                resourceLabelMap.getOrDefault(saved.getId(), List.of())
        );
    }

    @Transactional
    public ResourceResponse updateResource(Long resourceId, ResourceUpsertRequest request, UserContext userContext) {
        ResourceEntity resource = requireResource(resourceId);
        ChapterEntity chapter = requireChapter(resource.getChapterId());
        CourseEntity course = requireManageableCourse(chapter.getCourseId(), userContext);

        String previousStorageKey = resource.getStorageKey();
        ResourceType resourceType = parseResourceType(request.getType());
        String exerciseContent = resourceType == ResourceType.EXERCISE
                ? serializeExerciseQuestions(request.getExerciseQuestions())
                : null;
        String updatedStorageKey = resourceType == ResourceType.EXERCISE
                ? EXERCISE_STORAGE_KEY
                : (StringUtils.hasText(request.getUrl()) ? request.getUrl().trim() : previousStorageKey);
        List<Long> manualKnowledgePointIds = normalizeKnowledgePointIds(request.getKnowledgePointIds());
        List<String> manualTagLabels = normalizeTagLabels(request.getTagLabels());

        resource.setTitle(request.getTitle().trim());
        resource.setType(resourceType);
        resource.setStorageKey(updatedStorageKey);
        resource.setFileSize(resourceType == ResourceType.EXERCISE ? null : request.getSize());
        resource.setDurationSeconds(resourceType == ResourceType.EXERCISE ? null : request.getDuration());
        resource.setDescription(normalizeBlank(request.getDescription()));
        resource.setExerciseContent(exerciseContent);
        resource.setOrderIndex(resolveOrderIndex(request.getOrderIndex(), resource.getOrderIndex()));
        resource.setStatus(resourceType == ResourceType.EXERCISE || (!manualKnowledgePointIds.isEmpty() || !manualTagLabels.isEmpty())
                ? ResourceStatus.PUBLISHED
                : ResourceStatus.PROCESSING);
        ResourceEntity saved = resourceRepository.save(resource);
        replaceManualResourceTags(saved, manualKnowledgePointIds, manualTagLabels);
        if (resourceType != ResourceType.EXERCISE) {
            publishResourceUploadedEventIfNeeded(saved, chapter, course, manualKnowledgePointIds, manualTagLabels);
        }
        if (!Objects.equals(previousStorageKey, updatedStorageKey)) {
            resourceStorageService.deleteIfManaged(previousStorageKey);
        }
        Map<Long, List<ResourceKnowledgePointResponse>> resourceTags = loadResourceKnowledgePointMap(List.of(saved.getId()));
        Map<Long, List<ResourceTagResponse>> resourceLabelMap = loadResourceTagMap(List.of(saved.getId()));
        return toResourceResponse(
                saved,
                resourceTags.getOrDefault(saved.getId(), List.of()),
                resourceLabelMap.getOrDefault(saved.getId(), List.of())
        );
    }

    @Transactional
    public void deleteResource(Long resourceId, UserContext userContext) {
        ResourceEntity resource = requireResource(resourceId);
        ChapterEntity chapter = requireChapter(resource.getChapterId());
        requireManageableCourse(chapter.getCourseId(), userContext);
        resourceRepository.delete(resource);
        resourceStorageService.deleteIfManaged(resource.getStorageKey());
    }

    public void assertCanManageResource(Long resourceId, UserContext userContext) {
        ResourceEntity resource = requireResource(resourceId);
        ChapterEntity chapter = requireChapter(resource.getChapterId());
        requireManageableCourse(chapter.getCourseId(), userContext);
    }

    public List<KnowledgePointNodeResponse> listKnowledgePointTree(boolean activeOnly, UserContext userContext) {
        assertRole(userContext, "STUDENT", "TEACHER", "ADMIN");
        List<KnowledgePointEntity> knowledgePoints = activeOnly
                ? knowledgePointRepository.findByActiveTrueOrderByOrderIndexAscIdAsc()
                : knowledgePointRepository.findAllByOrderByOrderIndexAscIdAsc();
        return buildKnowledgePointTree(knowledgePoints, new HashMap<>());
    }

    public KnowledgeGraphResponse getKnowledgeGraph(Long rootId, boolean activeOnly, UserContext userContext) {
        assertRole(userContext, "STUDENT", "TEACHER", "ADMIN");
        List<KnowledgePointEntity> sourceKnowledgePoints = activeOnly
                ? knowledgePointRepository.findByActiveTrueOrderByOrderIndexAscIdAsc()
                : knowledgePointRepository.findAllByOrderByOrderIndexAscIdAsc();
        Map<Long, KnowledgePointEntity> sourceMap = sourceKnowledgePoints.stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getId(), item), Map::putAll);
        if (sourceMap.isEmpty()) {
            return KnowledgeGraphResponse.builder()
                    .rootId(rootId)
                    .totalKnowledgePoints(0)
                    .totalResourceRelations(0)
                    .coveredKnowledgePoints(0)
                    .nodes(List.of())
                    .edges(List.of())
                    .build();
        }

        KnowledgePointEntity root = null;
        List<KnowledgePointEntity> scopedKnowledgePoints;
        if (rootId == null) {
            scopedKnowledgePoints = sourceKnowledgePoints;
        } else {
            root = sourceMap.get(rootId);
            if (root == null) {
                throw BusinessException.notFound("Knowledge graph root not found");
            }
            scopedKnowledgePoints = collectKnowledgePointSubtree(rootId, sourceKnowledgePoints);
        }

        Map<Long, KnowledgePointEntity> scopedMap = scopedKnowledgePoints.stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getId(), item), Map::putAll);
        Map<Long, List<KnowledgePointEntity>> childrenByParent = new LinkedHashMap<>();
        for (KnowledgePointEntity knowledgePoint : scopedKnowledgePoints) {
            if (knowledgePoint.getParentId() != null && scopedMap.containsKey(knowledgePoint.getParentId())) {
                childrenByParent.computeIfAbsent(knowledgePoint.getParentId(), ignored -> new ArrayList<>()).add(knowledgePoint);
            }
        }
        childrenByParent.values().forEach(children -> children.sort(
                Comparator.comparing(KnowledgePointEntity::getOrderIndex).thenComparing(KnowledgePointEntity::getId)));

        List<ResourceTagRepository.ResourceTagKnowledgeLinkProjection> tagLinks = resourceTagRepository.findResourceTagKnowledgeLinks();
        Map<Long, Set<Long>> directResourceIds = loadKnowledgePointDirectResourceIds(sourceMap, tagLinks);
        Map<Long, Integer> directCounts = toResourceCountMap(directResourceIds);
        Map<Long, Integer> subtreeCounts = new LinkedHashMap<>();
        Set<Long> visitedForCount = new LinkedHashSet<>();
        for (KnowledgePointEntity knowledgePoint : scopedKnowledgePoints) {
            calculateSubtreeResourceCount(knowledgePoint.getId(), childrenByParent, directCounts, subtreeCounts, visitedForCount);
        }

        List<KnowledgeGraphNodeResponse> nodes = new ArrayList<>();
        List<KnowledgeGraphEdgeResponse> edges = new ArrayList<>();
        Map<Long, Integer> depthById = calculateKnowledgeGraphDepths(scopedKnowledgePoints, scopedMap);
        for (KnowledgePointEntity knowledgePoint : scopedKnowledgePoints) {
            int directResourceCount = directCounts.getOrDefault(knowledgePoint.getId(), 0);
            int resourceCount = subtreeCounts.getOrDefault(knowledgePoint.getId(), directResourceCount);
            nodes.add(KnowledgeGraphNodeResponse.builder()
                    .id(knowledgePoint.getId())
                    .parentId(knowledgePoint.getParentId())
                    .name(knowledgePoint.getName())
                    .path(buildKnowledgePointPath(knowledgePoint.getId(), sourceMap))
                    .nodeType(knowledgePoint.getNodeType().name())
                    .active(knowledgePoint.getActive())
                    .depth(depthById.getOrDefault(knowledgePoint.getId(), 0))
                    .directResourceCount(directResourceCount)
                    .resourceCount(resourceCount)
                    .coverageLevel(resolveKnowledgeGraphCoverageLevel(resourceCount))
                    .color(resolveKnowledgeGraphColor(resourceCount))
                    .build());
            if (knowledgePoint.getParentId() != null && scopedMap.containsKey(knowledgePoint.getParentId())) {
                edges.add(KnowledgeGraphEdgeResponse.builder()
                        .source(knowledgePoint.getParentId())
                        .target(knowledgePoint.getId())
                        .relation("PARENT_CHILD")
                        .build());
            }
        }

        if (root != null) {
            appendTagDerivedKnowledgePointNodes(root, sourceMap, scopedMap, directResourceIds, tagLinks, nodes, edges);
        }

        int totalResourceRelations = nodes.stream()
                .filter(node -> node.getParentId() == null || rootId != null && Objects.equals(node.getId(), rootId))
                .mapToInt(KnowledgeGraphNodeResponse::getResourceCount)
                .sum();
        if (rootId == null) {
            totalResourceRelations = directCounts.entrySet().stream()
                    .filter(entry -> scopedMap.containsKey(entry.getKey()))
                    .mapToInt(Map.Entry::getValue)
                    .sum();
        }

        return KnowledgeGraphResponse.builder()
                .rootId(root == null ? null : root.getId())
                .rootName(root == null ? null : root.getName())
                .rootPath(root == null ? null : buildKnowledgePointPath(root.getId(), sourceMap))
                .totalKnowledgePoints(nodes.size())
                .totalResourceRelations(totalResourceRelations)
                .coveredKnowledgePoints((int) nodes.stream().filter(node -> node.getResourceCount() > 0).count())
                .nodes(nodes)
                .edges(edges)
                .build();
    }

    @Transactional
    public KnowledgePointNodeResponse createKnowledgePoint(KnowledgePointUpsertRequest request, UserContext userContext) {
        assertRole(userContext, "ADMIN", "TEACHER");
        KnowledgePointType nodeType = parseKnowledgePointType(request.getNodeType());
        KnowledgePointEntity parent = requireValidKnowledgePointParent(request.getParentId(), nodeType);
        Long parentId = parent == null ? null : parent.getId();
        String normalizedName = normalizeSuggestionText(request.getName());

        KnowledgePointEntity existing = knowledgePointRepository.findAllByOrderByOrderIndexAscIdAsc().stream()
                .filter(item -> Objects.equals(item.getParentId(), parentId))
                .filter(item -> item.getNodeType() == nodeType)
                .filter(item -> Objects.equals(normalizeSuggestionText(item.getName()), normalizedName))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return toKnowledgePointNodeResponse(existing, loadKnowledgePointMap(), List.of());
        }

        KnowledgePointEntity entity = KnowledgePointEntity.builder()
                .parentId(parentId)
                .name(request.getName().trim())
                .description(normalizeBlank(request.getDescription()))
                .keywords(normalizeKeywords(request.getKeywords()))
                .nodeType(nodeType)
                .active(request.getActive() == null ? Boolean.TRUE : request.getActive())
                .orderIndex(resolveKnowledgePointOrderIndex(parentId, request.getOrderIndex()))
                .build();

        KnowledgePointEntity saved = knowledgePointRepository.save(entity);
        publishKnowledgePointUpdatedEvent(saved);
        return toKnowledgePointNodeResponse(saved, loadKnowledgePointMap(), List.of());
    }

    @Transactional
    public KnowledgePointNodeResponse updateKnowledgePoint(Long knowledgePointId, KnowledgePointUpsertRequest request, UserContext userContext) {
        assertRole(userContext, "ADMIN");
        KnowledgePointEntity entity = requireKnowledgePoint(knowledgePointId);
        KnowledgePointType requestedType = parseKnowledgePointType(request.getNodeType());
        if (requestedType != entity.getNodeType()) {
            throw BusinessException.badRequest("Changing knowledge point type is not supported");
        }

        KnowledgePointEntity parent = requireValidKnowledgePointParent(request.getParentId(), requestedType);
        Long parentId = parent == null ? null : parent.getId();
        if (!Objects.equals(entity.getParentId(), parentId)) {
            throw BusinessException.badRequest("Changing the parent node is not supported");
        }

        entity.setName(request.getName().trim());
        entity.setDescription(normalizeBlank(request.getDescription()));
        entity.setKeywords(normalizeKeywords(request.getKeywords()));
        entity.setActive(request.getActive() == null ? entity.getActive() : request.getActive());
        entity.setOrderIndex(resolveKnowledgePointOrderIndex(entity.getParentId(), request.getOrderIndex()));

        KnowledgePointEntity saved = knowledgePointRepository.save(entity);
        publishKnowledgePointUpdatedEvent(saved);
        return toKnowledgePointNodeResponse(saved, loadKnowledgePointMap(), List.of());
    }

    public List<ResourceTagSuggestionResponse> previewResourceTagSuggestions(
            ResourceTagPreviewRequest request,
            MultipartFile file,
            UserContext userContext
    ) {
        assertRole(userContext, "TEACHER", "ADMIN");
        if ((file == null || file.isEmpty())
                && !StringUtils.hasText(request.getTitle())
                && !StringUtils.hasText(request.getDescription())
                && !StringUtils.hasText(request.getSourceUrl())
                && !StringUtils.hasText(request.getFileName())) {
            throw BusinessException.badRequest("Please upload a file or provide resource metadata before generating AI tag suggestions");
        }
        return resourceTagSuggestionService.suggestForPreview(request, file);
    }

    public List<ResourceTagSuggestionResponse> getResourceTagSuggestions(Long resourceId, UserContext userContext) {
        ResourceEntity resource = requireResource(resourceId);
        ChapterEntity chapter = requireChapter(resource.getChapterId());
        requireManageableCourse(chapter.getCourseId(), userContext);
        return resourceTagSuggestionService.suggestForResource(resource);
    }

    @Transactional
    public ResourceResponse confirmResourceTags(Long resourceId, ResourceTagConfirmRequest request, UserContext userContext) {
        ResourceEntity resource = requireResource(resourceId);
        ChapterEntity chapter = requireChapter(resource.getChapterId());
        requireManageableCourse(chapter.getCourseId(), userContext);
        replaceManualResourceTags(resource, request.getKnowledgePointIds(), request.getTagLabels());
        resource.setStatus(ResourceStatus.PUBLISHED);
        resourceRepository.save(resource);
        Map<Long, List<ResourceKnowledgePointResponse>> resourceTags = loadResourceKnowledgePointMap(List.of(resource.getId()));
        Map<Long, List<ResourceTagResponse>> resourceLabelMap = loadResourceTagMap(List.of(resource.getId()));
        return toResourceResponse(
                resource,
                resourceTags.getOrDefault(resource.getId(), List.of()),
                resourceLabelMap.getOrDefault(resource.getId(), List.of())
        );
    }

    @Transactional
    public ResourceResponse retryResourceTagging(Long resourceId, UserContext userContext) {
        ResourceEntity resource = requireResource(resourceId);
        ChapterEntity chapter = requireChapter(resource.getChapterId());
        CourseEntity course = requireManageableCourse(chapter.getCourseId(), userContext);
        resourceKnowledgePointRepository.deleteByResourceId(resource.getId());
        resourceTagRepository.deleteByResourceId(resource.getId());
        resource.setTaggingStatus(ResourceTaggingStatus.PROCESSING);
        resource.setTaggingUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        ResourceEntity saved = resourceRepository.save(resource);

        try {
            if (applyAiSuggestionResponses(saved, resourceTagSuggestionService.suggestForResource(saved))) {
                Map<Long, List<ResourceKnowledgePointResponse>> resourceTags = loadResourceKnowledgePointMap(List.of(saved.getId()));
                Map<Long, List<ResourceTagResponse>> resourceLabelMap = loadResourceTagMap(List.of(saved.getId()));
                return toResourceResponse(
                        saved,
                        resourceTags.getOrDefault(saved.getId(), List.of()),
                        resourceLabelMap.getOrDefault(saved.getId(), List.of())
                );
            }
        } catch (Exception ex) {
            log.warn("Immediate resource tag regeneration failed, fallback to async tagging: resourceId={}", saved.getId(), ex);
        }

        ResourceUploadedEvent event = ResourceUploadedEvent.builder()
                .resourceId(saved.getId())
                .chapterId(saved.getChapterId())
                .courseId(course.getId())
                .teacherId(course.getTeacherId())
                .title(saved.getTitle())
                .description(saved.getDescription())
                .type(saved.getType().name())
                .storageKey(saved.getStorageKey())
                .build();
        runAfterCurrentCommit(() -> requestAiTagging(event));

        return toResourceResponse(saved, List.of(), List.of());
    }

    public List<ResourceResponse.ExerciseQuestionResponse> generateExerciseQuestions(
            ExerciseGenerateRequest request,
            UserContext userContext
    ) {
        assertRole(userContext, "TEACHER", "ADMIN");
        int count = Math.max(1, Math.min(MAX_EXERCISE_QUESTIONS, request.getQuestionCount() == null ? 5 : request.getQuestionCount()));
        List<String> topics = normalizeTagLabels(request.getTagLabels());
        if (topics.isEmpty() && StringUtils.hasText(request.getTitle())) {
            topics = List.of(request.getTitle().trim());
        }
        if (topics.isEmpty()) {
            topics = List.of("本课程核心知识点");
        }

        List<ResourceResponse.ExerciseQuestionResponse> questions = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String topic = topics.get(index % topics.size());
            String distractorA = index + 1 < topics.size() ? topics.get((index + 1) % topics.size()) : "只需要记忆概念名称";
            String distractorB = index + 2 < topics.size() ? topics.get((index + 2) % topics.size()) : "与课程目标无关的内容";
            String distractorC = StringUtils.hasText(request.getDescription()) ? "忽略题干中的学习场景" : "跳过资源学习直接完成";
            questions.add(ResourceResponse.ExerciseQuestionResponse.builder()
                    .id(UUID.randomUUID().toString())
                    .stem("关于“" + topic + "”，下列哪一项最符合本资源的学习目标？")
                    .options(List.of(
                            ResourceResponse.ExerciseOptionResponse.builder().id("A").text("理解“" + topic + "”的关键概念，并能结合资源内容进行判断").build(),
                            ResourceResponse.ExerciseOptionResponse.builder().id("B").text(distractorA).build(),
                            ResourceResponse.ExerciseOptionResponse.builder().id("C").text(distractorB).build(),
                            ResourceResponse.ExerciseOptionResponse.builder().id("D").text(distractorC).build()
                    ))
                    .answer("A")
                    .explanation("该题用于检查学生是否掌握“" + topic + "”对应的核心理解点。")
                    .build());
        }
        return questions;
    }

    @Transactional
    public void applyAiTaggedResource(ResourceTaggedEvent event) {
        ResourceEntity resource = requireResource(event.resourceId());
        ChapterEntity chapter = requireChapter(resource.getChapterId());
        CourseEntity course = requireCourse(chapter.getCourseId());

        if (resource.getTaggingStatus() == ResourceTaggingStatus.CONFIRMED) {
            return;
        }
        if (!Objects.equals(event.title(), resource.getTitle())
                || !Objects.equals(event.storageKey(), resource.getStorageKey())) {
            return;
        }

        boolean shouldNotifyTeacher = resource.getTaggingStatus() != ResourceTaggingStatus.SUGGESTED;
        resourceKnowledgePointRepository.deleteByResourceId(resource.getId());
        resourceTagRepository.deleteByResourceId(resource.getId());

        List<ResourceTaggedKnowledgePointEvent> knowledgePointEvents = event.knowledgePoints() == null
                ? List.of()
                : event.knowledgePoints().stream()
                .filter(Objects::nonNull)
                .filter(item -> item.knowledgePointId() != null)
                .toList();
        List<Long> knowledgePointIds = knowledgePointEvents.stream()
                .map(ResourceTaggedKnowledgePointEvent::knowledgePointId)
                .distinct()
                .toList();

        if (!knowledgePointIds.isEmpty()) {
            Map<Long, KnowledgePointEntity> knowledgePointMap = knowledgePointRepository.findByIdIn(knowledgePointIds).stream()
                    .filter(item -> Boolean.TRUE.equals(item.getActive()))
                    .filter(this::isAttachableKnowledgePoint)
                    .collect(LinkedHashMap::new, (map, item) -> map.put(item.getId(), item), Map::putAll);

            List<ResourceKnowledgePointEntity> relations = new ArrayList<>();
            List<ResourceTagEntity> tagEntities = new ArrayList<>();
            for (ResourceTaggedKnowledgePointEvent knowledgePointEvent : knowledgePointEvents) {
                KnowledgePointEntity knowledgePoint = knowledgePointMap.get(knowledgePointEvent.knowledgePointId());
                if (knowledgePoint == null) {
                    continue;
                }
                double confidence = knowledgePointEvent.confidence() == null ? 0.5D : knowledgePointEvent.confidence();
                relations.add(ResourceKnowledgePointEntity.builder()
                        .resourceId(resource.getId())
                        .knowledgePointId(knowledgePoint.getId())
                        .confidence(confidence)
                        .source(ResourceTagSource.AI)
                        .build());
                tagEntities.add(ResourceTagEntity.builder()
                        .resourceId(resource.getId())
                        .label(knowledgePoint.getName())
                        .normalizedLabel(normalizeSuggestionText(knowledgePoint.getName()))
                        .knowledgePointId(knowledgePoint.getId())
                        .confidence(confidence)
                        .source(ResourceTagSource.AI)
                        .build());
            }
            if (!relations.isEmpty()) {
                resourceKnowledgePointRepository.saveAll(relations);
            }
            if (!tagEntities.isEmpty()) {
                resourceTagRepository.saveAll(tagEntities);
            }
        }

        boolean hasSuggestions = !resourceTagRepository.findByResourceIdIn(List.of(resource.getId())).isEmpty()
                || resourceKnowledgePointRepository.findByResourceIdIn(List.of(resource.getId())).stream().findAny().isPresent();
        resource.setTaggingStatus(hasSuggestions ? ResourceTaggingStatus.SUGGESTED : ResourceTaggingStatus.UNTAGGED);
        resource.setTaggingUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        resource.setStatus(ResourceStatus.PUBLISHED);
        resourceRepository.save(resource);

        if (shouldNotifyTeacher && hasSuggestions) {
            outboxService.enqueue(EventTopics.NOTIFICATION_SEND, NotificationSendEvent.builder()
                    .userId(course.getTeacherId())
                    .type("COURSE")
                    .title("资源 AI 标注已完成")
                    .content("资源《" + resource.getTitle() + "》已生成 AI 标签建议，请进入课程内容管理页确认。")
                    .build());
        }
    }

    private boolean applyAiSuggestionResponses(ResourceEntity resource, List<ResourceTagSuggestionResponse> suggestions) {
        List<Long> knowledgePointIds = suggestions == null
                ? List.of()
                : suggestions.stream()
                .filter(Objects::nonNull)
                .map(ResourceTagSuggestionResponse::getKnowledgePointId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (knowledgePointIds.isEmpty()) {
            return false;
        }

        Map<Long, KnowledgePointEntity> knowledgePointMap = knowledgePointRepository.findByIdIn(knowledgePointIds).stream()
                .filter(item -> Boolean.TRUE.equals(item.getActive()))
                .filter(this::isAttachableKnowledgePoint)
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getId(), item), Map::putAll);
        if (knowledgePointMap.isEmpty()) {
            return false;
        }

        LinkedHashMap<Long, ResourceTagSuggestionResponse> suggestionMap = new LinkedHashMap<>();
        for (ResourceTagSuggestionResponse suggestion : suggestions) {
            if (suggestion == null || suggestion.getKnowledgePointId() == null) {
                continue;
            }
            if (knowledgePointMap.containsKey(suggestion.getKnowledgePointId())) {
                suggestionMap.putIfAbsent(suggestion.getKnowledgePointId(), suggestion);
            }
        }

        List<ResourceKnowledgePointEntity> relations = new ArrayList<>();
        List<ResourceTagEntity> tagEntities = new ArrayList<>();
        for (Map.Entry<Long, ResourceTagSuggestionResponse> entry : suggestionMap.entrySet()) {
            KnowledgePointEntity knowledgePoint = knowledgePointMap.get(entry.getKey());
            ResourceTagSuggestionResponse suggestion = entry.getValue();
            double confidence = suggestion.getConfidence() == null ? 0.5D : Math.max(0D, Math.min(1D, suggestion.getConfidence()));
            relations.add(ResourceKnowledgePointEntity.builder()
                    .resourceId(resource.getId())
                    .knowledgePointId(knowledgePoint.getId())
                    .confidence(confidence)
                    .source(ResourceTagSource.AI)
                    .build());
            tagEntities.add(ResourceTagEntity.builder()
                    .resourceId(resource.getId())
                    .label(knowledgePoint.getName())
                    .normalizedLabel(normalizeSuggestionText(knowledgePoint.getName()))
                    .knowledgePointId(knowledgePoint.getId())
                    .confidence(confidence)
                    .source(ResourceTagSource.AI)
                    .build());
        }

        if (relations.isEmpty()) {
            return false;
        }
        resourceKnowledgePointRepository.saveAll(relations);
        resourceTagRepository.saveAll(tagEntities);
        resource.setTaggingStatus(ResourceTaggingStatus.SUGGESTED);
        resource.setTaggingUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        resource.setStatus(ResourceStatus.PUBLISHED);
        resourceRepository.save(resource);
        return true;
    }

    public ManagedResourceContent loadManagedResourceContent(Long resourceId, UserContext userContext) {
        ResourceEntity resource = requireResource(resourceId);
        ChapterEntity chapter = requireChapter(resource.getChapterId());
        requireContentAccessibleCourse(chapter.getCourseId(), userContext);

        if (!resourceStorageService.isManagedStorageKey(resource.getStorageKey())) {
            throw BusinessException.badRequest("Resource does not use managed file storage");
        }

        return new ManagedResourceContent(
                resource.getTitle(),
                resource.getType(),
                resource.getStorageKey()
        );
    }

    private Specification<CourseEntity> withKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String normalized = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), normalized),
                cb.like(cb.lower(root.get("description")), normalized)
        );
    }

    private Specification<CourseEntity> withStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        CourseStatus courseStatus = parseCourseStatus(status);
        return (root, query, cb) -> cb.equal(root.get("status"), courseStatus);
    }

    private Specification<CourseEntity> withVisibility(UserContext userContext) {
        return switch (userContext.role()) {
            case "ADMIN" -> null;
            case "TEACHER" -> (root, query, cb) -> cb.equal(root.get("teacherId"), userContext.userId());
            case "STUDENT" -> (root, query, cb) -> {
                var targetedVisibility = query.subquery(Long.class);
                var visibleStudentRoot = targetedVisibility.from(CourseVisibleStudentEntity.class);
                targetedVisibility.select(visibleStudentRoot.get("id"))
                        .where(
                                cb.equal(visibleStudentRoot.get("courseId"), root.get("id")),
                                cb.equal(visibleStudentRoot.get("studentId"), userContext.userId())
                        );

                var enrolledVisibility = query.subquery(Long.class);
                var enrollmentRoot = enrolledVisibility.from(EnrollmentEntity.class);
                enrolledVisibility.select(enrollmentRoot.get("id"))
                        .where(
                                cb.equal(enrollmentRoot.get("courseId"), root.get("id")),
                                cb.equal(enrollmentRoot.get("studentId"), userContext.userId())
                        );

                return cb.and(
                        cb.equal(root.get("status"), CourseStatus.PUBLISHED),
                        cb.or(
                                cb.equal(root.get("visibilityType"), CourseVisibilityType.PUBLIC),
                                cb.exists(targetedVisibility),
                                cb.exists(enrolledVisibility)
                        )
                );
            };
            default -> throw BusinessException.forbidden("No access to course data");
        };
    }

    private CourseEntity requireCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> BusinessException.notFound("Course not found"));
    }

    private ChapterEntity requireChapter(Long chapterId) {
        return chapterRepository.findById(chapterId)
                .orElseThrow(() -> BusinessException.notFound("Chapter not found"));
    }

    private ResourceEntity requireResource(Long resourceId) {
        return resourceRepository.findById(resourceId)
                .orElseThrow(() -> BusinessException.notFound("Resource not found"));
    }

    private KnowledgePointEntity requireKnowledgePoint(Long knowledgePointId) {
        return knowledgePointRepository.findById(knowledgePointId)
                .orElseThrow(() -> BusinessException.notFound("Knowledge point not found"));
    }

    private CourseEntity requireManageableCourse(Long courseId, UserContext userContext) {
        CourseEntity course = requireCourse(courseId);
        if ("ADMIN".equals(userContext.role())) {
            return course;
        }
        if (!"TEACHER".equals(userContext.role())) {
            throw BusinessException.forbidden("Current role cannot manage courses");
        }
        if (!course.getTeacherId().equals(userContext.userId())) {
            throw BusinessException.forbidden("No permission to manage this course");
        }
        return course;
    }

    private CourseEntity requireSummaryVisibleCourse(Long courseId, UserContext userContext) {
        CourseEntity course = requireCourse(courseId);
        if ("ADMIN".equals(userContext.role())) {
            return course;
        }
        if ("TEACHER".equals(userContext.role()) && course.getTeacherId().equals(userContext.userId())) {
            return course;
        }
        if ("STUDENT".equals(userContext.role())
                && (canStudentViewCourseSummary(course, userContext.userId())
                || enrollmentRepository.existsByStudentIdAndCourseId(userContext.userId(), courseId))) {
            return course;
        }
        throw BusinessException.forbidden("No permission to view this course");
    }

    private CourseEntity requireContentAccessibleCourse(Long courseId, UserContext userContext) {
        CourseEntity course = requireCourse(courseId);
        if ("ADMIN".equals(userContext.role())) {
            return course;
        }
        if ("TEACHER".equals(userContext.role()) && course.getTeacherId().equals(userContext.userId())) {
            return course;
        }
        if ("STUDENT".equals(userContext.role())
                && canStudentViewCourseSummary(course, userContext.userId())) {
            return course;
        }
        throw BusinessException.forbidden("No permission to access this course content");
    }

    private void assertRole(UserContext userContext, String... roles) {
        for (String role : roles) {
            if (role.equals(userContext.role())) {
                return;
            }
        }
        throw BusinessException.forbidden("Current role is not allowed to perform this action");
    }

    private boolean canManageCourse(CourseEntity course, UserContext userContext) {
        return "ADMIN".equals(userContext.role())
                || ("TEACHER".equals(userContext.role()) && course.getTeacherId().equals(userContext.userId()));
    }

    private int toPageIndex(int page) {
        return Math.max(page, 1) - 1;
    }

    private int toPageSize(int pageSize) {
        return Math.min(Math.max(pageSize, 1), 100);
    }

    private int resolveChapterOrderIndex(Long courseId, Integer requestedOrderIndex) {
        if (requestedOrderIndex != null && requestedOrderIndex > 0) {
            return requestedOrderIndex;
        }
        return chapterRepository.findTopByCourseIdOrderByOrderIndexDescIdDesc(courseId)
                .map(chapter -> chapter.getOrderIndex() + 1)
                .orElse(1);
    }

    private int resolveResourceOrderIndex(Long chapterId, Integer requestedOrderIndex) {
        if (requestedOrderIndex != null && requestedOrderIndex > 0) {
            return requestedOrderIndex;
        }
        return resourceRepository.findTopByChapterIdOrderByOrderIndexDescIdDesc(chapterId)
                .map(resource -> resource.getOrderIndex() + 1)
                .orElse(1);
    }

    private int resolveKnowledgePointOrderIndex(Long parentId, Integer requestedOrderIndex) {
        if (requestedOrderIndex != null && requestedOrderIndex > 0) {
            return requestedOrderIndex;
        }
        return knowledgePointRepository.findAllByOrderByOrderIndexAscIdAsc().stream()
                .filter(item -> Objects.equals(item.getParentId(), parentId))
                .map(KnowledgePointEntity::getOrderIndex)
                .max(Integer::compareTo)
                .map(orderIndex -> orderIndex + 1)
                .orElse(1);
    }

    private int resolveOrderIndex(Integer requestedOrderIndex, Integer currentOrderIndex) {
        return requestedOrderIndex != null && requestedOrderIndex > 0 ? requestedOrderIndex : currentOrderIndex;
    }

    private CourseStatus parseCourseStatus(String status) {
        try {
            return CourseStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("Invalid course status");
        }
    }

    private CourseVisibilityType parseVisibilityType(String visibilityType) {
        if (!StringUtils.hasText(visibilityType)) {
            return CourseVisibilityType.PUBLIC;
        }
        try {
            return CourseVisibilityType.valueOf(visibilityType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("Invalid course visibility type");
        }
    }

    private ResourceType parseResourceType(String type) {
        try {
            return ResourceType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("Invalid resource type");
        }
    }

    private KnowledgePointType parseKnowledgePointType(String type) {
        try {
            return KnowledgePointType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("Invalid knowledge point type");
        }
    }

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeKeywords(String keywords) {
        if (!StringUtils.hasText(keywords)) {
            return null;
        }
        return String.join(",", splitKeywords(keywords).stream().distinct().toList());
    }

    private List<Long> normalizeVisibleStudentIds(List<Long> visibleStudentIds) {
        if (visibleStudentIds == null || visibleStudentIds.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(visibleStudentIds.stream()
                .filter(Objects::nonNull)
                .toList()));
    }

    private List<Long> normalizeKnowledgePointIds(List<Long> knowledgePointIds) {
        if (knowledgePointIds == null || knowledgePointIds.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(knowledgePointIds.stream()
                .filter(Objects::nonNull)
                .toList()));
    }

    private List<String> normalizeTagLabels(List<String> tagLabels) {
        if (tagLabels == null || tagLabels.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<String, String> normalizedToOriginal = new LinkedHashMap<>();
        for (String tagLabel : tagLabels) {
            if (!StringUtils.hasText(tagLabel)) {
                continue;
            }
            String trimmed = tagLabel.trim();
            String normalized = normalizeSuggestionText(trimmed);
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            normalizedToOriginal.putIfAbsent(normalized, trimmed);
        }
        return new ArrayList<>(normalizedToOriginal.values());
    }

    private void validateVisibleStudents(CourseVisibilityType visibilityType, List<Long> visibleStudentIds) {
        if (visibilityType == CourseVisibilityType.SELECTED_STUDENTS && visibleStudentIds.isEmpty()) {
            throw BusinessException.badRequest("Please select at least one student for targeted visibility");
        }

        for (Long studentId : visibleStudentIds) {
            UserServiceResponse response = userServiceClient.getUserById(studentId);
            if (response == null || response.getData() == null) {
                throw BusinessException.badRequest("Visible student does not exist: " + studentId);
            }
            if (!"STUDENT".equalsIgnoreCase(response.getData().getRole())) {
                throw BusinessException.badRequest("Visible targets must all be students");
            }
            if (Boolean.FALSE.equals(response.getData().getIsActive())) {
                throw BusinessException.badRequest("Visible student is inactive: " + studentId);
            }
        }
    }

    private void syncVisibleStudents(Long courseId, CourseVisibilityType visibilityType, List<Long> visibleStudentIds) {
        courseVisibleStudentRepository.deleteByCourseId(courseId);
        if (visibilityType != CourseVisibilityType.SELECTED_STUDENTS) {
            return;
        }
        List<CourseVisibleStudentEntity> entities = visibleStudentIds.stream()
                .map(studentId -> CourseVisibleStudentEntity.builder()
                        .courseId(courseId)
                        .studentId(studentId)
                        .build())
                .toList();
        courseVisibleStudentRepository.saveAll(entities);
    }

    private boolean canStudentViewCourseSummary(CourseEntity course, Long studentId) {
        if (course.getStatus() != CourseStatus.PUBLISHED) {
            return false;
        }
        if (course.getVisibilityType() == CourseVisibilityType.PUBLIC) {
            return true;
        }
        return courseVisibleStudentRepository.existsByCourseIdAndStudentId(course.getId(), studentId);
    }

    private void ensurePublishable(CourseEntity course) {
        if (course.getStatus() == CourseStatus.PUBLISHED) {
            throw BusinessException.badRequest("Course is already published");
        }
        if (course.getVisibilityType() == CourseVisibilityType.SELECTED_STUDENTS
                && courseVisibleStudentRepository.countByCourseId(course.getId()) == 0) {
            throw BusinessException.badRequest("Targeted courses must select at least one visible student before publishing");
        }
    }

    private void publishCoursePublishedNotifications(CourseEntity course) {
        Set<Long> recipientIds = new LinkedHashSet<>();
        if (course.getVisibilityType() == CourseVisibilityType.SELECTED_STUDENTS) {
            recipientIds.addAll(courseVisibleStudentRepository.findByCourseId(course.getId()).stream()
                    .map(CourseVisibleStudentEntity::getStudentId)
                    .toList());
        }
        recipientIds.addAll(enrollmentRepository.findAll().stream()
                .filter(enrollment -> enrollment.getCourseId().equals(course.getId()))
                .map(EnrollmentEntity::getStudentId)
                .toList());

        for (Long recipientId : recipientIds) {
            outboxService.enqueue(EventTopics.NOTIFICATION_SEND, NotificationSendEvent.builder()
                    .userId(recipientId)
                    .type("COURSE")
                    .title("新课程已发布")
                    .content("课程《" + course.getTitle() + "》已发布，现在可以查看并选课。")
                    .build());
        }
    }

    private void publishCourseUpdatedEvent(CourseEntity course, String changeType) {
        outboxService.enqueue(EventTopics.COURSE_UPDATED, CourseUpdatedEvent.builder()
                .courseId(course.getId())
                .teacherId(course.getTeacherId())
                .title(course.getTitle())
                .status(course.getStatus().name())
                .visibilityType(course.getVisibilityType().name())
                .changeType(changeType)
                .updatedAt(course.getUpdatedAt() == null ? null : course.getUpdatedAt().toString())
                .build());
    }

    private void publishKnowledgePointUpdatedEvent(KnowledgePointEntity entity) {
        outboxService.enqueue(EventTopics.KNOWLEDGE_POINT_UPDATED, KnowledgePointUpdatedEvent.builder()
                .knowledgePointId(entity.getId())
                .parentId(entity.getParentId())
                .name(entity.getName())
                .nodeType(entity.getNodeType().name())
                .active(Boolean.TRUE.equals(entity.getActive()))
                .updatedAt(entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().toString())
                .build());
    }

    private void publishResourceUploadedEventIfNeeded(
            ResourceEntity resource,
            ChapterEntity chapter,
            CourseEntity course,
            List<Long> manualKnowledgePointIds,
            List<String> manualTagLabels) {
        if ((manualKnowledgePointIds != null && !manualKnowledgePointIds.isEmpty())
                || (manualTagLabels != null && !manualTagLabels.isEmpty())) {
            return;
        }
        ResourceUploadedEvent event = ResourceUploadedEvent.builder()
                .resourceId(resource.getId())
                .chapterId(resource.getChapterId())
                .courseId(course.getId())
                .teacherId(course.getTeacherId())
                .title(resource.getTitle())
                .description(resource.getDescription())
                .type(resource.getType().name())
                .storageKey(resource.getStorageKey())
                .build();

        runAfterCurrentCommit(() -> requestAiTagging(event));
    }

    private void requestAiTagging(ResourceUploadedEvent event) {
        resourceTagAgentClient.requestTagging(event).ifPresentOrElse(
                taggedEvent -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> applyAiTaggedResource(taggedEvent)),
                () -> outboxService.enqueue(EventTopics.RESOURCE_UPLOADED, event)
        );
    }

    private void runAfterCurrentCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private Map<Long, String> resolveTeacherNames(List<CourseEntity> courses) {
        Map<Long, String> teacherNames = new HashMap<>();
        for (CourseEntity course : courses) {
            teacherNames.computeIfAbsent(course.getTeacherId(), this::resolveTeacherName);
        }
        return teacherNames;
    }

    private String resolveTeacherName(Long teacherId) {
        try {
            UserServiceResponse response = userServiceClient.getUserById(teacherId);
            if (response != null && response.getData() != null && StringUtils.hasText(response.getData().getUsername())) {
                return response.getData().getUsername();
            }
        } catch (FeignException ex) {
            // Degrade gracefully: course data should still be available even if user-service is unavailable.
        } catch (Exception ex) {
            // Keep course queries available while user-service is unstable or misconfigured.
        }
        return "User-" + teacherId;
    }

    private void recordCourseAudit(UserContext userContext, CourseEntity course, String action, String detail) {
        recordCourseAudit(userContext, course.getId(), course.getTitle(), action, detail);
    }

    private void recordCourseAudit(UserContext userContext, Long courseId, String title, String action, String detail) {
        try {
            userServiceClient.createAdminAuditLog(CreateAdminAuditLogRequest.builder()
                    .actorId(userContext.userId())
                    .action(action)
                    .targetType(AUDIT_TARGET_COURSE)
                    .targetId(courseId)
                    .targetName(title)
                    .detail(detail)
                    .build());
        } catch (FeignException ex) {
            log.warn("Failed to record course audit log: action={}, courseId={}, status={}", action, courseId, ex.status());
        } catch (Exception ex) {
            log.warn("Failed to record course audit log: action={}, courseId={}", action, courseId, ex);
        }
    }

    private Map<Long, List<Long>> loadVisibleStudentIds(List<CourseEntity> courses) {
        if (courses.isEmpty()) {
            return Map.of();
        }

        List<Long> courseIds = courses.stream().map(CourseEntity::getId).toList();
        Map<Long, List<Long>> visibleStudentIdsByCourse = new HashMap<>();
        for (CourseVisibleStudentEntity item : courseVisibleStudentRepository.findByCourseIdIn(courseIds)) {
            visibleStudentIdsByCourse.computeIfAbsent(item.getCourseId(), ignored -> new ArrayList<>())
                    .add(item.getStudentId());
        }
        return visibleStudentIdsByCourse;
    }

    private CourseResponse toCourseResponseAfterMutation(CourseEntity course) {
        return toCourseResponse(
                course,
                Map.of(course.getTeacherId(), resolveTeacherName(course.getTeacherId())),
                loadVisibleStudentIds(List.of(course)),
                true
        );
    }

    private CourseResponse toCourseResponse(
            CourseEntity entity,
            Map<Long, String> teacherNames,
            Map<Long, List<Long>> visibleStudentIdsByCourse,
            boolean includeManageFields
    ) {
        List<Long> visibleStudentIds = visibleStudentIdsByCourse.getOrDefault(entity.getId(), List.of());
        return CourseResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .coverImage(entity.getCoverKey())
                .teacherId(entity.getTeacherId())
                .teacherName(teacherNames.getOrDefault(entity.getTeacherId(), "User-" + entity.getTeacherId()))
                .status(entity.getStatus().name())
                .visibilityType(entity.getVisibilityType().name())
                .visibleStudentIds(includeManageFields ? visibleStudentIds : null)
                .visibleStudentCount(visibleStudentIds.size())
                .createdAt(entity.getCreatedAt().toString())
                .updatedAt(entity.getUpdatedAt().toString())
                .build();
    }

    private ChapterResponse toChapterResponse(ChapterEntity entity) {
        return ChapterResponse.builder()
                .id(entity.getId())
                .courseId(entity.getCourseId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .orderIndex(entity.getOrderIndex())
                .createdAt(entity.getCreatedAt().toString())
                .build();
    }

    private ResourceResponse toResourceResponse(
            ResourceEntity entity,
            List<ResourceKnowledgePointResponse> knowledgePoints,
            List<ResourceTagResponse> tags
    ) {
        boolean managedFile = resourceStorageService.isManagedStorageKey(entity.getStorageKey());
        return ResourceResponse.builder()
                .id(entity.getId())
                .chapterId(entity.getChapterId())
                .title(entity.getTitle())
                .type(entity.getType().name())
                .url(managedFile ? buildManagedResourceUrl(entity.getId()) : entity.getStorageKey())
                .sourceUrl(managedFile ? null : entity.getStorageKey())
                .description(entity.getDescription())
                .managedFile(managedFile)
                .taggingStatus(entity.getTaggingStatus() == null ? ResourceTaggingStatus.UNTAGGED.name() : entity.getTaggingStatus().name())
                .taggingUpdatedAt(entity.getTaggingUpdatedAt() == null ? null : entity.getTaggingUpdatedAt().toString())
                .knowledgePoints(knowledgePoints)
                .tags(tags)
                .duration(entity.getDurationSeconds())
                .size(entity.getFileSize())
                .orderIndex(entity.getOrderIndex())
                .createdAt(entity.getCreatedAt().toString())
                .exerciseQuestions(deserializeExerciseQuestions(entity.getExerciseContent()))
                .build();
    }

    private String serializeExerciseQuestions(List<ResourceUpsertRequest.ExerciseQuestionRequest> questions) {
        List<ResourceResponse.ExerciseQuestionResponse> normalizedQuestions = normalizeExerciseQuestions(questions);
        if (normalizedQuestions.isEmpty()) {
            throw BusinessException.badRequest("Exercise resource must contain at least one single-choice question");
        }
        try {
            return objectMapper.writeValueAsString(normalizedQuestions);
        } catch (JsonProcessingException ex) {
            throw BusinessException.badRequest("Invalid exercise content");
        }
    }

    private List<ResourceResponse.ExerciseQuestionResponse> normalizeExerciseQuestions(List<ResourceUpsertRequest.ExerciseQuestionRequest> questions) {
        if (questions == null || questions.isEmpty()) {
            return List.of();
        }
        List<ResourceResponse.ExerciseQuestionResponse> result = new ArrayList<>();
        int index = 0;
        for (ResourceUpsertRequest.ExerciseQuestionRequest question : questions) {
            if (question == null || !StringUtils.hasText(question.getStem())) {
                continue;
            }
            List<ResourceResponse.ExerciseOptionResponse> options = normalizeExerciseOptions(question.getOptions());
            if (options.size() < 2) {
                continue;
            }
            String answer = StringUtils.hasText(question.getAnswer()) ? question.getAnswer().trim().toUpperCase(Locale.ROOT) : "";
            Set<String> optionIds = options.stream().map(ResourceResponse.ExerciseOptionResponse::getId).collect(java.util.stream.Collectors.toSet());
            if (!optionIds.contains(answer)) {
                answer = options.get(0).getId();
            }
            result.add(ResourceResponse.ExerciseQuestionResponse.builder()
                    .id(StringUtils.hasText(question.getId()) ? question.getId().trim() : UUID.randomUUID().toString())
                    .stem(question.getStem().trim())
                    .options(options)
                    .answer(answer)
                    .explanation(normalizeBlank(question.getExplanation()))
                    .build());
            index += 1;
            if (index >= MAX_EXERCISE_QUESTIONS) {
                break;
            }
        }
        return result;
    }

    private List<ResourceResponse.ExerciseOptionResponse> normalizeExerciseOptions(List<ResourceUpsertRequest.ExerciseOptionRequest> options) {
        if (options == null || options.isEmpty()) {
            return List.of();
        }
        List<String> fallbackIds = List.of("A", "B", "C", "D");
        List<ResourceResponse.ExerciseOptionResponse> result = new ArrayList<>();
        Set<String> usedIds = new LinkedHashSet<>();
        for (int index = 0; index < options.size() && result.size() < 6; index++) {
            ResourceUpsertRequest.ExerciseOptionRequest option = options.get(index);
            if (option == null || !StringUtils.hasText(option.getText())) {
                continue;
            }
            String fallbackId = index < fallbackIds.size() ? fallbackIds.get(index) : String.valueOf(index + 1);
            String id = StringUtils.hasText(option.getId()) ? option.getId().trim().toUpperCase(Locale.ROOT) : fallbackId;
            if (usedIds.contains(id)) {
                id = fallbackId;
            }
            usedIds.add(id);
            result.add(ResourceResponse.ExerciseOptionResponse.builder()
                    .id(id)
                    .text(option.getText().trim())
                    .build());
        }
        return result;
    }

    private List<ResourceResponse.ExerciseQuestionResponse> deserializeExerciseQuestions(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(content, new TypeReference<>() {
            });
        } catch (Exception ex) {
            log.warn("Failed to parse exercise content", ex);
            return List.of();
        }
    }

    private String buildManagedResourceUrl(Long resourceId) {
        return "/api/v1/resources/" + resourceId + "/content";
    }

    private void deleteManagedResourcesForCourse(Long courseId) {
        List<Long> chapterIds = chapterRepository.findByCourseIdOrderByOrderIndexAscIdAsc(courseId).stream()
                .map(ChapterEntity::getId)
                .toList();
        deleteManagedResourcesByChapterIds(chapterIds);
    }

    private void deleteManagedResourcesByChapterIds(List<Long> chapterIds) {
        if (chapterIds == null || chapterIds.isEmpty()) {
            return;
        }
        resourceRepository.findByChapterIdInOrderByOrderIndexAscIdAsc(chapterIds).stream()
                .map(ResourceEntity::getStorageKey)
                .forEach(resourceStorageService::deleteIfManaged);
    }

    private Map<Long, KnowledgePointEntity> loadKnowledgePointMap() {
        return knowledgePointRepository.findAllByOrderByOrderIndexAscIdAsc().stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getId(), item), Map::putAll);
    }

    private List<KnowledgePointNodeResponse> buildKnowledgePointTree(List<KnowledgePointEntity> knowledgePoints, Map<Long, KnowledgePointEntity> providedMap) {
        Map<Long, KnowledgePointEntity> knowledgePointMap = providedMap.isEmpty()
                ? knowledgePoints.stream().collect(LinkedHashMap::new, (map, item) -> map.put(item.getId(), item), Map::putAll)
                : providedMap;

        Map<Long, List<KnowledgePointEntity>> childrenByParent = new LinkedHashMap<>();
        List<KnowledgePointEntity> roots = new ArrayList<>();
        for (KnowledgePointEntity knowledgePoint : knowledgePoints) {
            if (knowledgePoint.getParentId() == null) {
                roots.add(knowledgePoint);
            } else {
                childrenByParent.computeIfAbsent(knowledgePoint.getParentId(), ignored -> new ArrayList<>()).add(knowledgePoint);
            }
        }

        return roots.stream()
                .sorted(Comparator.comparing(KnowledgePointEntity::getOrderIndex).thenComparing(KnowledgePointEntity::getId))
                .map(root -> toKnowledgePointTreeNode(root, knowledgePointMap, childrenByParent))
                .toList();
    }

    private List<KnowledgePointEntity> collectKnowledgePointSubtree(Long rootId, List<KnowledgePointEntity> knowledgePoints) {
        Map<Long, List<KnowledgePointEntity>> childrenByParent = new LinkedHashMap<>();
        Map<Long, KnowledgePointEntity> knowledgePointMap = new LinkedHashMap<>();
        for (KnowledgePointEntity knowledgePoint : knowledgePoints) {
            knowledgePointMap.put(knowledgePoint.getId(), knowledgePoint);
            if (knowledgePoint.getParentId() != null) {
                childrenByParent.computeIfAbsent(knowledgePoint.getParentId(), ignored -> new ArrayList<>()).add(knowledgePoint);
            }
        }
        childrenByParent.values().forEach(children -> children.sort(
                Comparator.comparing(KnowledgePointEntity::getOrderIndex).thenComparing(KnowledgePointEntity::getId)));

        List<KnowledgePointEntity> result = new ArrayList<>();
        collectKnowledgePointSubtree(rootId, knowledgePointMap, childrenByParent, result, new LinkedHashSet<>());
        return result;
    }

    private void collectKnowledgePointSubtree(
            Long knowledgePointId,
            Map<Long, KnowledgePointEntity> knowledgePointMap,
            Map<Long, List<KnowledgePointEntity>> childrenByParent,
            List<KnowledgePointEntity> collector,
            Set<Long> visited
    ) {
        if (!visited.add(knowledgePointId)) {
            return;
        }
        KnowledgePointEntity current = knowledgePointMap.get(knowledgePointId);
        if (current == null) {
            return;
        }
        collector.add(current);
        for (KnowledgePointEntity child : childrenByParent.getOrDefault(knowledgePointId, List.of())) {
            collectKnowledgePointSubtree(child.getId(), knowledgePointMap, childrenByParent, collector, visited);
        }
    }

    private Map<Long, Set<Long>> loadKnowledgePointDirectResourceIds(
            Map<Long, KnowledgePointEntity> knowledgePointMap,
            List<ResourceTagRepository.ResourceTagKnowledgeLinkProjection> tagLinks) {
        Map<Long, Set<Long>> resourceIdsByKnowledgePoint = new LinkedHashMap<>();
        Map<String, List<Long>> knowledgePointIdsByNormalizedName = new LinkedHashMap<>();
        for (KnowledgePointEntity knowledgePoint : knowledgePointMap.values()) {
            knowledgePointIdsByNormalizedName
                    .computeIfAbsent(normalizeSuggestionText(knowledgePoint.getName()), ignored -> new ArrayList<>())
                    .add(knowledgePoint.getId());
        }

        for (ResourceKnowledgePointRepository.ResourceKnowledgePointLinkProjection link : resourceKnowledgePointRepository.findResourceKnowledgePointLinks()) {
            if (link.getKnowledgePointId() == null || link.getResourceId() == null || !knowledgePointMap.containsKey(link.getKnowledgePointId())) {
                continue;
            }
            resourceIdsByKnowledgePoint
                    .computeIfAbsent(link.getKnowledgePointId(), ignored -> new LinkedHashSet<>())
                    .add(link.getResourceId());
        }

        for (ResourceTagRepository.ResourceTagKnowledgeLinkProjection link : tagLinks) {
            if (link.getResourceId() == null) {
                continue;
            }
            if (link.getKnowledgePointId() != null && knowledgePointMap.containsKey(link.getKnowledgePointId())) {
                resourceIdsByKnowledgePoint
                        .computeIfAbsent(link.getKnowledgePointId(), ignored -> new LinkedHashSet<>())
                        .add(link.getResourceId());
            }

            String normalizedLabel = normalizeSuggestionText(link.getNormalizedLabel());
            if (!StringUtils.hasText(normalizedLabel)) {
                continue;
            }
            for (Long matchedKnowledgePointId : knowledgePointIdsByNormalizedName.getOrDefault(normalizedLabel, List.of())) {
                resourceIdsByKnowledgePoint
                        .computeIfAbsent(matchedKnowledgePointId, ignored -> new LinkedHashSet<>())
                        .add(link.getResourceId());
            }
        }

        return resourceIdsByKnowledgePoint;
    }

    private Map<Long, Integer> toResourceCountMap(Map<Long, Set<Long>> resourceIdsByKnowledgePoint) {
        Map<Long, Integer> counts = new LinkedHashMap<>();
        resourceIdsByKnowledgePoint.forEach((knowledgePointId, resourceIds) -> counts.put(knowledgePointId, resourceIds.size()));
        return counts;
    }

    private void appendTagDerivedKnowledgePointNodes(
            KnowledgePointEntity root,
            Map<Long, KnowledgePointEntity> sourceMap,
            Map<Long, KnowledgePointEntity> scopedMap,
            Map<Long, Set<Long>> directResourceIds,
            List<ResourceTagRepository.ResourceTagKnowledgeLinkProjection> tagLinks,
            List<KnowledgeGraphNodeResponse> nodes,
            List<KnowledgeGraphEdgeResponse> edges
    ) {
        Set<Long> scopedResourceIds = scopedMap.keySet().stream()
                .map(id -> directResourceIds.getOrDefault(id, Set.of()))
                .flatMap(Collection::stream)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        if (scopedResourceIds.isEmpty()) {
            return;
        }

        Set<String> existingLabels = scopedMap.values().stream()
                .map(KnowledgePointEntity::getName)
                .map(this::normalizeSuggestionText)
                .filter(StringUtils::hasText)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);

        Map<String, TagDerivedKnowledgePointAccumulator> tagAccumulators = new LinkedHashMap<>();
        for (ResourceTagRepository.ResourceTagKnowledgeLinkProjection link : tagLinks) {
            if (link.getResourceId() == null || !scopedResourceIds.contains(link.getResourceId())) {
                continue;
            }
            String normalizedLabel = normalizeSuggestionText(link.getNormalizedLabel());
            if (!StringUtils.hasText(normalizedLabel) || existingLabels.contains(normalizedLabel)) {
                continue;
            }
            String label = StringUtils.hasText(link.getLabel()) ? link.getLabel().trim() : normalizedLabel;
            tagAccumulators
                    .computeIfAbsent(normalizedLabel, ignored -> new TagDerivedKnowledgePointAccumulator(label))
                    .add(link.getResourceId());
        }

        long derivedId = -1L;
        String rootPath = buildKnowledgePointPath(root.getId(), sourceMap);
        for (TagDerivedKnowledgePointAccumulator accumulator : tagAccumulators.values().stream()
                .sorted(Comparator.comparing(TagDerivedKnowledgePointAccumulator::resourceCount).reversed()
                        .thenComparing(TagDerivedKnowledgePointAccumulator::label, String::compareToIgnoreCase))
                .toList()) {
            int resourceCount = accumulator.resourceCount();
            nodes.add(KnowledgeGraphNodeResponse.builder()
                    .id(derivedId)
                    .parentId(root.getId())
                    .name(accumulator.label())
                    .path(rootPath + " / " + accumulator.label())
                    .nodeType(KnowledgePointType.POINT.name())
                    .active(true)
                    .depth(1)
                    .directResourceCount(resourceCount)
                    .resourceCount(resourceCount)
                    .coverageLevel(resolveKnowledgeGraphCoverageLevel(resourceCount))
                    .color(resolveKnowledgeGraphColor(resourceCount))
                    .build());
            edges.add(KnowledgeGraphEdgeResponse.builder()
                    .source(root.getId())
                    .target(derivedId)
                    .relation("PARENT_CHILD")
                    .build());
            derivedId--;
        }
    }

    private int calculateSubtreeResourceCount(
            Long knowledgePointId,
            Map<Long, List<KnowledgePointEntity>> childrenByParent,
            Map<Long, Integer> directCounts,
            Map<Long, Integer> subtreeCounts,
            Set<Long> visited
    ) {
        if (subtreeCounts.containsKey(knowledgePointId)) {
            return subtreeCounts.get(knowledgePointId);
        }
        if (!visited.add(knowledgePointId)) {
            return directCounts.getOrDefault(knowledgePointId, 0);
        }

        int count = directCounts.getOrDefault(knowledgePointId, 0);
        for (KnowledgePointEntity child : childrenByParent.getOrDefault(knowledgePointId, List.of())) {
            count += calculateSubtreeResourceCount(child.getId(), childrenByParent, directCounts, subtreeCounts, visited);
        }
        subtreeCounts.put(knowledgePointId, count);
        visited.remove(knowledgePointId);
        return count;
    }

    private Map<Long, Integer> calculateKnowledgeGraphDepths(
            List<KnowledgePointEntity> scopedKnowledgePoints,
            Map<Long, KnowledgePointEntity> scopedMap
    ) {
        Map<Long, Integer> depths = new LinkedHashMap<>();
        for (KnowledgePointEntity knowledgePoint : scopedKnowledgePoints) {
            depths.put(knowledgePoint.getId(), calculateKnowledgeGraphDepth(knowledgePoint, scopedMap, new LinkedHashSet<>()));
        }
        return depths;
    }

    private int calculateKnowledgeGraphDepth(
            KnowledgePointEntity knowledgePoint,
            Map<Long, KnowledgePointEntity> scopedMap,
            Set<Long> visited
    ) {
        int depth = 0;
        KnowledgePointEntity cursor = knowledgePoint;
        while (cursor.getParentId() != null && scopedMap.containsKey(cursor.getParentId()) && visited.add(cursor.getId())) {
            depth++;
            cursor = scopedMap.get(cursor.getParentId());
        }
        return depth;
    }

    private String resolveKnowledgeGraphCoverageLevel(int resourceCount) {
        if (resourceCount <= 0) {
            return "NONE";
        }
        if (resourceCount <= 2) {
            return "LOW";
        }
        if (resourceCount <= 5) {
            return "MEDIUM";
        }
        if (resourceCount <= 10) {
            return "HIGH";
        }
        return "VERY_HIGH";
    }

    private String resolveKnowledgeGraphColor(int resourceCount) {
        return switch (resolveKnowledgeGraphCoverageLevel(resourceCount)) {
            case "LOW" -> "#f56c6c";
            case "MEDIUM" -> "#e6a23c";
            case "HIGH" -> "#409eff";
            case "VERY_HIGH" -> "#67c23a";
            default -> "#c0c4cc";
        };
    }

    private KnowledgePointNodeResponse toKnowledgePointTreeNode(
            KnowledgePointEntity entity,
            Map<Long, KnowledgePointEntity> knowledgePointMap,
            Map<Long, List<KnowledgePointEntity>> childrenByParent
    ) {
        List<KnowledgePointNodeResponse> children = childrenByParent.getOrDefault(entity.getId(), List.of()).stream()
                .sorted(Comparator.comparing(KnowledgePointEntity::getOrderIndex).thenComparing(KnowledgePointEntity::getId))
                .map(child -> toKnowledgePointTreeNode(child, knowledgePointMap, childrenByParent))
                .toList();
        return toKnowledgePointNodeResponse(entity, knowledgePointMap, children);
    }

    private KnowledgePointNodeResponse toKnowledgePointNodeResponse(
            KnowledgePointEntity entity,
            Map<Long, KnowledgePointEntity> knowledgePointMap,
            List<KnowledgePointNodeResponse> children
    ) {
        return KnowledgePointNodeResponse.builder()
                .id(entity.getId())
                .parentId(entity.getParentId())
                .name(entity.getName())
                .description(entity.getDescription())
                .keywords(entity.getKeywords())
                .nodeType(entity.getNodeType().name())
                .active(entity.getActive())
                .orderIndex(entity.getOrderIndex())
                .path(buildKnowledgePointPath(entity.getId(), knowledgePointMap))
                .children(children)
                .build();
    }

    private String buildKnowledgePointPath(Long knowledgePointId, Map<Long, KnowledgePointEntity> knowledgePointMap) {
        List<String> names = new ArrayList<>();
        Set<Long> visited = new LinkedHashSet<>();
        KnowledgePointEntity cursor = knowledgePointMap.get(knowledgePointId);
        while (cursor != null && visited.add(cursor.getId())) {
            names.add(cursor.getName());
            cursor = cursor.getParentId() == null ? null : knowledgePointMap.get(cursor.getParentId());
        }
        Collections.reverse(names);
        return String.join(" / ", names);
    }

    private Map<Long, List<ResourceKnowledgePointResponse>> loadResourceKnowledgePointMap(Collection<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, KnowledgePointEntity> knowledgePointMap = loadKnowledgePointMap();
        Map<Long, List<ResourceKnowledgePointResponse>> result = new LinkedHashMap<>();
        for (ResourceKnowledgePointEntity relation : resourceKnowledgePointRepository.findByResourceIdIn(resourceIds)) {
            KnowledgePointEntity knowledgePoint = knowledgePointMap.get(relation.getKnowledgePointId());
            if (knowledgePoint == null) {
                continue;
            }
            result.computeIfAbsent(relation.getResourceId(), ignored -> new ArrayList<>())
                    .add(ResourceKnowledgePointResponse.builder()
                            .id(knowledgePoint.getId())
                            .name(knowledgePoint.getName())
                            .nodeType(knowledgePoint.getNodeType().name())
                            .path(buildKnowledgePointPath(knowledgePoint.getId(), knowledgePointMap))
                            .confidence(relation.getConfidence())
                            .source(relation.getSource().name())
                            .build());
        }
        result.values().forEach(items -> items.sort(Comparator
                .comparing(ResourceKnowledgePointResponse::getPath)
                .thenComparing(ResourceKnowledgePointResponse::getName)));
        return result;
    }

    private Map<Long, List<ResourceTagResponse>> loadResourceTagMap(Collection<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, KnowledgePointEntity> knowledgePointMap = loadKnowledgePointMap();
        Map<Long, List<ResourceTagResponse>> result = new LinkedHashMap<>();
        for (ResourceTagEntity tag : resourceTagRepository.findByResourceIdIn(resourceIds)) {
            result.computeIfAbsent(tag.getResourceId(), ignored -> new ArrayList<>())
                    .add(ResourceTagResponse.builder()
                            .id(tag.getId())
                            .label(tag.getLabel())
                            .confidence(tag.getConfidence())
                            .source(tag.getSource().name())
                            .knowledgePointId(tag.getKnowledgePointId())
                            .knowledgePointPath(tag.getKnowledgePointId() == null ? null : buildKnowledgePointPath(tag.getKnowledgePointId(), knowledgePointMap))
                            .build());
        }
        result.values().forEach(items -> items.sort(Comparator
                .comparing(ResourceTagResponse::getLabel)
                .thenComparing(item -> item.getId() == null ? Long.MAX_VALUE : item.getId())));
        return result;
    }

    private KnowledgePointEntity requireValidKnowledgePointParent(Long parentId, KnowledgePointType nodeType) {
        if (nodeType == KnowledgePointType.SUBJECT) {
            if (parentId != null) {
                throw BusinessException.badRequest("Subject nodes cannot have a parent");
            }
            return null;
        }

        if (parentId == null) {
            throw BusinessException.badRequest("Non-root knowledge points must specify a parent");
        }

        KnowledgePointEntity parent = requireKnowledgePoint(parentId);
        if (nodeType == KnowledgePointType.DOMAIN && parent.getNodeType() != KnowledgePointType.SUBJECT) {
            throw BusinessException.badRequest("Domain nodes must belong to a subject");
        }
        if (nodeType == KnowledgePointType.POINT && parent.getNodeType() != KnowledgePointType.DOMAIN) {
            throw BusinessException.badRequest("Leaf knowledge points must belong to a domain");
        }
        return parent;
    }

    private boolean isAttachableKnowledgePoint(KnowledgePointEntity knowledgePoint) {
        return knowledgePoint != null && knowledgePoint.getNodeType() != KnowledgePointType.SUBJECT;
    }

    private void replaceManualResourceTags(
            ResourceEntity resource,
            List<Long> requestedKnowledgePointIds,
            List<String> requestedTagLabels
    ) {
        List<Long> knowledgePointIds = normalizeKnowledgePointIds(requestedKnowledgePointIds);
        List<String> tagLabels = normalizeTagLabels(requestedTagLabels);
        List<KnowledgePointEntity> knowledgePoints = knowledgePointIds.isEmpty()
                ? List.of()
                : knowledgePointRepository.findByIdIn(knowledgePointIds);

        if (knowledgePoints.size() != knowledgePointIds.size()) {
            throw BusinessException.badRequest("Some selected knowledge points do not exist");
        }

        for (KnowledgePointEntity knowledgePoint : knowledgePoints) {
            if (!Boolean.TRUE.equals(knowledgePoint.getActive())) {
                throw BusinessException.badRequest("Inactive knowledge points cannot be selected");
            }
            if (knowledgePoint.getNodeType() == KnowledgePointType.SUBJECT) {
                throw BusinessException.badRequest("Subject nodes cannot be attached directly to resources");
            }
        }

        List<KnowledgePointEntity> activeKnowledgePoints = knowledgePointRepository.findByActiveTrueOrderByOrderIndexAscIdAsc();
        Map<Long, KnowledgePointEntity> activeKnowledgePointMap = activeKnowledgePoints.stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getId(), item), Map::putAll);
        Map<Long, KnowledgePointEntity> activeAttachableKnowledgePointMap = activeKnowledgePoints.stream()
                .filter(item -> item.getNodeType() != KnowledgePointType.SUBJECT)
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getId(), item), Map::putAll);
        Map<String, KnowledgePointEntity> knowledgePointByNormalizedLabel = new LinkedHashMap<>();
        for (KnowledgePointEntity knowledgePoint : activeAttachableKnowledgePointMap.values()) {
            knowledgePointByNormalizedLabel.putIfAbsent(normalizeSuggestionText(knowledgePoint.getName()), knowledgePoint);
        }

        resourceKnowledgePointRepository.deleteByResourceId(resource.getId());
        resourceTagRepository.deleteByResourceId(resource.getId());
        if (knowledgePoints.isEmpty() && tagLabels.isEmpty()) {
            resource.setTaggingStatus(ResourceTaggingStatus.UNTAGGED);
            resource.setTaggingUpdatedAt(null);
            resourceRepository.save(resource);
            return;
        }

        LinkedHashMap<Long, ResourceKnowledgePointEntity> relationMap = new LinkedHashMap<>();
        for (KnowledgePointEntity knowledgePoint : knowledgePoints) {
            relationMap.put(knowledgePoint.getId(), ResourceKnowledgePointEntity.builder()
                    .resourceId(resource.getId())
                    .knowledgePointId(knowledgePoint.getId())
                    .confidence(1D)
                    .source(ResourceTagSource.MANUAL)
                    .build());
        }

        List<ResourceTagEntity> tagEntities = new ArrayList<>();
        LinkedHashSet<String> mergedLabels = new LinkedHashSet<>(tagLabels);
        for (KnowledgePointEntity knowledgePoint : knowledgePoints) {
            mergedLabels.add(knowledgePoint.getName());
        }

        for (String tagLabel : mergedLabels) {
            String normalizedLabel = normalizeSuggestionText(tagLabel);
            KnowledgePointEntity matchedKnowledgePoint = knowledgePointByNormalizedLabel.get(normalizedLabel);
            if (matchedKnowledgePoint == null) {
                matchedKnowledgePoint = createKnowledgePointForManualTag(
                        tagLabel,
                        knowledgePoints,
                        activeKnowledgePoints,
                        activeKnowledgePointMap
                );
                knowledgePointByNormalizedLabel.putIfAbsent(normalizedLabel, matchedKnowledgePoint);
                activeAttachableKnowledgePointMap.putIfAbsent(matchedKnowledgePoint.getId(), matchedKnowledgePoint);
            }
            relationMap.putIfAbsent(matchedKnowledgePoint.getId(), ResourceKnowledgePointEntity.builder()
                    .resourceId(resource.getId())
                    .knowledgePointId(matchedKnowledgePoint.getId())
                    .confidence(1D)
                    .source(ResourceTagSource.MANUAL)
                    .build());
            tagEntities.add(ResourceTagEntity.builder()
                    .resourceId(resource.getId())
                    .label(matchedKnowledgePoint.getName())
                    .normalizedLabel(normalizedLabel)
                    .knowledgePointId(matchedKnowledgePoint.getId())
                    .confidence(1D)
                    .source(ResourceTagSource.MANUAL)
                    .build());
        }

        if (!relationMap.isEmpty()) {
            resourceKnowledgePointRepository.saveAll(relationMap.values());
        }
        if (!tagEntities.isEmpty()) {
            resourceTagRepository.saveAll(tagEntities);
        }

        resource.setTaggingStatus(ResourceTaggingStatus.CONFIRMED);
        resource.setTaggingUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        resourceRepository.save(resource);
    }

    private KnowledgePointEntity createKnowledgePointForManualTag(
            String tagLabel,
            List<KnowledgePointEntity> selectedKnowledgePoints,
            List<KnowledgePointEntity> activeKnowledgePoints,
            Map<Long, KnowledgePointEntity> activeKnowledgePointMap
    ) {
        KnowledgePointEntity parent = resolveManualTagParent(
                selectedKnowledgePoints,
                activeKnowledgePoints,
                activeKnowledgePointMap
        );
        if (parent == null) {
            throw BusinessException.badRequest("Please create a subject or select a parent knowledge point before adding new tags");
        }

        KnowledgePointType nodeType = parent.getNodeType() == KnowledgePointType.SUBJECT
                ? KnowledgePointType.DOMAIN
                : KnowledgePointType.POINT;
        KnowledgePointEntity entity = KnowledgePointEntity.builder()
                .parentId(parent.getId())
                .name(tagLabel.trim())
                .description(null)
                .keywords(tagLabel.trim())
                .nodeType(nodeType)
                .active(true)
                .orderIndex(resolveKnowledgePointOrderIndex(parent.getId(), null))
                .build();
        KnowledgePointEntity saved = knowledgePointRepository.save(entity);
        publishKnowledgePointUpdatedEvent(saved);
        activeKnowledgePoints.add(saved);
        activeKnowledgePointMap.put(saved.getId(), saved);
        return saved;
    }

    private KnowledgePointEntity resolveManualTagParent(
            List<KnowledgePointEntity> selectedKnowledgePoints,
            List<KnowledgePointEntity> activeKnowledgePoints,
            Map<Long, KnowledgePointEntity> activeKnowledgePointMap
    ) {
        for (KnowledgePointEntity selected : selectedKnowledgePoints) {
            if (selected.getNodeType() == KnowledgePointType.DOMAIN) {
                return selected;
            }
        }
        for (KnowledgePointEntity selected : selectedKnowledgePoints) {
            if (selected.getNodeType() == KnowledgePointType.POINT && selected.getParentId() != null) {
                KnowledgePointEntity parent = activeKnowledgePointMap.get(selected.getParentId());
                if (parent != null && parent.getNodeType() == KnowledgePointType.DOMAIN) {
                    return parent;
                }
            }
        }
        return activeKnowledgePoints.stream()
                .filter(item -> item.getNodeType() == KnowledgePointType.SUBJECT)
                .findFirst()
                .orElse(null);
    }

    private String normalizeSuggestionText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\s]+", " ");
    }

    private List<String> splitKeywords(String keywords) {
        if (!StringUtils.hasText(keywords)) {
            return List.of();
        }
        String[] rawParts = keywords.split("[,;\\n\\r]+");
        List<String> result = new ArrayList<>();
        for (String rawPart : rawParts) {
            if (StringUtils.hasText(rawPart)) {
                result.add(rawPart.trim());
            }
        }
        return result;
    }

    private static class TagDerivedKnowledgePointAccumulator {
        private final String label;
        private final Set<Long> resourceIds = new LinkedHashSet<>();

        private TagDerivedKnowledgePointAccumulator(String label) {
            this.label = label;
        }

        private void add(Long resourceId) {
            resourceIds.add(resourceId);
        }

        private String label() {
            return label;
        }

        private int resourceCount() {
            return resourceIds.size();
        }
    }

    public record ManagedResourceContent(String title, ResourceType type, String storageKey) {
    }
}
