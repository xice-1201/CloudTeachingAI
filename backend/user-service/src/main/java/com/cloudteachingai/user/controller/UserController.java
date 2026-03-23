package com.cloudteachingai.user.controller;

import com.cloudteachingai.user.dto.ApiResponse;
import com.cloudteachingai.user.dto.CreateUserRequest;
import com.cloudteachingai.user.dto.UserResponse;
import com.cloudteachingai.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/admin/users")
    public ApiResponse<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("Create user request: email={}, role={}", request.getEmail(), request.getRole());
        UserResponse response = userService.createUser(request);
        return ApiResponse.success(response);
    }

    @GetMapping("/users/{id}")
    public ApiResponse<UserResponse> getUserById(@PathVariable Long id) {
        UserResponse response = userService.getUserById(id);
        return ApiResponse.success(response);
    }

    @GetMapping("/internal/users/by-email")
    public ApiResponse<UserResponse> getUserByEmail(@RequestParam String email) {
        UserResponse response = userService.getUserByEmail(email);
        return ApiResponse.success(response);
    }
}
