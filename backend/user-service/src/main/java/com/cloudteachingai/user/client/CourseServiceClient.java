package com.cloudteachingai.user.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "course-service", url = "${course-service.url:http://localhost:8003}")
public interface CourseServiceClient {

    @DeleteMapping("/api/v1/internal/users/{userId}/course-data")
    void deleteUserCourseData(
            @PathVariable Long userId,
            @RequestParam String role
    );
}
