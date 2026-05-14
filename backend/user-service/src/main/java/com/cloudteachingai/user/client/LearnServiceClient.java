package com.cloudteachingai.user.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "learn-service", url = "${learn-service.url:http://localhost:8004}")
public interface LearnServiceClient {

    @DeleteMapping("/api/v1/learn/internal/users/{userId}/learning-data")
    void deleteUserLearningData(@PathVariable Long userId);
}
