package com.cloudteachingai.course.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "notify-service", url = "${notify-service.url:http://localhost:8005}")
public interface NotifyServiceClient {

    @PostMapping("/api/v1/internal/notifications")
    void createNotification(@RequestBody CreateNotificationRequest request);
}
