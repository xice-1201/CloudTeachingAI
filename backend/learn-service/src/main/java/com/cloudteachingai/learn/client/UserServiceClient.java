package com.cloudteachingai.learn.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "learn-user-service-client", url = "${user-service.url:http://localhost:8002}")
public interface UserServiceClient {

    @GetMapping("/api/v1/internal/mentor-relations/check")
    UserApiResponse<Boolean> checkApprovedMentorRelation(
            @RequestParam("mentorId") Long mentorId,
            @RequestParam("studentId") Long studentId);
}
