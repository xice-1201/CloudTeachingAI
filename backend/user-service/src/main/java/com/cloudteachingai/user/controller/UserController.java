package com.cloudteachingai.user.controller;

import com.cloudteachingai.user.dto.ApiResponse;
import com.cloudteachingai.user.dto.CreateTeacherRegistrationApplicationRequest;
import com.cloudteachingai.user.dto.CreateUserRequest;
import com.cloudteachingai.user.dto.PageResponse;
import com.cloudteachingai.user.dto.ReviewTeacherRegistrationApplicationRequest;
import com.cloudteachingai.user.dto.TeacherRegistrationApplicationResponse;
import com.cloudteachingai.user.dto.UpdateProfileRequest;
import com.cloudteachingai.user.dto.UserResponse;
import com.cloudteachingai.user.entity.User;
import com.cloudteachingai.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
        return ApiResponse.success(userService.createUser(request));
    }

    @GetMapping("/admin/teacher-registration-applications")
    public ApiResponse<List<TeacherRegistrationApplicationResponse>> listPendingTeacherRegistrationApplications() {
        return ApiResponse.success(userService.listPendingTeacherRegistrationApplications());
    }

    @PostMapping("/admin/teacher-registration-applications/{id}/approve")
    public ApiResponse<TeacherRegistrationApplicationResponse> approveTeacherRegistrationApplication(
            @PathVariable Long id,
            @Valid @RequestBody ReviewTeacherRegistrationApplicationRequest request) {
        return ApiResponse.success(userService.approveTeacherRegistrationApplication(id, request));
    }

    @PostMapping("/admin/teacher-registration-applications/{id}/reject")
    public ApiResponse<TeacherRegistrationApplicationResponse> rejectTeacherRegistrationApplication(
            @PathVariable Long id,
            @Valid @RequestBody ReviewTeacherRegistrationApplicationRequest request) {
        return ApiResponse.success(userService.rejectTeacherRegistrationApplication(id, request));
    }

    @PostMapping("/internal/users/register")
    public ApiResponse<UserResponse> registerUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("Register user request: email={}", request.getEmail());
        if (request.getRole() == null) {
            request.setRole(User.UserRole.STUDENT);
        }
        return ApiResponse.success(userService.createUserProfileOnly(request));
    }

    @GetMapping("/internal/users/by-email")
    public ApiResponse<UserResponse> getUserByEmail(@RequestParam String email) {
        return ApiResponse.success(userService.getUserByEmail(email));
    }

    @PostMapping("/internal/teacher-registration-applications")
    public ApiResponse<TeacherRegistrationApplicationResponse> submitTeacherRegistrationApplication(
            @Valid @RequestBody CreateTeacherRegistrationApplicationRequest request) {
        return ApiResponse.success(userService.submitTeacherRegistrationApplication(request));
    }

    @GetMapping("/internal/teacher-registration-applications/by-email")
    public ApiResponse<TeacherRegistrationApplicationResponse> getPendingTeacherRegistrationApplicationByEmail(
            @RequestParam String email) {
        return ApiResponse.success(userService.getPendingTeacherRegistrationApplicationByEmail(email));
    }

    @GetMapping("/users/{id}")
    public ApiResponse<UserResponse> getUserById(@PathVariable Long id) {
        return ApiResponse.success(userService.getUserById(id));
    }

    @GetMapping("/users/me")
    public ApiResponse<UserResponse> getProfile(
            @RequestHeader(value = "X-User-Id", required = false) Long userIdHeader,
            @RequestParam(value = "userId", required = false) Long userIdParam) {
        Long userId = userIdHeader != null ? userIdHeader : userIdParam;
        if (userId == null) {
            return ApiResponse.error(40101, "未提供用户身份");
        }
        return ApiResponse.success(userService.getProfile(userId));
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
        return ApiResponse.success(userService.updateProfile(userId, request));
    }

    @PostMapping("/users/me/avatar")
    public ApiResponse<Map<String, String>> uploadAvatar(
            @RequestHeader(value = "X-User-Id", required = false) Long userIdHeader,
            @RequestParam(value = "userId", required = false) Long userIdParam) {
        Long userId = userIdHeader != null ? userIdHeader : userIdParam;
        if (userId == null) {
            return ApiResponse.error(40101, "未提供用户身份");
        }
        String placeholderUrl = "https://placeholder.example.com/avatars/" + userId + ".png";
        return ApiResponse.success(Map.of("url", placeholderUrl));
    }

    @GetMapping("/users/students")
    public ApiResponse<PageResponse<UserResponse>> listStudents(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(userService.listByRole(User.UserRole.STUDENT, keyword, page, pageSize));
    }

    @GetMapping("/users/teachers")
    public ApiResponse<PageResponse<UserResponse>> listTeachers(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(userService.listByRole(User.UserRole.TEACHER, keyword, page, pageSize));
    }

    @GetMapping("/users/mentor-relations")
    public ApiResponse<Map<String, Object>> getMentorRelations(
            @RequestHeader(value = "X-User-Id", required = false) Long userIdHeader,
            @RequestParam(value = "userId", required = false) Long userIdParam) {
        return ApiResponse.success(Map.of("mentor", Map.of(), "students", List.of()));
    }

    @PostMapping("/users/mentor-relations")
    public ApiResponse<Map<String, String>> applyForMentor(
            @RequestHeader(value = "X-User-Id", required = false) Long userIdHeader,
            @RequestParam(value = "userId", required = false) Long userIdParam,
            @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.success(Map.of("message", "申请已提交"));
    }
}
