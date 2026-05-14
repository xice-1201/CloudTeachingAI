package com.cloudteachingai.user.client;

import com.cloudteachingai.user.dto.CreateNotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "notify-service", url = "${notify-service.url:http://localhost:8006}")
public interface NotifyServiceClient {

    @PostMapping("/api/v1/internal/notifications")
    void createNotification(@RequestBody CreateNotificationRequest request);

    @DeleteMapping("/api/v1/internal/users/{userId}/notifications")
    void deleteNotificationsForUser(@PathVariable Long userId);
}
