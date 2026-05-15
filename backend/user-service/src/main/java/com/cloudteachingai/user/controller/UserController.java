package com.cloudteachingai.user.controller;

import com.cloudteachingai.user.dto.AdminAuditLogResponse;
import com.cloudteachingai.user.dto.ApiResponse;
import com.cloudteachingai.user.dto.CreateAdminAuditLogRequest;
import com.cloudteachingai.user.dto.CreateTeacherRegistrationApplicationRequest;
import com.cloudteachingai.user.dto.CreateUserRequest;
import com.cloudteachingai.user.dto.MentorRelationResponse;
import com.cloudteachingai.user.dto.PageResponse;
import com.cloudteachingai.user.dto.ReviewTeacherRegistrationApplicationRequest;
import com.cloudteachingai.user.dto.ServiceHealthResponse;
import com.cloudteachingai.user.dto.TeacherRegistrationApplicationResponse;
import com.cloudteachingai.user.dto.UpdateProfileRequest;
import com.cloudteachingai.user.dto.UserResponse;
import com.cloudteachingai.user.entity.User;
import com.cloudteachingai.user.service.AvatarStorageService;
import com.cloudteachingai.user.service.SystemHealthService;
import com.cloudteachingai.user.service.AdminAuditLogService;
import com.cloudteachingai.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final SystemHealthService systemHealthService;
    private final AdminAuditLogService adminAuditLogService;
    private final AvatarStorageService avatarStorageService;

    @PostMapping("/admin/users")
    public ApiResponse<UserResponse> createUser(
            @RequestHeader(value = "X-User-Id", required = false) Long actorId,
            @Valid @RequestBody CreateUserRequest request) {
        log.info("Create user request: email={}, role={}", request.getEmail(), request.getRole());
        return ApiResponse.success(userService.createUser(request, actorId));
    }

    @GetMapping("/admin/users")
    public ApiResponse<PageResponse<UserResponse>> listUsers(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(userService.listUsers(keyword, role, active, page, pageSize));
    }

    @PostMapping("/admin/users/{id}/activate")
    public ApiResponse<UserResponse> activateUser(
            @RequestHeader(value = "X-User-Id", required = false) Long actorId,
            @PathVariable Long id) {
        return ApiResponse.success(userService.updateUserActive(id, true, actorId));
    }

    @PostMapping("/admin/users/{id}/deactivate")
    public ApiResponse<UserResponse> deactivateUser(
            @RequestHeader(value = "X-User-Id", required = false) Long actorId,
            @PathVariable Long id) {
        return ApiResponse.success(userService.updateUserActive(id, false, actorId));
    }

    @DeleteMapping("/admin/users/{id}")
    public ApiResponse<Void> deleteUserCompletely(
            @RequestHeader(value = "X-User-Id", required = false) Long actorId,
            @PathVariable Long id) {
        userService.deleteUserCompletely(id, actorId);
        return ApiResponse.success(null);
    }

    @GetMapping("/admin/audit-logs")
    public ApiResponse<PageResponse<AdminAuditLogResponse>> listAuditLogs(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(adminAuditLogService.listLogs(keyword, action, targetType, page, pageSize));
    }

    @PostMapping("/internal/admin-audit-logs")
    public ApiResponse<AdminAuditLogResponse> createInternalAuditLog(
            @Valid @RequestBody CreateAdminAuditLogRequest request) {
        return ApiResponse.success(adminAuditLogService.record(
                request.getActorId(),
                request.getAction(),
                request.getTargetType(),
                request.getTargetId(),
                request.getTargetName(),
                request.getDetail()
        ));
    }

    @GetMapping("/admin/teacher-registration-applications")
    public ApiResponse<List<TeacherRegistrationApplicationResponse>> listPendingTeacherRegistrationApplications() {
        return ApiResponse.success(userService.listPendingTeacherRegistrationApplications());
    }

    @GetMapping("/admin/system-health")
    public ApiResponse<List<ServiceHealthResponse>> listSystemHealth() {
        return ApiResponse.success(systemHealthService.listServiceHealth());
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

    @PostMapping(value = "/users/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, String>> uploadAvatar(
            @RequestHeader(value = "X-User-Id", required = false) Long userIdHeader,
            @RequestParam(value = "userId", required = false) Long userIdParam,
            @RequestPart("file") MultipartFile file) {
        Long userId = userIdHeader != null ? userIdHeader : userIdParam;
        if (userId == null) {
            return ApiResponse.error(40101, "未提供用户身份");
        }
        String avatarUrl = avatarStorageService.store(file, userId);
        userService.updateAvatar(userId, avatarUrl);
        return ApiResponse.success(Map.of("url", avatarUrl));
    }

    @GetMapping("/users/avatars/{filename:.+}")
    public ResponseEntity<Resource> getAvatar(@PathVariable String filename) {
        return ResponseEntity.ok()
                .contentType(avatarStorageService.resolveMediaType(filename))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .body(avatarStorageService.loadAsResource(filename));
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
        return ApiResponse.success(userService.getMentorRelations(resolveUserId(userIdHeader, userIdParam)));
    }

    @PostMapping("/users/mentor-relations")
    public ApiResponse<MentorRelationResponse> applyForMentor(
            @RequestHeader(value = "X-User-Id", required = false) Long userIdHeader,
            @RequestParam(value = "userId", required = false) Long userIdParam,
            @RequestBody(required = false) Map<String, Object> body) {
        Long mentorId = parseLong(body == null ? null : body.get("mentorId"));
        return ApiResponse.success(userService.applyForMentor(resolveUserId(userIdHeader, userIdParam), mentorId));
    }

    @PostMapping("/users/mentor-relations/{id}/approve")
    public ApiResponse<MentorRelationResponse> approveMentorRelation(
            @RequestHeader(value = "X-User-Id", required = false) Long userIdHeader,
            @RequestParam(value = "userId", required = false) Long userIdParam,
            @PathVariable Long id) {
        return ApiResponse.success(userService.approveMentorRelation(resolveUserId(userIdHeader, userIdParam), id));
    }

    @PostMapping("/users/mentor-relations/{id}/reject")
    public ApiResponse<MentorRelationResponse> rejectMentorRelation(
            @RequestHeader(value = "X-User-Id", required = false) Long userIdHeader,
            @RequestParam(value = "userId", required = false) Long userIdParam,
            @PathVariable Long id) {
        return ApiResponse.success(userService.rejectMentorRelation(resolveUserId(userIdHeader, userIdParam), id));
    }

    private Long resolveUserId(Long userIdHeader, Long userIdParam) {
        return userIdHeader != null ? userIdHeader : userIdParam;
    }

    private Long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        return null;
    }
}
