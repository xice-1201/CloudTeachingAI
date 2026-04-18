package com.cloudteachingai.course.service;

import com.cloudteachingai.course.client.CreateNotificationRequest;
import com.cloudteachingai.course.client.NotifyServiceClient;
import com.cloudteachingai.course.client.UserServiceClient;
import com.cloudteachingai.course.client.UserServiceResponse;
import com.cloudteachingai.course.controller.CourseController.UserContext;
import com.cloudteachingai.course.dto.ChapterResponse;
import com.cloudteachingai.course.dto.ChapterUpsertRequest;
import com.cloudteachingai.course.dto.CourseResponse;
import com.cloudteachingai.course.dto.CourseUpsertRequest;
import com.cloudteachingai.course.dto.PageResponse;
import com.cloudteachingai.course.dto.ResourceResponse;
import com.cloudteachingai.course.dto.ResourceUpsertRequest;
import com.cloudteachingai.course.entity.ChapterEntity;
import com.cloudteachingai.course.entity.CourseEntity;
import com.cloudteachingai.course.entity.CourseVisibleStudentEntity;
import com.cloudteachingai.course.entity.EnrollmentEntity;
import com.cloudteachingai.course.entity.ResourceEntity;
import com.cloudteachingai.course.entity.enums.CourseStatus;
import com.cloudteachingai.course.entity.enums.CourseVisibilityType;
import com.cloudteachingai.course.entity.enums.ResourceStatus;
import com.cloudteachingai.course.entity.enums.ResourceType;
import com.cloudteachingai.course.exception.BusinessException;
import com.cloudteachingai.course.repository.ChapterRepository;
import com.cloudteachingai.course.repository.CourseRepository;
import com.cloudteachingai.course.repository.CourseVisibleStudentRepository;
import com.cloudteachingai.course.repository.EnrollmentRepository;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CourseFacadeService {

    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final ResourceRepository resourceRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseVisibleStudentRepository courseVisibleStudentRepository;
    private final UserServiceClient userServiceClient;
    private final NotifyServiceClient notifyServiceClient;
    private final CourseCoverStorageService courseCoverStorageService;
    private final ResourceStorageService resourceStorageService;

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
        notifyCoursePublished(saved);
        return toCourseResponseAfterMutation(saved);
    }

    @Transactional
    public CourseResponse unpublishCourse(Long id, UserContext userContext) {
        CourseEntity course = requireManageableCourse(id, userContext);
        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw BusinessException.badRequest("Only published courses can be unpublished");
        }
        course.setStatus(CourseStatus.DRAFT);
        return toCourseResponseAfterMutation(courseRepository.save(course));
    }

    @Transactional
    public CourseResponse archiveCourse(Long id, UserContext userContext) {
        CourseEntity course = requireManageableCourse(id, userContext);
        if (course.getStatus() == CourseStatus.ARCHIVED) {
            throw BusinessException.badRequest("Course is already archived");
        }
        course.setStatus(CourseStatus.ARCHIVED);
        return toCourseResponseAfterMutation(courseRepository.save(course));
    }

    @Transactional
    public CourseResponse restoreCourse(Long id, UserContext userContext) {
        CourseEntity course = requireManageableCourse(id, userContext);
        if (course.getStatus() != CourseStatus.ARCHIVED) {
            throw BusinessException.badRequest("Only archived courses can be restored");
        }
        course.setStatus(CourseStatus.DRAFT);
        return toCourseResponseAfterMutation(courseRepository.save(course));
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
        return resourceRepository.findByChapterIdOrderByOrderIndexAscIdAsc(chapterId).stream()
                .map(this::toResourceResponse)
                .toList();
    }

    public ResourceResponse getResource(Long resourceId, UserContext userContext) {
        ResourceEntity resource = requireResource(resourceId);
        ChapterEntity chapter = requireChapter(resource.getChapterId());
        requireContentAccessibleCourse(chapter.getCourseId(), userContext);
        return toResourceResponse(resource);
    }

    @Transactional
    public ResourceResponse createResource(Long chapterId, ResourceUpsertRequest request, UserContext userContext) {
        ChapterEntity chapter = requireChapter(chapterId);
        requireManageableCourse(chapter.getCourseId(), userContext);

        String storageKey = normalizeBlank(request.getUrl());
        if (!StringUtils.hasText(storageKey)) {
            throw BusinessException.badRequest("Please upload a resource file or provide an external URL");
        }

        ResourceEntity resource = ResourceEntity.builder()
                .chapterId(chapterId)
                .title(request.getTitle().trim())
                .type(parseResourceType(request.getType()))
                .storageKey(storageKey)
                .fileSize(request.getSize())
                .durationSeconds(request.getDuration())
                .description(normalizeBlank(request.getDescription()))
                .orderIndex(resolveResourceOrderIndex(chapterId, request.getOrderIndex()))
                .status(ResourceStatus.PUBLISHED)
                .build();
        return toResourceResponse(resourceRepository.save(resource));
    }

    @Transactional
    public ResourceResponse updateResource(Long resourceId, ResourceUpsertRequest request, UserContext userContext) {
        ResourceEntity resource = requireResource(resourceId);
        ChapterEntity chapter = requireChapter(resource.getChapterId());
        requireManageableCourse(chapter.getCourseId(), userContext);

        String previousStorageKey = resource.getStorageKey();
        String updatedStorageKey = StringUtils.hasText(request.getUrl()) ? request.getUrl().trim() : previousStorageKey;

        resource.setTitle(request.getTitle().trim());
        resource.setType(parseResourceType(request.getType()));
        resource.setStorageKey(updatedStorageKey);
        resource.setFileSize(request.getSize());
        resource.setDurationSeconds(request.getDuration());
        resource.setDescription(normalizeBlank(request.getDescription()));
        resource.setOrderIndex(resolveOrderIndex(request.getOrderIndex(), resource.getOrderIndex()));
        ResourceResponse response = toResourceResponse(resourceRepository.save(resource));
        if (!Objects.equals(previousStorageKey, updatedStorageKey)) {
            resourceStorageService.deleteIfManaged(previousStorageKey);
        }
        return response;
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

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<Long> normalizeVisibleStudentIds(List<Long> visibleStudentIds) {
        if (visibleStudentIds == null || visibleStudentIds.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(visibleStudentIds.stream()
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

    private void notifyCoursePublished(CourseEntity course) {
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
            try {
                notifyServiceClient.createNotification(CreateNotificationRequest.builder()
                        .userId(recipientId)
                        .type("COURSE")
                        .title("New course published")
                        .content("Course \"" + course.getTitle() + "\" has been published. You can now view the course and enroll.")
                        .build());
            } catch (FeignException ex) {
                // Keep publishing available even if notification delivery is temporarily unavailable.
            } catch (Exception ex) {
                // Ignore transient notification issues to avoid blocking course publication.
            }
        }
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

    private ResourceResponse toResourceResponse(ResourceEntity entity) {
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

    public record ManagedResourceContent(String title, ResourceType type, String storageKey) {
    }
}
