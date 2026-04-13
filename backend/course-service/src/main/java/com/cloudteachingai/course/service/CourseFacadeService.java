package com.cloudteachingai.course.service;

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
import com.cloudteachingai.course.entity.EnrollmentEntity;
import com.cloudteachingai.course.entity.ResourceEntity;
import com.cloudteachingai.course.entity.enums.CourseStatus;
import com.cloudteachingai.course.entity.enums.ResourceStatus;
import com.cloudteachingai.course.entity.enums.ResourceType;
import com.cloudteachingai.course.exception.BusinessException;
import com.cloudteachingai.course.repository.ChapterRepository;
import com.cloudteachingai.course.repository.CourseRepository;
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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CourseFacadeService {

    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final ResourceRepository resourceRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserServiceClient userServiceClient;

    public PageResponse<CourseResponse> listCourses(UserContext userContext, int page, int pageSize, String keyword, String status) {
        Pageable pageable = PageRequest.of(toPageIndex(page), toPageSize(pageSize), Sort.by(Sort.Direction.DESC, "updatedAt"));
        Specification<CourseEntity> specification = Specification.where(withKeyword(keyword))
                .and(withStatus(status))
                .and(withVisibility(userContext));
        Page<CourseEntity> result = courseRepository.findAll(specification, pageable);
        Map<Long, String> teacherNames = resolveTeacherNames(result.getContent());
        return PageResponse.<CourseResponse>builder()
                .items(result.getContent().stream().map(course -> toCourseResponse(course, teacherNames)).toList())
                .total((int) result.getTotalElements())
                .page(page)
                .pageSize(pageSize)
                .build();
    }

    public PageResponse<CourseResponse> listEnrolledCourses(UserContext userContext, int page, int pageSize) {
        assertRole(userContext, "STUDENT");

        Pageable pageable = PageRequest.of(toPageIndex(page), toPageSize(pageSize), Sort.by(Sort.Direction.DESC, "enrolledAt"));
        Page<EnrollmentEntity> enrollmentPage = enrollmentRepository.findByStudentId(userContext.userId(), pageable);
        List<Long> courseIds = enrollmentPage.getContent().stream().map(EnrollmentEntity::getCourseId).toList();
        List<CourseEntity> courses = courseIds.isEmpty()
                ? Collections.emptyList()
                : courseRepository.findAllByIdInOrderByUpdatedAtDesc(courseIds);
        Map<Long, String> teacherNames = resolveTeacherNames(courses);
        List<CourseResponse> items = courses.stream()
                .map(course -> toCourseResponse(course, teacherNames))
                .toList();

        return PageResponse.<CourseResponse>builder()
                .items(items)
                .total((int) enrollmentPage.getTotalElements())
                .page(page)
                .pageSize(pageSize)
                .build();
    }

    public CourseResponse getCourse(Long id, UserContext userContext) {
        CourseEntity course = requireVisibleCourse(id, userContext);
        return toCourseResponse(course, Map.of(course.getTeacherId(), resolveTeacherName(course.getTeacherId())));
    }

    @Transactional
    public CourseResponse createCourse(CourseUpsertRequest request, UserContext userContext) {
        assertRole(userContext, "TEACHER", "ADMIN");

        CourseEntity course = CourseEntity.builder()
                .teacherId(userContext.userId())
                .title(request.getTitle().trim())
                .description(request.getDescription().trim())
                .coverKey(normalizeBlank(request.getCoverImage()))
                .status(CourseStatus.DRAFT)
                .build();

        CourseEntity saved = courseRepository.save(course);
        return toCourseResponse(saved, Map.of(saved.getTeacherId(), resolveTeacherName(saved.getTeacherId())));
    }

    @Transactional
    public CourseResponse updateCourse(Long id, CourseUpsertRequest request, UserContext userContext) {
        CourseEntity course = requireManageableCourse(id, userContext);
        course.setTitle(request.getTitle().trim());
        course.setDescription(request.getDescription().trim());
        course.setCoverKey(normalizeBlank(request.getCoverImage()));
        CourseEntity saved = courseRepository.save(course);
        return toCourseResponse(saved, Map.of(saved.getTeacherId(), resolveTeacherName(saved.getTeacherId())));
    }

    @Transactional
    public void deleteCourse(Long id, UserContext userContext) {
        courseRepository.delete(requireManageableCourse(id, userContext));
    }

    @Transactional
    public CourseResponse publishCourse(Long id, UserContext userContext) {
        CourseEntity course = requireManageableCourse(id, userContext);
        course.setStatus(CourseStatus.PUBLISHED);
        CourseEntity saved = courseRepository.save(course);
        return toCourseResponse(saved, Map.of(saved.getTeacherId(), resolveTeacherName(saved.getTeacherId())));
    }

    @Transactional
    public void enrollCourse(Long courseId, UserContext userContext) {
        assertRole(userContext, "STUDENT");

        CourseEntity course = requireCourse(courseId);
        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw BusinessException.badRequest("课程尚未发布，无法选课");
        }
        if (enrollmentRepository.existsByStudentIdAndCourseId(userContext.userId(), courseId)) {
            throw BusinessException.conflict("课程已选");
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
                .orElseThrow(() -> BusinessException.notFound("未找到选课记录"));
        enrollmentRepository.delete(enrollment);
    }

    public List<ChapterResponse> listChapters(Long courseId, UserContext userContext) {
        CourseEntity course = requireVisibleCourse(courseId, userContext);
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
            throw BusinessException.badRequest("章节不属于当前课程");
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
            throw BusinessException.badRequest("章节不属于当前课程");
        }
        chapterRepository.delete(chapter);
    }

    public List<ResourceResponse> listResources(Long chapterId, UserContext userContext) {
        ChapterEntity chapter = requireChapter(chapterId);
        requireVisibleCourse(chapter.getCourseId(), userContext);
        return resourceRepository.findByChapterIdOrderByOrderIndexAscIdAsc(chapterId).stream()
                .map(this::toResourceResponse)
                .toList();
    }

    public ResourceResponse getResource(Long resourceId, UserContext userContext) {
        ResourceEntity resource = requireResource(resourceId);
        ChapterEntity chapter = requireChapter(resource.getChapterId());
        requireVisibleCourse(chapter.getCourseId(), userContext);
        return toResourceResponse(resource);
    }

    @Transactional
    public ResourceResponse createResource(Long chapterId, ResourceUpsertRequest request, UserContext userContext) {
        ChapterEntity chapter = requireChapter(chapterId);
        requireManageableCourse(chapter.getCourseId(), userContext);

        ResourceEntity resource = ResourceEntity.builder()
                .chapterId(chapterId)
                .title(request.getTitle().trim())
                .type(parseResourceType(request.getType()))
                .storageKey(request.getUrl().trim())
                .fileSize(request.getSize())
                .durationSeconds(request.getDuration())
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

        resource.setTitle(request.getTitle().trim());
        resource.setType(parseResourceType(request.getType()));
        resource.setStorageKey(request.getUrl().trim());
        resource.setFileSize(request.getSize());
        resource.setDurationSeconds(request.getDuration());
        resource.setOrderIndex(resolveOrderIndex(request.getOrderIndex(), resource.getOrderIndex()));
        return toResourceResponse(resourceRepository.save(resource));
    }

    @Transactional
    public void deleteResource(Long resourceId, UserContext userContext) {
        ResourceEntity resource = requireResource(resourceId);
        ChapterEntity chapter = requireChapter(resource.getChapterId());
        requireManageableCourse(chapter.getCourseId(), userContext);
        resourceRepository.delete(resource);
    }

    public void assertCanManageResource(Long resourceId, UserContext userContext) {
        ResourceEntity resource = requireResource(resourceId);
        ChapterEntity chapter = requireChapter(resource.getChapterId());
        requireManageableCourse(chapter.getCourseId(), userContext);
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
            case "STUDENT" -> (root, query, cb) -> cb.equal(root.get("status"), CourseStatus.PUBLISHED);
            default -> throw BusinessException.forbidden("无权访问课程数据");
        };
    }

    private CourseEntity requireCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> BusinessException.notFound("课程不存在"));
    }

    private ChapterEntity requireChapter(Long chapterId) {
        return chapterRepository.findById(chapterId)
                .orElseThrow(() -> BusinessException.notFound("章节不存在"));
    }

    private ResourceEntity requireResource(Long resourceId) {
        return resourceRepository.findById(resourceId)
                .orElseThrow(() -> BusinessException.notFound("资源不存在"));
    }

    private CourseEntity requireManageableCourse(Long courseId, UserContext userContext) {
        CourseEntity course = requireCourse(courseId);
        if ("ADMIN".equals(userContext.role())) {
            return course;
        }
        if (!"TEACHER".equals(userContext.role())) {
            throw BusinessException.forbidden("当前角色不能维护课程");
        }
        if (!course.getTeacherId().equals(userContext.userId())) {
            throw BusinessException.forbidden("无权维护该课程");
        }
        return course;
    }

    private CourseEntity requireVisibleCourse(Long courseId, UserContext userContext) {
        CourseEntity course = requireCourse(courseId);
        if ("ADMIN".equals(userContext.role())) {
            return course;
        }
        if ("TEACHER".equals(userContext.role()) && course.getTeacherId().equals(userContext.userId())) {
            return course;
        }
        if ("STUDENT".equals(userContext.role())) {
            boolean enrolled = enrollmentRepository.existsByStudentIdAndCourseId(userContext.userId(), courseId);
            if (course.getStatus() == CourseStatus.PUBLISHED || enrolled) {
                return course;
            }
        }
        throw BusinessException.forbidden("无权访问该课程");
    }

    private void assertRole(UserContext userContext, String... roles) {
        for (String role : roles) {
            if (role.equals(userContext.role())) {
                return;
            }
        }
        throw BusinessException.forbidden("当前角色无权执行该操作");
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
            throw BusinessException.badRequest("课程状态非法");
        }
    }

    private ResourceType parseResourceType(String type) {
        try {
            return ResourceType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("资源类型非法");
        }
    }

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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

    private CourseResponse toCourseResponse(CourseEntity entity, Map<Long, String> teacherNames) {
        return CourseResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .coverImage(entity.getCoverKey())
                .teacherId(entity.getTeacherId())
                .teacherName(teacherNames.getOrDefault(entity.getTeacherId(), "User-" + entity.getTeacherId()))
                .status(entity.getStatus().name())
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
        return ResourceResponse.builder()
                .id(entity.getId())
                .chapterId(entity.getChapterId())
                .title(entity.getTitle())
                .type(entity.getType().name())
                .url(entity.getStorageKey())
                .duration(entity.getDurationSeconds())
                .size(entity.getFileSize())
                .orderIndex(entity.getOrderIndex())
                .createdAt(entity.getCreatedAt().toString())
                .build();
    }
}
