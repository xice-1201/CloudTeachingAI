package com.cloudteachingai.course.service;

import com.cloudteachingai.course.client.UserServiceClient;
import com.cloudteachingai.course.client.UserServiceResponse;
import com.cloudteachingai.course.controller.CourseController.UserContext;
import com.cloudteachingai.course.dto.ChapterResponse;
import com.cloudteachingai.course.dto.ChapterUpsertRequest;
import com.cloudteachingai.course.dto.CourseResponse;
import com.cloudteachingai.course.dto.CourseUpsertRequest;
import com.cloudteachingai.course.dto.InternalKnowledgePointResponse;
import com.cloudteachingai.course.dto.InternalResourceTaggingContextResponse;
import com.cloudteachingai.course.dto.KnowledgePointNodeResponse;
import com.cloudteachingai.course.dto.KnowledgePointUpsertRequest;
import com.cloudteachingai.course.dto.PageResponse;
import com.cloudteachingai.course.dto.ResourceKnowledgePointResponse;
import com.cloudteachingai.course.dto.ResourceResponse;
import com.cloudteachingai.course.dto.ResourceTagConfirmRequest;
import com.cloudteachingai.course.dto.ResourceTagPreviewRequest;
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
import feign.FeignException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

@Service
@RequiredArgsConstructor
public class CourseFacadeService {

