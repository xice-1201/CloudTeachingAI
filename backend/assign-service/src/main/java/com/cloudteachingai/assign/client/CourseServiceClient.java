package com.cloudteachingai.assign.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "course-service-client", url = "${course-service.url}")
public interface CourseServiceClient {

    @GetMapping("/api/v1/courses/{id}")
    CourseApiResponse<CourseSummaryResponse> getCourse(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("id") Long courseId);

    @GetMapping("/api/v1/courses/enrolled")
    CourseApiResponse<CoursePageResponse<CourseSummaryResponse>> listEnrolledCourses(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("page") int page,
            @RequestParam("pageSize") int pageSize);

    @GetMapping("/api/v1/internal/courses/{id}/student-ids")
    CourseApiResponse<List<Long>> listCourseStudentIds(@PathVariable("id") Long courseId);
}
