package com.cloudteachingai.user.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "assign-service", url = "${assign-service.url:http://localhost:8005}")
public interface AssignServiceClient {

    @DeleteMapping("/api/v1/internal/users/{userId}/assignment-data")
    void deleteUserAssignmentData(
            @PathVariable Long userId,
            @RequestParam String role
    );
}
