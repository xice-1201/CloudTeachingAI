package com.cloudteachingai.learn.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "course-service-client", url = "${course-service.url}")
public interface CourseServiceClient {

    @GetMapping("/api/v1/courses/{courseId}/chapters")
    CourseApiResponse<List<CourseChapterResponse>> listChapters(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("courseId") Long courseId);

    @GetMapping("/api/v1/chapters/{chapterId}/resources")
    CourseApiResponse<List<CourseResourceResponse>> listResources(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("chapterId") Long chapterId);

    @GetMapping("/api/v1/knowledge-points/tree")
    CourseApiResponse<List<CourseKnowledgePointNodeResponse>> listKnowledgePointTree(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("activeOnly") boolean activeOnly);

    @GetMapping("/api/v1/courses/{courseId}")
    CourseApiResponse<CourseSummaryResponse> getCourse(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("courseId") Long courseId);

    @GetMapping("/api/v1/courses")
    CourseApiResponse<PageResponse<CourseSummaryResponse>> listCourses(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("page") int page,
            @RequestParam("pageSize") int pageSize);

    @GetMapping("/api/v1/courses/enrolled")
    CourseApiResponse<PageResponse<CourseSummaryResponse>> listEnrolledCourses(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("page") int page,
            @RequestParam("pageSize") int pageSize);
}
