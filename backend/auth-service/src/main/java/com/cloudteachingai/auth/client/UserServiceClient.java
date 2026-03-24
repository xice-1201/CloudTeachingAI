package com.cloudteachingai.auth.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", url = "${user-service.url:http://localhost:8002}")
public interface UserServiceClient {

    @GetMapping("/api/v1/users/{id}")
    UserRoleResponse getUserById(@PathVariable Long id);
}