    private static final int MAX_TAG_SUGGESTIONS = 8;

    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final ResourceRepository resourceRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseVisibleStudentRepository courseVisibleStudentRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final ResourceKnowledgePointRepository resourceKnowledgePointRepository;
    private final UserServiceClient userServiceClient;
    private final CourseCoverStorageService courseCoverStorageService;
    private final ResourceStorageService resourceStorageService;
    private final OutboxService outboxService;

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
        deleteManagedResourcesForCourse(id);
        courseVisibleStudentRepository.deleteByCourseId(id);
        courseRepository.delete(course);
        courseCoverStorageService.deleteIfManaged(course.getCoverKey());
    }

    @Transactional
    public CourseResponse publishCourse(Long id, UserContext userContext) {
        CourseEntity course = requireManageableCourse(id, userContext);
        ensurePublishable(course);
        course.setStatus(CourseStatus.PUBLISHED);
        CourseEntity saved = courseRepository.save(course);
        publishCourseUpdatedEvent(saved, "PUBLISHED");
        publishCoursePublishedNotifications(saved);
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
        return resources.stream()
                .map(resource -> toResourceResponse(resource, resourceTags.getOrDefault(resource.getId(), List.of())))
                .toList();
    }

    public ResourceResponse getResource(Long resourceId, UserContext userContext) {
        ResourceEntity resource = requireResource(resourceId);
        ChapterEntity chapter = requireChapter(resource.getChapterId());
        requireContentAccessibleCourse(chapter.getCourseId(), userContext);
        Map<Long, List<ResourceKnowledgePointResponse>> resourceTags = loadResourceKnowledgePointMap(List.of(resourceId));
        return toResourceResponse(resource, resourceTags.getOrDefault(resourceId, List.of()));
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
                .filter(item -> item.getNodeType() == KnowledgePointType.POINT)
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

    @Transactional
    public ResourceResponse createResource(Long chapterId, ResourceUpsertRequest request, UserContext userContext) {
        ChapterEntity chapter = requireChapter(chapterId);
        CourseEntity course = requireManageableCourse(chapter.getCourseId(), userContext);

        String storageKey = normalizeBlank(request.getUrl());
        if (!StringUtils.hasText(storageKey)) {
            throw BusinessException.badRequest("Please upload a resource file or provide an external URL");
        }

        List<Long> manualKnowledgePointIds = normalizeKnowledgePointIds(request.getKnowledgePointIds());
        ResourceStatus initialStatus = manualKnowledgePointIds.isEmpty() ? ResourceStatus.PROCESSING : ResourceStatus.PUBLISHED;

        ResourceEntity resource = ResourceEntity.builder()
                .chapterId(chapterId)
                .title(request.getTitle().trim())
                .type(parseResourceType(request.getType()))
                .storageKey(storageKey)
                .fileSize(request.getSize())
                .durationSeconds(request.getDuration())
                .description(normalizeBlank(request.getDescription()))
                .orderIndex(resolveResourceOrderIndex(chapterId, request.getOrderIndex()))
                .status(initialStatus)
                .build();

        ResourceEntity saved = resourceRepository.save(resource);
        replaceResourceKnowledgePoints(saved, manualKnowledgePointIds);
        publishResourceUploadedEventIfNeeded(saved, chapter, course, manualKnowledgePointIds);
        Map<Long, List<ResourceKnowledgePointResponse>> resourceTags = loadResourceKnowledgePointMap(List.of(saved.getId()));
        return toResourceResponse(saved, resourceTags.getOrDefault(saved.getId(), List.of()));
    }

    @Transactional
    public ResourceResponse updateResource(Long resourceId, ResourceUpsertRequest request, UserContext userContext) {
        ResourceEntity resource = requireResource(resourceId);
        ChapterEntity chapter = requireChapter(resource.getChapterId());
        CourseEntity course = requireManageableCourse(chapter.getCourseId(), userContext);

        String previousStorageKey = resource.getStorageKey();
        String updatedStorageKey = StringUtils.hasText(request.getUrl()) ? request.getUrl().trim() : previousStorageKey;
        List<Long> manualKnowledgePointIds = normalizeKnowledgePointIds(request.getKnowledgePointIds());

        resource.setTitle(request.getTitle().trim());
        resource.setType(parseResourceType(request.getType()));
        resource.setStorageKey(updatedStorageKey);
        resource.setFileSize(request.getSize());
        resource.setDurationSeconds(request.getDuration());
        resource.setDescription(normalizeBlank(request.getDescription()));
        resource.setOrderIndex(resolveOrderIndex(request.getOrderIndex(), resource.getOrderIndex()));
        resource.setStatus(manualKnowledgePointIds.isEmpty() ? ResourceStatus.PROCESSING : ResourceStatus.PUBLISHED);
        ResourceEntity saved = resourceRepository.save(resource);
        replaceResourceKnowledgePoints(saved, manualKnowledgePointIds);
        publishResourceUploadedEventIfNeeded(saved, chapter, course, manualKnowledgePointIds);
        if (!Objects.equals(previousStorageKey, updatedStorageKey)) {
            resourceStorageService.deleteIfManaged(previousStorageKey);
        }
        Map<Long, List<ResourceKnowledgePointResponse>> resourceTags = loadResourceKnowledgePointMap(List.of(saved.getId()));
        return toResourceResponse(saved, resourceTags.getOrDefault(saved.getId(), List.of()));
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

    @Transactional
    public KnowledgePointNodeResponse createKnowledgePoint(KnowledgePointUpsertRequest request, UserContext userContext) {
        assertRole(userContext, "ADMIN");
        KnowledgePointType nodeType = parseKnowledgePointType(request.getNodeType());
        KnowledgePointEntity parent = requireValidKnowledgePointParent(request.getParentId(), nodeType);

        KnowledgePointEntity entity = KnowledgePointEntity.builder()
                .parentId(parent == null ? null : parent.getId())
                .name(request.getName().trim())
                .description(normalizeBlank(request.getDescription()))
                .keywords(normalizeKeywords(request.getKeywords()))
                .nodeType(nodeType)
                .active(request.getActive() == null ? Boolean.TRUE : request.getActive())
                .orderIndex(resolveKnowledgePointOrderIndex(parent == null ? null : parent.getId(), request.getOrderIndex()))
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

    public List<ResourceTagSuggestionResponse> previewResourceTagSuggestions(ResourceTagPreviewRequest request, UserContext userContext) {
        assertRole(userContext, "TEACHER", "ADMIN");
        return buildTagSuggestions(request.getTitle(), request.getDescription());
    }

    public List<ResourceTagSuggestionResponse> getResourceTagSuggestions(Long resourceId, UserContext userContext) {
        ResourceEntity resource = requireResource(resourceId);
        ChapterEntity chapter = requireChapter(resource.getChapterId());
        requireManageableCourse(chapter.getCourseId(), userContext);
        return buildTagSuggestions(resource.getTitle(), resource.getDescription());
    }

    @Transactional
    public ResourceResponse confirmResourceTags(Long resourceId, ResourceTagConfirmRequest request, UserContext userContext) {
        ResourceEntity resource = requireResource(resourceId);
        ChapterEntity chapter = requireChapter(resource.getChapterId());
        requireManageableCourse(chapter.getCourseId(), userContext);
        replaceResourceKnowledgePoints(resource, request.getKnowledgePointIds());
        resource.setStatus(ResourceStatus.PUBLISHED);
        resourceRepository.save(resource);
        Map<Long, List<ResourceKnowledgePointResponse>> resourceTags = loadResourceKnowledgePointMap(List.of(resource.getId()));
        return toResourceResponse(resource, resourceTags.getOrDefault(resource.getId(), List.of()));
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
                    .filter(item -> item.getNodeType() == KnowledgePointType.POINT)
                    .collect(LinkedHashMap::new, (map, item) -> map.put(item.getId(), item), Map::putAll);

            List<ResourceKnowledgePointEntity> relations = new ArrayList<>();
            for (ResourceTaggedKnowledgePointEvent knowledgePointEvent : knowledgePointEvents) {
                KnowledgePointEntity knowledgePoint = knowledgePointMap.get(knowledgePointEvent.knowledgePointId());
                if (knowledgePoint == null) {
                    continue;
                }
                relations.add(ResourceKnowledgePointEntity.builder()
                        .resourceId(resource.getId())
                        .knowledgePointId(knowledgePoint.getId())
                        .confidence(knowledgePointEvent.confidence() == null ? 0.5D : knowledgePointEvent.confidence())
                        .source(ResourceTagSource.AI)
                        .build());
            }
            if (!relations.isEmpty()) {
                resourceKnowledgePointRepository.saveAll(relations);
            }
        }

        boolean hasSuggestions = resourceKnowledgePointRepository.findByResourceIdIn(List.of(resource.getId())).stream().findAny().isPresent();
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
                && course.getStatus() == CourseStatus.PUBLISHED
                && enrollmentRepository.existsByStudentIdAndCourseId(userContext.userId(), courseId)) {
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
            List<Long> manualKnowledgePointIds) {
        if (manualKnowledgePointIds != null && !manualKnowledgePointIds.isEmpty()) {
            return;
        }
        outboxService.enqueue(EventTopics.RESOURCE_UPLOADED, ResourceUploadedEvent.builder()
                .resourceId(resource.getId())
                .chapterId(resource.getChapterId())
                .courseId(course.getId())
                .teacherId(course.getTeacherId())
                .title(resource.getTitle())
                .description(resource.getDescription())
                .type(resource.getType().name())
                .storageKey(resource.getStorageKey())
                .build());
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

    private ResourceResponse toResourceResponse(ResourceEntity entity, List<ResourceKnowledgePointResponse> knowledgePoints) {
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
                .duration(entity.getDurationSeconds())
                .size(entity.getFileSize())
                .orderIndex(entity.getOrderIndex())
                .createdAt(entity.getCreatedAt().toString())
                .build();
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

    private void replaceResourceKnowledgePoints(ResourceEntity resource, List<Long> requestedKnowledgePointIds) {
        List<Long> knowledgePointIds = normalizeKnowledgePointIds(requestedKnowledgePointIds);
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
            if (knowledgePoint.getNodeType() != KnowledgePointType.POINT) {
                throw BusinessException.badRequest("Only leaf knowledge points can be attached to resources");
            }
        }

        resourceKnowledgePointRepository.deleteByResourceId(resource.getId());
        if (knowledgePoints.isEmpty()) {
            resource.setTaggingStatus(ResourceTaggingStatus.UNTAGGED);
            resource.setTaggingUpdatedAt(null);
            resourceRepository.save(resource);
            return;
        }

        Map<Long, ResourceTagSuggestionResponse> suggestionMap = buildTagSuggestions(resource.getTitle(), resource.getDescription()).stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getKnowledgePointId(), item), Map::putAll);

        List<ResourceKnowledgePointEntity> relations = knowledgePoints.stream()
                .map(knowledgePoint -> {
                    ResourceTagSuggestionResponse suggestion = suggestionMap.get(knowledgePoint.getId());
                    return ResourceKnowledgePointEntity.builder()
                            .resourceId(resource.getId())
                            .knowledgePointId(knowledgePoint.getId())
                            .confidence(suggestion == null ? 1D : suggestion.getConfidence())
                            .source(suggestion == null ? ResourceTagSource.MANUAL : ResourceTagSource.AI)
                            .build();
                })
                .toList();
        resourceKnowledgePointRepository.saveAll(relations);

        resource.setTaggingStatus(ResourceTaggingStatus.CONFIRMED);
        resource.setTaggingUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        resourceRepository.save(resource);
    }

    private List<ResourceTagSuggestionResponse> buildTagSuggestions(String title, String description) {
        String normalizedTitle = normalizeSuggestionText(title);
        String normalizedFullText = normalizeSuggestionText(
                (StringUtils.hasText(title) ? title : "") + " " + (StringUtils.hasText(description) ? description : "")
        );
        if (!StringUtils.hasText(normalizedFullText)) {
            return List.of();
        }

        List<KnowledgePointEntity> activeLeafKnowledgePoints = knowledgePointRepository.findByActiveTrueOrderByOrderIndexAscIdAsc().stream()
                .filter(item -> item.getNodeType() == KnowledgePointType.POINT)
                .toList();
        Map<Long, KnowledgePointEntity> knowledgePointMap = loadKnowledgePointMap();
        List<ResourceTagSuggestionResponse> suggestions = new ArrayList<>();

        for (KnowledgePointEntity knowledgePoint : activeLeafKnowledgePoints) {
            double score = 0D;
            List<String> reasons = new ArrayList<>();
            String pointName = normalizeSuggestionText(knowledgePoint.getName());
            if (StringUtils.hasText(pointName) && normalizedTitle.contains(pointName)) {
                score += 0.75D;
                reasons.add("matched title");
            } else if (StringUtils.hasText(pointName) && normalizedFullText.contains(pointName)) {
                score += 0.65D;
                reasons.add("matched knowledge point name");
            }

            for (String keyword : splitKeywords(knowledgePoint.getKeywords())) {
                String normalizedKeyword = normalizeSuggestionText(keyword);
                if (StringUtils.hasText(normalizedKeyword) && normalizedFullText.contains(normalizedKeyword)) {
                    score += 0.18D;
                    reasons.add("matched keyword \"" + keyword + "\"");
                }
            }

            KnowledgePointEntity domain = knowledgePointMap.get(knowledgePoint.getParentId());
            if (domain != null) {
                String normalizedDomain = normalizeSuggestionText(domain.getName());
                if (StringUtils.hasText(normalizedDomain) && normalizedFullText.contains(normalizedDomain)) {
                    score += 0.08D;
                    reasons.add("matched domain");
                }

                KnowledgePointEntity subject = domain.getParentId() == null ? null : knowledgePointMap.get(domain.getParentId());
                if (subject != null) {
                    String normalizedSubject = normalizeSuggestionText(subject.getName());
                    if (StringUtils.hasText(normalizedSubject) && normalizedFullText.contains(normalizedSubject)) {
                        score += 0.04D;
                        reasons.add("matched subject");
                    }
                }
            }

            if (score < 0.18D) {
                continue;
            }

            suggestions.add(ResourceTagSuggestionResponse.builder()
                    .knowledgePointId(knowledgePoint.getId())
                    .knowledgePointName(knowledgePoint.getName())
                    .path(buildKnowledgePointPath(knowledgePoint.getId(), knowledgePointMap))
                    .confidence(Math.round(Math.min(0.99D, score) * 100.0D) / 100.0D)
                    .reason(String.join("; ", reasons.stream().distinct().toList()))
                    .build());
        }

        return suggestions.stream()
                .sorted(Comparator
                        .comparing(ResourceTagSuggestionResponse::getConfidence, Comparator.reverseOrder())
                        .thenComparing(ResourceTagSuggestionResponse::getPath))
                .limit(MAX_TAG_SUGGESTIONS)
                .toList();
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

    public record ManagedResourceContent(String title, ResourceType type, String storageKey) {
    }
}
