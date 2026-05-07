package com.cloudteachingai.assign.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "assign-notify-service-client", url = "${notify-service.url:http://localhost:8006}")
public interface NotifyServiceClient {

    @PostMapping("/api/v1/internal/notifications")
    void createNotification(@RequestBody CreateNotificationRequest request);
}
