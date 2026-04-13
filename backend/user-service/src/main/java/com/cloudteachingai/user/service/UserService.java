package com.cloudteachingai.user.service;

import com.cloudteachingai.user.client.AuthServiceClient;
import com.cloudteachingai.user.client.NotifyServiceClient;
import com.cloudteachingai.user.dto.CreateNotificationRequest;
import com.cloudteachingai.user.dto.CreateTeacherRegistrationApplicationRequest;
import com.cloudteachingai.user.dto.CreateUserRequest;
import com.cloudteachingai.user.dto.PageResponse;
import com.cloudteachingai.user.dto.ReviewTeacherRegistrationApplicationRequest;
import com.cloudteachingai.user.dto.TeacherRegistrationApplicationResponse;
import com.cloudteachingai.user.dto.UpdateProfileRequest;
import com.cloudteachingai.user.dto.UserResponse;
import com.cloudteachingai.user.entity.TeacherRegistrationApplication;
import com.cloudteachingai.user.entity.User;
import com.cloudteachingai.user.exception.BusinessException;
import com.cloudteachingai.user.repository.TeacherRegistrationApplicationRepository;
import com.cloudteachingai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TeacherRegistrationApplicationRepository teacherRegistrationApplicationRepository;
    private final AuthServiceClient authServiceClient;
    private final NotifyServiceClient notifyServiceClient;

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw BusinessException.conflict("邮箱已被注册");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .role(request.getRole())
                .isActive(true)
                .build();

        user = userRepository.save(user);

        try {
            authServiceClient.createCredential(user.getId(), request.getEmail(), request.getPassword());
        } catch (Exception e) {
            log.error("Failed to create auth credential for user {}", user.getId(), e);
            throw BusinessException.internalError("创建用户凭证失败");
        }

        log.info("User created successfully: id={}, email={}, role={}", user.getId(), user.getEmail(), user.getRole());
        sendWelcomeNotification(user);

        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse createUserProfileOnly(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw BusinessException.conflict("邮箱已被注册");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .role(request.getRole())
                .isActive(true)
                .build();

        user = userRepository.save(user);

        log.info("User profile created successfully: id={}, email={}, role={}", user.getId(), user.getEmail(), user.getRole());
        sendWelcomeNotification(user);
        return UserResponse.from(user);
    }

    public UserResponse getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        return UserResponse.from(user);
    }

    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        return UserResponse.from(user);
    }

    public UserResponse getProfile(Long userId) {
        return getUserById(userId);
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            user.setUsername(request.getUsername());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }
        user = userRepository.save(user);
        return UserResponse.from(user);
    }

    public PageResponse<UserResponse> listByRole(User.UserRole role, String keyword, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<User> users;
        if (keyword != null && !keyword.isBlank()) {
            users = userRepository.findByRoleAndUsernameContainingIgnoreCase(role, keyword, pageable);
        } else {
            users = userRepository.findByRole(role, pageable);
        }
        List<UserResponse> items = users.getContent().stream().map(UserResponse::from).toList();
        return new PageResponse<>(items, (int) users.getTotalElements(), page, pageSize);
    }

    @Transactional
    public TeacherRegistrationApplicationResponse submitTeacherRegistrationApplication(
            CreateTeacherRegistrationApplicationRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw BusinessException.conflict("邮箱已被注册");
        }

        teacherRegistrationApplicationRepository
                .findByEmailAndStatus(request.getEmail(), TeacherRegistrationApplication.Status.PENDING)
                .ifPresent(existing -> {
                    throw BusinessException.conflict("教师注册申请已提交，请等待管理员审核");
                });

        TeacherRegistrationApplication application = teacherRegistrationApplicationRepository.save(
                TeacherRegistrationApplication.builder()
                        .username(request.getUsername())
                        .email(request.getEmail())
                        .passwordHash(request.getPasswordHash())
                        .status(TeacherRegistrationApplication.Status.PENDING)
                        .build()
        );

        notifyAdminsForTeacherApplication(application);
        log.info("Teacher registration application submitted: id={}, email={}", application.getId(), application.getEmail());
        return TeacherRegistrationApplicationResponse.from(application);
    }

    public List<TeacherRegistrationApplicationResponse> listPendingTeacherRegistrationApplications() {
        return teacherRegistrationApplicationRepository
                .findAllByStatusOrderByRequestedAtDesc(TeacherRegistrationApplication.Status.PENDING)
                .stream()
                .map(TeacherRegistrationApplicationResponse::from)
                .toList();
    }

    public TeacherRegistrationApplicationResponse getPendingTeacherRegistrationApplicationByEmail(String email) {
        TeacherRegistrationApplication application = teacherRegistrationApplicationRepository
                .findByEmailAndStatus(email, TeacherRegistrationApplication.Status.PENDING)
                .orElseThrow(() -> BusinessException.notFound("教师注册申请不存在"));
        return TeacherRegistrationApplicationResponse.from(application);
    }

    @Transactional
    public TeacherRegistrationApplicationResponse approveTeacherRegistrationApplication(
            Long applicationId,
            ReviewTeacherRegistrationApplicationRequest request) {
        TeacherRegistrationApplication application = teacherRegistrationApplicationRepository.findById(applicationId)
                .orElseThrow(() -> BusinessException.notFound("教师注册申请不存在"));

        if (application.getStatus() != TeacherRegistrationApplication.Status.PENDING) {
            throw BusinessException.conflict("教师注册申请已处理");
        }

        if (userRepository.existsByEmail(application.getEmail())) {
            throw BusinessException.conflict("邮箱已被注册");
        }

        User user = User.builder()
                .username(application.getUsername())
                .email(application.getEmail())
                .role(User.UserRole.TEACHER)
                .isActive(true)
                .build();

        user = userRepository.save(user);

        try {
            authServiceClient.createCredentialWithHash(user.getId(), user.getEmail(), application.getPasswordHash());
        } catch (Exception e) {
            log.error("Failed to create teacher credential from application {}", applicationId, e);
            throw BusinessException.internalError("创建教师登录凭证失败");
        }

        application.setStatus(TeacherRegistrationApplication.Status.APPROVED);
        application.setReviewedBy(request.getReviewerId());
        application.setReviewNote(request.getReviewNote());
        application.setReviewedAt(LocalDateTime.now());
        application.setCreatedUserId(user.getId());
        application = teacherRegistrationApplicationRepository.save(application);

        sendWelcomeNotification(user);
        sendTeacherApprovalNotification(user);
        log.info("Teacher registration application approved: applicationId={}, userId={}", applicationId, user.getId());
        return TeacherRegistrationApplicationResponse.from(application);
    }

    @Transactional
    public TeacherRegistrationApplicationResponse rejectTeacherRegistrationApplication(
            Long applicationId,
            ReviewTeacherRegistrationApplicationRequest request) {
        TeacherRegistrationApplication application = teacherRegistrationApplicationRepository.findById(applicationId)
                .orElseThrow(() -> BusinessException.notFound("教师注册申请不存在"));

        if (application.getStatus() != TeacherRegistrationApplication.Status.PENDING) {
            throw BusinessException.conflict("教师注册申请已处理");
        }

        application.setStatus(TeacherRegistrationApplication.Status.REJECTED);
        application.setReviewedBy(request.getReviewerId());
        application.setReviewNote(request.getReviewNote());
        application.setReviewedAt(LocalDateTime.now());
        application = teacherRegistrationApplicationRepository.save(application);

        sendTeacherRejectionNotification(application);
        log.info("Teacher registration application rejected: applicationId={}", applicationId);
        return TeacherRegistrationApplicationResponse.from(application);
    }

    private void sendWelcomeNotification(User user) {
        try {
            notifyServiceClient.createNotification(new CreateNotificationRequest(
                    user.getId(),
                    "SYSTEM",
                    "欢迎来到 CloudTeachingAI",
                    "你的账号已经准备就绪，快去探索课程与学习任务吧。"
            ));
        } catch (Exception e) {
            log.warn("Failed to create welcome notification for user {}", user.getId(), e);
        }
    }

    private void notifyAdminsForTeacherApplication(TeacherRegistrationApplication application) {
        List<User> admins = userRepository.findAllByRoleAndIsActiveTrue(User.UserRole.ADMIN);
        for (User admin : admins) {
            try {
                notifyServiceClient.createNotification(new CreateNotificationRequest(
                        admin.getId(),
                        "SYSTEM",
                        "新的教师注册申请",
                        String.format("用户 %s（%s）提交了教师注册申请，请尽快审核。", application.getUsername(), application.getEmail())
                ));
            } catch (Exception e) {
                log.warn("Failed to notify admin {} for teacher registration application {}", admin.getId(), application.getId(), e);
            }
        }
    }

    private void sendTeacherApprovalNotification(User user) {
        try {
            notifyServiceClient.createNotification(new CreateNotificationRequest(
                    user.getId(),
                    "SYSTEM",
                    "教师注册申请已通过",
                    "你的教师注册申请已通过审核，现在可以使用教师身份登录平台。"
            ));
        } catch (Exception e) {
            log.warn("Failed to send teacher approval notification for user {}", user.getId(), e);
        }
    }

    private void sendTeacherRejectionNotification(TeacherRegistrationApplication application) {
        log.info("Teacher registration application rejected notification prepared: email={}", application.getEmail());
    }
}
