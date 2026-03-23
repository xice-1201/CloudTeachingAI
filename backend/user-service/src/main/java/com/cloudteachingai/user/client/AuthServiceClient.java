package com.cloudteachingai.user.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "auth-service", url = "${auth-service.url:http://localhost:8001}")
public interface AuthServiceClient {

    @PostMapping("/api/v1/auth/internal/create-credential")
    void createCredential(
            @RequestParam Long userId,
            @RequestParam String email,
            @RequestParam String password
    );
}
