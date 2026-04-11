package com.cloudteachingai.course.service;

import com.cloudteachingai.course.dto.ChapterResponse;
import com.cloudteachingai.course.dto.CourseResponse;
import com.cloudteachingai.course.dto.CourseUpsertRequest;
import com.cloudteachingai.course.dto.PageResponse;
import com.cloudteachingai.course.dto.ResourceResponse;
import com.cloudteachingai.course.dto.ChapterUpsertRequest;
import com.cloudteachingai.course.dto.ResourceUpsertRequest;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class CourseFacadeService {

    private final AtomicLong courseIdSequence = new AtomicLong(1000);
    private final AtomicLong chapterIdSequence = new AtomicLong(2000);
    private final AtomicLong resourceIdSequence = new AtomicLong(3000);

    public PageResponse<CourseResponse> listCourses(int page, int pageSize) {
        return emptyPage(page, pageSize);
    }

    public PageResponse<CourseResponse> listEnrolledCourses(int page, int pageSize) {
        return emptyPage(page, pageSize);
    }

    public CourseResponse getCourse(Long id, Long userId) {
        return buildCourse(id, userId, "DRAFT");
    }

    public CourseResponse createCourse(CourseUpsertRequest request, Long userId) {
        return CourseResponse.builder()
                .id(courseIdSequence.incrementAndGet())
                .title(request.getTitle())
                .description(request.getDescription())
                .coverImage(request.getCoverImage())
                .teacherId(userId)
                .teacherName(defaultTeacherName(userId))
                .status("DRAFT")
                .createdAt(now())
                .updatedAt(now())
                .build();
    }

    public CourseResponse updateCourse(Long id, CourseUpsertRequest request, Long userId) {
        return CourseResponse.builder()
                .id(id)
                .title(request.getTitle())
                .description(request.getDescription())
                .coverImage(request.getCoverImage())
                .teacherId(userId)
                .teacherName(defaultTeacherName(userId))
                .status("DRAFT")
                .createdAt(now())
                .updatedAt(now())
                .build();
    }

    public CourseResponse publishCourse(Long id, Long userId) {
        return buildCourse(id, userId, "PUBLISHED");
    }

    public ChapterResponse createChapter(Long courseId, ChapterUpsertRequest request) {
        return ChapterResponse.builder()
                .id(chapterIdSequence.incrementAndGet())
                .courseId(courseId)
                .title(request.getTitle())
                .description(request.getDescription())
                .orderIndex(request.getOrderIndex() == null ? 1 : request.getOrderIndex())
                .createdAt(now())
                .build();
    }

    public ChapterResponse updateChapter(Long courseId, Long chapterId, ChapterUpsertRequest request) {
        return ChapterResponse.builder()
                .id(chapterId)
                .courseId(courseId)
                .title(request.getTitle())
                .description(request.getDescription())
                .orderIndex(request.getOrderIndex() == null ? 1 : request.getOrderIndex())
                .createdAt(now())
                .build();
    }

    public ResourceResponse createResource(Long chapterId, ResourceUpsertRequest request) {
        return ResourceResponse.builder()
                .id(resourceIdSequence.incrementAndGet())
                .chapterId(chapterId)
                .title(request.getTitle())
                .type(request.getType())
                .url(request.getUrl())
                .duration(request.getDuration())
                .size(request.getSize())
                .orderIndex(request.getOrderIndex() == null ? 1 : request.getOrderIndex())
                .createdAt(now())
                .build();
    }

    public ResourceResponse updateResource(Long resourceId, ResourceUpsertRequest request) {
        return ResourceResponse.builder()
                .id(resourceId)
                .chapterId(0L)
                .title(request.getTitle())
                .type(request.getType())
                .url(request.getUrl())
                .duration(request.getDuration())
                .size(request.getSize())
                .orderIndex(request.getOrderIndex() == null ? 1 : request.getOrderIndex())
                .createdAt(now())
                .build();
    }

    public java.util.List<ChapterResponse> listChapters(Long courseId) {
        return Collections.emptyList();
    }

    public java.util.List<ResourceResponse> listResources(Long chapterId) {
        return Collections.emptyList();
    }

    private PageResponse<CourseResponse> emptyPage(int page, int pageSize) {
        return PageResponse.<CourseResponse>builder()
                .items(Collections.emptyList())
                .total(0)
                .page(page)
                .pageSize(pageSize)
                .build();
    }

    private CourseResponse buildCourse(Long id, Long userId, String status) {
        return CourseResponse.builder()
                .id(id)
                .title("")
                .description("")
                .coverImage(null)
                .teacherId(userId)
                .teacherName(defaultTeacherName(userId))
                .status(status)
                .createdAt(now())
                .updatedAt(now())
                .build();
    }

    private String defaultTeacherName(Long userId) {
        return userId == null ? "" : "User-" + userId;
    }

    private String now() {
        return OffsetDateTime.now(ZoneOffset.UTC).toString();
    }
}
