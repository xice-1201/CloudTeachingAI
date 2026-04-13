package com.cloudteachingai.learn.service;

import com.cloudteachingai.learn.client.CourseApiResponse;
import com.cloudteachingai.learn.client.CourseChapterResponse;
import com.cloudteachingai.learn.client.CourseResourceResponse;
import com.cloudteachingai.learn.client.CourseServiceClient;
import com.cloudteachingai.learn.controller.LearnController.UserContext;
import com.cloudteachingai.learn.dto.CourseProgressResponse;
import com.cloudteachingai.learn.dto.LearningProgressResponse;
import com.cloudteachingai.learn.dto.UpdateLearningProgressRequest;
import com.cloudteachingai.learn.entity.LearningProgressEntity;
import com.cloudteachingai.learn.exception.BusinessException;
import com.cloudteachingai.learn.repository.LearningProgressRepository;
import feign.FeignException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LearnFacadeService {

    private final LearningProgressRepository learningProgressRepository;
    private final CourseServiceClient courseServiceClient;

    public LearningProgressResponse getResourceProgress(Long resourceId, UserContext userContext) {
        assertStudent(userContext);
        return learningProgressRepository.findByStudentIdAndResourceId(userContext.userId(), resourceId)
                .map(this::toLearningProgressResponse)
                .orElseGet(() -> LearningProgressResponse.builder()
                        .resourceId(resourceId)
                        .progress(0D)
                        .lastPosition(null)
                        .completed(false)
                        .lastAccessedAt(OffsetDateTime.now(ZoneOffset.UTC).toString())
                        .build());
    }

    @Transactional
    public LearningProgressResponse updateResourceProgress(
            Long resourceId,
            UpdateLearningProgressRequest request,
            String authorization,
            UserContext userContext) {
        assertStudent(userContext);
        resolveTotalResources(request.getCourseId(), authorization);

        double sanitizedProgress = clampProgress(request.getProgress());
        int sanitizedPosition = sanitizeLastPosition(request.getLastPosition());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        LearningProgressEntity progress = learningProgressRepository.findByStudentIdAndResourceId(userContext.userId(), resourceId)
                .orElseGet(() -> LearningProgressEntity.builder()
                        .studentId(userContext.userId())
                        .courseId(request.getCourseId())
                        .resourceId(resourceId)
                        .progress(0D)
                        .completed(false)
                        .build());

        progress.setCourseId(progress.getCourseId() == null ? request.getCourseId() : progress.getCourseId());
        progress.setProgress(Math.max(progress.getProgress(), sanitizedProgress));
        progress.setLastPosition(sanitizedPosition > 0 ? sanitizedPosition : null);
        progress.setLastAccessedAt(now);
        progress.setCompleted(progress.getProgress() >= 0.999D);
        if (Boolean.TRUE.equals(progress.getCompleted()) && progress.getCompletedAt() == null) {
            progress.setCompletedAt(now);
        }

        LearningProgressEntity saved = learningProgressRepository.save(progress);
        return toLearningProgressResponse(saved);
    }

    public CourseProgressResponse getCourseProgress(
            Long courseId,
            String authorization,
            UserContext userContext) {
        assertStudent(userContext);

        List<LearningProgressEntity> progresses = learningProgressRepository.findByStudentIdAndCourseId(userContext.userId(), courseId);
        int trackedResources = progresses.size();
        int totalResources = Math.max(resolveTotalResources(courseId, authorization), trackedResources);
        int completedResources = (int) progresses.stream().filter(progress -> Boolean.TRUE.equals(progress.getCompleted())).count();
        double totalProgress = progresses.stream().mapToDouble(progress -> progress.getProgress() == null ? 0D : progress.getProgress()).sum();
        double courseProgress = totalResources == 0 ? 0D : Math.min(1D, totalProgress / totalResources);
        OffsetDateTime lastLearnedAt = progresses.stream()
                .map(LearningProgressEntity::getLastAccessedAt)
                .filter(value -> value != null)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return CourseProgressResponse.builder()
                .courseId(courseId)
                .progress(courseProgress)
                .totalResources(totalResources)
                .completedResources(completedResources)
                .lastLearnedAt(lastLearnedAt == null ? null : lastLearnedAt.toString())
                .build();
    }

    public List<Object> getAbilityMap(UserContext userContext) {
        assertStudent(userContext);
        return Collections.emptyList();
    }

    public Object getLearningPath(UserContext userContext) {
        assertStudent(userContext);
        return null;
    }

    public Object generateLearningPath(UserContext userContext) {
        assertStudent(userContext);
        return null;
    }

    private int resolveTotalResources(Long courseId, String authorization) {
        try {
            CourseApiResponse<List<CourseChapterResponse>> chapterResponse = courseServiceClient.listChapters(authorization, courseId);
            List<CourseChapterResponse> chapters = chapterResponse == null || chapterResponse.getData() == null
                    ? Collections.emptyList()
                    : chapterResponse.getData();

            int total = 0;
            for (CourseChapterResponse chapter : chapters) {
                CourseApiResponse<List<CourseResourceResponse>> resourceResponse = courseServiceClient.listResources(authorization, chapter.getId());
                List<CourseResourceResponse> resources = resourceResponse == null || resourceResponse.getData() == null
                        ? Collections.emptyList()
                        : resourceResponse.getData();
                total += resources.size();
            }
            return total;
        } catch (FeignException.NotFound ex) {
            throw BusinessException.notFound("Course not found");
        } catch (FeignException.Forbidden ex) {
            throw BusinessException.forbidden("No access to this course");
        } catch (FeignException.Unauthorized ex) {
            throw BusinessException.unauthorized("Invalid token");
        } catch (FeignException ex) {
            return 0;
        }
    }

    private void assertStudent(UserContext userContext) {
        if (!"STUDENT".equals(userContext.role())) {
            throw BusinessException.forbidden("Only students can access learning progress");
        }
    }

    private double clampProgress(Double progress) {
        if (progress == null) {
            return 0D;
        }
        return Math.min(1D, Math.max(0D, progress));
    }

    private int sanitizeLastPosition(Integer lastPosition) {
        if (lastPosition == null) {
            return 0;
        }
        return Math.max(0, lastPosition);
    }

    private LearningProgressResponse toLearningProgressResponse(LearningProgressEntity entity) {
        return LearningProgressResponse.builder()
                .resourceId(entity.getResourceId())
                .progress(entity.getProgress())
                .lastPosition(entity.getLastPosition())
                .completed(entity.getCompleted())
                .lastAccessedAt(entity.getLastAccessedAt() == null ? null : entity.getLastAccessedAt().toString())
                .build();
    }
}
