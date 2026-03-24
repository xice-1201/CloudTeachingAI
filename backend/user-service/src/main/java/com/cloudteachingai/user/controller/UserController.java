package com.cloudteachingai.user.controller;

import com.cloudteachingai.user.dto.ApiResponse;
import com.cloudteachingai.user.dto.CreateUserRequest;
import com.cloudteachingai.user.dto.PageResponse;
import com.cloudteachingai.user.dto.UpdateProfileRequest;
import com.cloudteachingai.user.dto.UserResponse;
import com.cloudteachingai.user.entity.User;
import com.cloudteachingai.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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

    @GetMapping("/users/me")
    public ApiResponse<UserResponse> getProfile(
            @RequestHeader(value = "X-User-Id", required = false) Long userIdHeader,
            @RequestParam(value = "userId", required = false) Long userIdParam) {
        Long userId = userIdHeader != null ? userIdHeader : userIdParam;
        if (userId == null) {
            return ApiResponse.error(40101, "未提供用户身份");
        }
        UserResponse response = userService.getProfile(userId);
        return ApiResponse.success(response);
    }

    @PutMapping("/users/me")
    public ApiResponse<UserResponse> updateProfile(
            @RequestHeader(value = "X-User-Id", required = false) Long userIdHeader,
            @RequestParam(value = "userId", required = false) Long userIdParam,
            @RequestBody UpdateProfileRequest request) {
        Long userId = userIdHeader != null ? userIdHeader : userIdParam;
        if (userId == null) {
            return ApiResponse.error(40101, "未提供用户身份");
        }
        UserResponse response = userService.updateProfile(userId, request);
        return ApiResponse.success(response);
    }

    @PostMapping("/users/me/avatar")
    public ApiResponse<Map<String, String>> uploadAvatar(
            @RequestHeader(value = "X-User-Id", required = false) Long userIdHeader,
            @RequestParam(value = "userId", required = false) Long userIdParam) {
        Long userId = userIdHeader != null ? userIdHeader : userIdParam;
        if (userId == null) {
            return ApiResponse.error(40101, "未提供用户身份");
        }
        // Placeholder URL until MinIO is set up
        String placeholderUrl = "https://placeholder.example.com/avatars/" + userId + ".png";
        return ApiResponse.success(Map.of("url", placeholderUrl));
    }

    @GetMapping("/users/students")
    public ApiResponse<PageResponse<UserResponse>> listStudents(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageResponse<UserResponse> response = userService.listByRole(User.UserRole.STUDENT, keyword, page, pageSize);
        return ApiResponse.success(response);
    }

    @GetMapping("/users/teachers")
    public ApiResponse<PageResponse<UserResponse>> listTeachers(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageResponse<UserResponse> response = userService.listByRole(User.UserRole.TEACHER, keyword, page, pageSize);
        return ApiResponse.success(response);
    }

    @GetMapping("/users/mentor-relations")
    public ApiResponse<Map<String, Object>> getMentorRelations(
            @RequestHeader(value = "X-User-Id", required = false) Long userIdHeader,
            @RequestParam(value = "userId", required = false) Long userIdParam) {
        // Placeholder until full mentor-relation feature is implemented
        return ApiResponse.success(Map.of("mentor", Map.of(), "students", java.util.List.of()));
    }

    @PostMapping("/users/mentor-relations")
    public ApiResponse<Map<String, String>> applyForMentor(
            @RequestHeader(value = "X-User-Id", required = false) Long userIdHeader,
            @RequestParam(value = "userId", required = false) Long userIdParam,
            @RequestBody(required = false) Map<String, Object> body) {
        // Placeholder until full mentor-relation feature is implemented
        return ApiResponse.success(Map.of("message", "申请已提交"));
    }
}
