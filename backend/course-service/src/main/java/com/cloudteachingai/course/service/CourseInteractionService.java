package com.cloudteachingai.course.service;

import com.cloudteachingai.course.client.CreateNotificationRequest;
import com.cloudteachingai.course.client.NotifyServiceClient;
import com.cloudteachingai.course.client.UserServiceClient;
import com.cloudteachingai.course.client.UserServiceResponse;
import com.cloudteachingai.course.controller.CourseController.UserContext;
import com.cloudteachingai.course.dto.AnnouncementResponse;
import com.cloudteachingai.course.dto.AnnouncementUpsertRequest;
import com.cloudteachingai.course.dto.DiscussionPostResponse;
import com.cloudteachingai.course.dto.DiscussionPostUpsertRequest;
import com.cloudteachingai.course.event.AnnouncementPublishedEvent;
import com.cloudteachingai.course.event.EventTopics;
import com.cloudteachingai.course.event.NotificationSendEvent;
import com.cloudteachingai.course.entity.ChapterEntity;
import com.cloudteachingai.course.entity.CourseAnnouncementEntity;
import com.cloudteachingai.course.entity.CourseDiscussionPostEntity;
import com.cloudteachingai.course.entity.CourseEntity;
import com.cloudteachingai.course.entity.enums.CourseStatus;
import com.cloudteachingai.course.entity.enums.CourseVisibilityType;
import com.cloudteachingai.course.exception.BusinessException;
import com.cloudteachingai.course.repository.ChapterRepository;
import com.cloudteachingai.course.repository.CourseAnnouncementRepository;
import com.cloudteachingai.course.repository.CourseDiscussionPostRepository;
import com.cloudteachingai.course.repository.CourseRepository;
import com.cloudteachingai.course.repository.CourseVisibleStudentRepository;
import com.cloudteachingai.course.repository.EnrollmentRepository;
import com.cloudteachingai.course.repository.ResourceRepository;
import feign.FeignException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CourseInteractionService {

    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final ResourceRepository resourceRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseVisibleStudentRepository courseVisibleStudentRepository;
    private final CourseAnnouncementRepository courseAnnouncementRepository;
    private final CourseDiscussionPostRepository courseDiscussionPostRepository;
    private final UserServiceClient userServiceClient;
    private final NotifyServiceClient notifyServiceClient;
    private final OutboxService outboxService;

    public List<AnnouncementResponse> listAnnouncements(Long courseId, UserContext userContext) {
        requireSummaryVisibleCourse(courseId, userContext);
        List<CourseAnnouncementEntity> announcements =
                courseAnnouncementRepository.findByCourseIdOrderByPinnedDescPublishedAtDescIdDesc(courseId);
        return toAnnouncementResponses(announcements);
    }

    @Transactional
    public AnnouncementResponse createAnnouncement(
            Long courseId,
            AnnouncementUpsertRequest request,
            UserContext userContext) {
        CourseEntity course = requireManageableCourse(courseId, userContext);
        CourseAnnouncementEntity announcement = courseAnnouncementRepository.save(CourseAnnouncementEntity.builder()
                .courseId(courseId)
                .authorId(userContext.userId())
                .title(request.getTitle().trim())
                .content(request.getContent().trim())
                .pinned(Boolean.TRUE.equals(request.getPinned()))
                .publishedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
        publishAnnouncementPublishedEvent(course, announcement);
        publishAnnouncementNotifications(course, announcement);
        return toAnnouncementResponses(List.of(announcement)).getFirst();
    }

    @Transactional
    public AnnouncementResponse updateAnnouncement(
            Long announcementId,
            AnnouncementUpsertRequest request,
            UserContext userContext) {
        CourseAnnouncementEntity announcement = requireAnnouncement(announcementId);
        requireManageableCourse(announcement.getCourseId(), userContext);
        announcement.setTitle(request.getTitle().trim());
        announcement.setContent(request.getContent().trim());
        announcement.setPinned(Boolean.TRUE.equals(request.getPinned()));
        return toAnnouncementResponses(List.of(courseAnnouncementRepository.save(announcement))).getFirst();
    }

    @Transactional
    public void deleteAnnouncement(Long announcementId, UserContext userContext) {
        CourseAnnouncementEntity announcement = requireAnnouncement(announcementId);
        requireManageableCourse(announcement.getCourseId(), userContext);
        courseAnnouncementRepository.delete(announcement);
    }

    public List<DiscussionPostResponse> listDiscussions(Long courseId, Long resourceId, UserContext userContext) {
        requireDiscussionAccessibleCourse(courseId, userContext);
        if (resourceId != null) {
            requireResourceInCourse(resourceId, courseId);
        }
        List<CourseDiscussionPostEntity> roots = resourceId == null
                ? courseDiscussionPostRepository.findByCourseIdAndParentIdIsNullOrderByCreatedAtDescIdDesc(courseId)
                : courseDiscussionPostRepository.findByCourseIdAndResourceIdAndParentIdIsNullOrderByCreatedAtDescIdDesc(courseId, resourceId);
        return toDiscussionResponses(roots);
    }

    @Transactional
    public DiscussionPostResponse createDiscussion(
            Long courseId,
            DiscussionPostUpsertRequest request,
            UserContext userContext) {
        requireDiscussionAccessibleCourse(courseId, userContext);

        Long resourceId = request.getResourceId();
        Long parentId = request.getParentId();
        CourseDiscussionPostEntity parent = null;
        if (parentId != null) {
            parent = requireDiscussion(parentId);
            if (!Objects.equals(parent.getCourseId(), courseId)) {
                throw BusinessException.badRequest("Discussion reply does not belong to the current course");
            }
            resourceId = parent.getResourceId();
        } else if (resourceId != null) {
            requireResourceInCourse(resourceId, courseId);
        }

        String title = parentId == null ? normalizeTitle(request.getTitle()) : null;
        if (parentId == null && !StringUtils.hasText(title)) {
            throw BusinessException.badRequest("Discussion topic title is required");
        }

        CourseDiscussionPostEntity discussion = courseDiscussionPostRepository.save(CourseDiscussionPostEntity.builder()
                .courseId(courseId)
                .resourceId(resourceId)
                .parentId(parentId)
                .authorId(userContext.userId())
                .title(title)
                .content(request.getContent().trim())
                .build());
        return toDiscussionResponses(List.of(discussion)).getFirst();
    }

    @Transactional
    public void deleteDiscussion(Long discussionId, UserContext userContext) {
        CourseDiscussionPostEntity discussion = requireDiscussion(discussionId);
        CourseEntity course = requireCourse(discussion.getCourseId());
        boolean canDelete = canManageCourse(course, userContext) || Objects.equals(discussion.getAuthorId(), userContext.userId());
        if (!canDelete) {
            throw BusinessException.forbidden("No permission to delete this discussion");
        }
        courseDiscussionPostRepository.delete(discussion);
    }

    private List<AnnouncementResponse> toAnnouncementResponses(List<CourseAnnouncementEntity> announcements) {
        Map<Long, String> authorNames = resolveUserNames(announcements.stream()
                .map(CourseAnnouncementEntity::getAuthorId)
                .toList());
        return announcements.stream()
                .map(announcement -> AnnouncementResponse.builder()
                        .id(announcement.getId())
                        .courseId(announcement.getCourseId())
                        .authorId(announcement.getAuthorId())
                        .authorName(authorNames.getOrDefault(announcement.getAuthorId(), "User-" + announcement.getAuthorId()))
                        .title(announcement.getTitle())
                        .content(announcement.getContent())
                        .pinned(announcement.getPinned())
                        .publishedAt(announcement.getPublishedAt().toString())
                        .createdAt(announcement.getCreatedAt().toString())
                        .updatedAt(announcement.getUpdatedAt().toString())
                        .build())
                .toList();
    }

    private List<DiscussionPostResponse> toDiscussionResponses(List<CourseDiscussionPostEntity> roots) {
        if (roots.isEmpty()) {
            return List.of();
        }

        List<Long> rootIds = roots.stream().map(CourseDiscussionPostEntity::getId).toList();
        List<CourseDiscussionPostEntity> replies = courseDiscussionPostRepository.findByParentIdInOrderByCreatedAtAscIdAsc(rootIds);
        Map<Long, List<CourseDiscussionPostEntity>> repliesByParent = new LinkedHashMap<>();
        for (CourseDiscussionPostEntity reply : replies) {
            repliesByParent.computeIfAbsent(reply.getParentId(), ignored -> new ArrayList<>()).add(reply);
        }

        List<Long> authorIds = new ArrayList<>();
        roots.forEach(item -> authorIds.add(item.getAuthorId()));
        replies.forEach(item -> authorIds.add(item.getAuthorId()));
        Map<Long, String> authorNames = resolveUserNames(authorIds);

        return roots.stream()
                .map(root -> DiscussionPostResponse.builder()
                        .id(root.getId())
                        .courseId(root.getCourseId())
                        .resourceId(root.getResourceId())
                        .parentId(root.getParentId())
                        .authorId(root.getAuthorId())
                        .authorName(authorNames.getOrDefault(root.getAuthorId(), "User-" + root.getAuthorId()))
                        .title(root.getTitle())
                        .content(root.getContent())
                        .createdAt(root.getCreatedAt().toString())
                        .updatedAt(root.getUpdatedAt().toString())
                        .replies(repliesByParent.getOrDefault(root.getId(), List.of()).stream()
                                .map(reply -> DiscussionPostResponse.builder()
                                        .id(reply.getId())
                                        .courseId(reply.getCourseId())
                                        .resourceId(reply.getResourceId())
                                        .parentId(reply.getParentId())
                                        .authorId(reply.getAuthorId())
                                        .authorName(authorNames.getOrDefault(reply.getAuthorId(), "User-" + reply.getAuthorId()))
                                        .title(reply.getTitle())
                                        .content(reply.getContent())
                                        .createdAt(reply.getCreatedAt().toString())
                                        .updatedAt(reply.getUpdatedAt().toString())
                                        .replies(List.of())
                                        .build())
                                .toList())
                        .build())
                .toList();
    }

    private void requireResourceInCourse(Long resourceId, Long courseId) {
        var resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> BusinessException.notFound("Resource not found"));
        ChapterEntity chapter = chapterRepository.findById(resource.getChapterId())
                .orElseThrow(() -> BusinessException.notFound("Chapter not found"));
        if (!Objects.equals(chapter.getCourseId(), courseId)) {
            throw BusinessException.badRequest("Resource does not belong to the current course");
        }
    }

    private CourseAnnouncementEntity requireAnnouncement(Long announcementId) {
        return courseAnnouncementRepository.findById(announcementId)
                .orElseThrow(() -> BusinessException.notFound("Announcement not found"));
    }

    private CourseDiscussionPostEntity requireDiscussion(Long discussionId) {
        return courseDiscussionPostRepository.findById(discussionId)
                .orElseThrow(() -> BusinessException.notFound("Discussion not found"));
    }

    private CourseEntity requireManageableCourse(Long courseId, UserContext userContext) {
        CourseEntity course = requireCourse(courseId);
        if (!canManageCourse(course, userContext)) {
            throw BusinessException.forbidden("No permission to manage this course");
        }
        return course;
    }

    private CourseEntity requireSummaryVisibleCourse(Long courseId, UserContext userContext) {
        CourseEntity course = requireCourse(courseId);
        if (canManageCourse(course, userContext)) {
            return course;
        }
        if (!"STUDENT".equals(userContext.role())) {
            throw BusinessException.forbidden("Only students can access this course");
        }
        if (!canStudentViewCourseSummary(course, userContext.userId())) {
            throw BusinessException.forbidden("No access to this course");
        }
        return course;
    }

    private CourseEntity requireDiscussionAccessibleCourse(Long courseId, UserContext userContext) {
        CourseEntity course = requireCourse(courseId);
        if (canManageCourse(course, userContext)) {
            return course;
        }
        if (!"STUDENT".equals(userContext.role())) {
            throw BusinessException.forbidden("Only students can participate in course discussions");
        }
        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw BusinessException.forbidden("Course content is not available");
        }
        if (!enrollmentRepository.existsByStudentIdAndCourseId(userContext.userId(), courseId)) {
            throw BusinessException.forbidden("Please enroll in this course before participating in discussions");
        }
        return course;
    }

    private CourseEntity requireCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> BusinessException.notFound("Course not found"));
    }

    private boolean canManageCourse(CourseEntity course, UserContext userContext) {
        return "ADMIN".equals(userContext.role())
                || ("TEACHER".equals(userContext.role()) && Objects.equals(course.getTeacherId(), userContext.userId()));
    }

    private boolean canStudentViewCourseSummary(CourseEntity course, Long studentId) {
        if (course.getStatus() == CourseStatus.ARCHIVED) {
            return false;
        }
        if (course.getVisibilityType() == CourseVisibilityType.PUBLIC) {
            return course.getStatus() == CourseStatus.PUBLISHED;
        }
        return course.getStatus() == CourseStatus.PUBLISHED
                && courseVisibleStudentRepository.existsByCourseIdAndStudentId(course.getId(), studentId);
    }

    private String normalizeTitle(String title) {
        return StringUtils.hasText(title) ? title.trim() : null;
    }

    private Map<Long, String> resolveUserNames(Collection<Long> userIds) {
        Map<Long, String> result = new HashMap<>();
        for (Long userId : userIds) {
            if (userId == null || result.containsKey(userId)) {
                continue;
            }
            result.put(userId, resolveUserName(userId));
        }
        return result;
    }

    private String resolveUserName(Long userId) {
        try {
            UserServiceResponse response = userServiceClient.getUserById(userId);
            if (response != null && response.getData() != null && StringUtils.hasText(response.getData().getUsername())) {
                return response.getData().getUsername();
            }
        } catch (FeignException ignored) {
            // degrade gracefully
        } catch (Exception ignored) {
            // degrade gracefully
        }
        return "User-" + userId;
    }

    private void publishAnnouncementPublishedEvent(CourseEntity course, CourseAnnouncementEntity announcement) {
        outboxService.enqueue(EventTopics.ANNOUNCEMENT_PUBLISHED, AnnouncementPublishedEvent.builder()
                .announcementId(announcement.getId())
                .courseId(course.getId())
                .authorId(announcement.getAuthorId())
                .courseTitle(course.getTitle())
                .title(announcement.getTitle())
                .pinned(Boolean.TRUE.equals(announcement.getPinned()))
                .publishedAt(announcement.getPublishedAt() == null ? null : announcement.getPublishedAt().toString())
                .build());
    }

    private void publishAnnouncementNotifications(CourseEntity course, CourseAnnouncementEntity announcement) {
        List<Long> recipientIds = enrollmentRepository.findAll().stream()
                .filter(item -> Objects.equals(item.getCourseId(), course.getId()))
                .map(item -> item.getStudentId())
                .distinct()
                .toList();
        for (Long recipientId : recipientIds) {
            outboxService.enqueue(EventTopics.NOTIFICATION_SEND, NotificationSendEvent.builder()
                    .userId(recipientId)
                    .type("COURSE")
                    .title("课程新公告")
                    .content("课程《" + course.getTitle() + "》发布了新公告：" + announcement.getTitle())
                    .build());
        }
    }

    private void notifyAnnouncementPublished(CourseEntity course, CourseAnnouncementEntity announcement) {
        List<Long> recipientIds = enrollmentRepository.findAll().stream()
                .filter(item -> Objects.equals(item.getCourseId(), course.getId()))
                .map(item -> item.getStudentId())
                .distinct()
                .toList();
        for (Long recipientId : recipientIds) {
            try {
                notifyServiceClient.createNotification(CreateNotificationRequest.builder()
                        .userId(recipientId)
                        .type("COURSE")
                        .title("课程新公告")
                        .content("课程《" + course.getTitle() + "》发布了新公告：" + announcement.getTitle())
                        .build());
            } catch (FeignException ignored) {
                // keep course announcement available even if notification service is unavailable
            } catch (Exception ignored) {
                // ignore transient notification issues
            }
        }
    }
}
