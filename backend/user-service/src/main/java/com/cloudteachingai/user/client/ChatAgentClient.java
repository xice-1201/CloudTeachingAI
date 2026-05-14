package com.cloudteachingai.user.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "chat-agent", url = "${chat-agent.url:http://localhost:8104}")
public interface ChatAgentClient {

    @DeleteMapping("/api/v1/internal/users/{userId}/chat-data")
    void deleteUserChatData(@PathVariable Long userId);
}
