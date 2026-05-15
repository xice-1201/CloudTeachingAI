package com.cloudteachingai.user.service;

import com.cloudteachingai.user.client.AuthServiceClient;
import com.cloudteachingai.user.client.AssignServiceClient;
import com.cloudteachingai.user.client.ChatAgentClient;
import com.cloudteachingai.user.client.CourseServiceClient;
import com.cloudteachingai.user.client.LearnServiceClient;
import com.cloudteachingai.user.client.NotifyServiceClient;
import com.cloudteachingai.user.dto.CreateNotificationRequest;
import com.cloudteachingai.user.dto.CreateTeacherRegistrationApplicationRequest;
import com.cloudteachingai.user.dto.CreateUserRequest;
import com.cloudteachingai.user.dto.MentorRelationResponse;
import com.cloudteachingai.user.dto.PageResponse;
import com.cloudteachingai.user.dto.ReviewTeacherRegistrationApplicationRequest;
import com.cloudteachingai.user.dto.TeacherRegistrationApplicationResponse;
import com.cloudteachingai.user.dto.UpdateProfileRequest;
import com.cloudteachingai.user.dto.UserResponse;
import com.cloudteachingai.user.entity.MentorRelation;
import com.cloudteachingai.user.entity.TeacherRegistrationApplication;
import com.cloudteachingai.user.entity.User;
import com.cloudteachingai.user.exception.BusinessException;
import com.cloudteachingai.user.repository.MentorRelationRepository;
import com.cloudteachingai.user.repository.TeacherRegistrationApplicationRepository;
import com.cloudteachingai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final MentorRelationRepository mentorRelationRepository;
    private final TeacherRegistrationApplicationRepository teacherRegistrationApplicationRepository;
    private final AuthServiceClient authServiceClient;
    private final CourseServiceClient courseServiceClient;
    private final LearnServiceClient learnServiceClient;
    private final AssignServiceClient assignServiceClient;
    private final ChatAgentClient chatAgentClient;
    private final NotifyServiceClient notifyServiceClient;
    private final AdminAuditLogService adminAuditLogService;
    private final AvatarStorageService avatarStorageService;

    @Transactional
    public UserResponse createUser(CreateUserRequest request, Long actorId) {
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
        adminAuditLogService.record(
                actorId,
                "USER_CREATED",
                "USER",
                user.getId(),
                user.getUsername(),
                "Created " + user.getRole() + " user: " + user.getEmail()
        );

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
        String avatarToDelete = null;
        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            user.setUsername(request.getUsername());
        }
        if (request.getAvatar() != null) {
            String previousAvatar = user.getAvatar();
            String nextAvatar = StringUtils.hasText(request.getAvatar()) ? request.getAvatar() : null;
            user.setAvatar(nextAvatar);
            if (!Objects.equals(previousAvatar, nextAvatar)) {
                avatarToDelete = previousAvatar;
            }
        }
        user = userRepository.save(user);
        avatarStorageService.deleteIfManaged(avatarToDelete);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateAvatar(Long userId, String avatarUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        String previousAvatar = user.getAvatar();
        user.setAvatar(avatarUrl);
        user = userRepository.save(user);
        avatarStorageService.deleteIfManaged(previousAvatar);
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

    public PageResponse<UserResponse> listUsers(String keyword, String role, Boolean active, int page, int pageSize) {
        Pageable pageable = PageRequest.of(toPageIndex(page), toPageSize(pageSize), Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<User> specification = Specification
                .where(withKeyword(keyword))
                .and(withRole(role))
                .and(withActive(active));
        Page<User> users = userRepository.findAll(specification, pageable);
        List<UserResponse> items = users.getContent().stream().map(UserResponse::from).toList();
        return new PageResponse<>(items, (int) users.getTotalElements(), page, pageSize);
    }

    public Map<String, Object> getMentorRelations(Long userId) {
        User user = requireUser(userId);
        Map<String, Object> response = new LinkedHashMap<>();

        if (user.getRole() == User.UserRole.STUDENT) {
            UserResponse mentor = mentorRelationRepository
                    .findFirstByStudentIdAndStatusOrderByRequestedAtDesc(userId, MentorRelation.Status.APPROVED)
                    .flatMap(relation -> userRepository.findById(relation.getMentorId()))
                    .map(UserResponse::from)
                    .orElse(null);
            List<MentorRelationResponse> applications = mentorRelationRepository
                    .findAllByStudentIdAndStatusOrderByRequestedAtDesc(userId, MentorRelation.Status.PENDING)
                    .stream()
                    .map(this::toMentorRelationResponse)
                    .toList();
            response.put("mentor", mentor);
            response.put("students", List.of());
            response.put("applications", applications);
            return response;
        }

        if (user.getRole() == User.UserRole.TEACHER) {
            List<UserResponse> students = mentorRelationRepository
                    .findAllByMentorIdAndStatusOrderByReviewedAtDesc(userId, MentorRelation.Status.APPROVED)
                    .stream()
                    .map(relation -> userRepository.findById(relation.getStudentId()).orElse(null))
                    .filter(Objects::nonNull)
                    .map(UserResponse::from)
                    .toList();
            List<MentorRelationResponse> applications = mentorRelationRepository
                    .findAllByMentorIdAndStatusOrderByRequestedAtDesc(userId, MentorRelation.Status.PENDING)
                    .stream()
                    .map(this::toMentorRelationResponse)
                    .toList();
            response.put("mentor", null);
            response.put("students", students);
            response.put("applications", applications);
            return response;
        }

        response.put("mentor", null);
        response.put("students", List.of());
        response.put("applications", List.of());
        return response;
    }

    @Transactional
    public MentorRelationResponse applyForMentor(Long studentId, Long mentorId) {
        User student = requireUser(studentId);
        if (mentorId == null) {
            throw BusinessException.badRequest("请选择导师");
        }
        User mentor = requireUser(mentorId);
        if (student.getRole() != User.UserRole.STUDENT) {
            throw BusinessException.badRequest("只有学生可以申请导师");
        }
        if (mentor.getRole() != User.UserRole.TEACHER) {
            throw BusinessException.badRequest("只能申请教师作为导师");
        }
        if (mentorRelationRepository.existsByStudentIdAndStatus(studentId, MentorRelation.Status.APPROVED)) {
            throw BusinessException.conflict("你已经拥有导师关系");
        }
        mentorRelationRepository
                .findByStudentIdAndMentorIdAndStatus(studentId, mentorId, MentorRelation.Status.PENDING)
                .ifPresent(existing -> {
                    throw BusinessException.conflict("已向该教师提交导师申请");
                });

        MentorRelation relation = mentorRelationRepository.save(MentorRelation.builder()
                .studentId(studentId)
                .mentorId(mentorId)
                .status(MentorRelation.Status.PENDING)
                .build());
        notifyMentorApplicationSubmitted(relation, student, mentor);
        return MentorRelationResponse.from(relation, student, mentor);
    }

    @Transactional
    public MentorRelationResponse approveMentorRelation(Long teacherId, Long relationId) {
        User teacher = requireUser(teacherId);
        if (teacher.getRole() != User.UserRole.TEACHER) {
            throw BusinessException.badRequest("只有教师可以处理导师申请");
        }
        MentorRelation relation = requirePendingMentorApplicationForTeacher(relationId, teacherId);
        if (mentorRelationRepository.existsByStudentIdAndStatus(relation.getStudentId(), MentorRelation.Status.APPROVED)) {
            throw BusinessException.conflict("该学生已经拥有导师关系");
        }

        relation.setStatus(MentorRelation.Status.APPROVED);
        relation.setReviewedAt(LocalDateTime.now());
        relation = mentorRelationRepository.save(relation);
        rejectOtherPendingMentorApplications(relation);

        User student = requireUser(relation.getStudentId());
        notifyMentorApplicationApproved(relation, student, teacher);
        return MentorRelationResponse.from(relation, student, teacher);
    }

    @Transactional
    public MentorRelationResponse rejectMentorRelation(Long teacherId, Long relationId) {
        User teacher = requireUser(teacherId);
        if (teacher.getRole() != User.UserRole.TEACHER) {
            throw BusinessException.badRequest("只有教师可以处理导师申请");
        }
        MentorRelation relation = requirePendingMentorApplicationForTeacher(relationId, teacherId);
        relation.setStatus(MentorRelation.Status.REJECTED);
        relation.setReviewedAt(LocalDateTime.now());
        relation = mentorRelationRepository.save(relation);

        User student = requireUser(relation.getStudentId());
        notifyMentorApplicationRejected(relation, student, teacher);
        return MentorRelationResponse.from(relation, student, teacher);
    }

    @Transactional
    public UserResponse updateUserActive(Long userId, boolean active, Long actorId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        user.setIsActive(active);
        user = userRepository.save(user);
        adminAuditLogService.record(
                actorId,
                active ? "USER_ACTIVATED" : "USER_DEACTIVATED",
                "USER",
                user.getId(),
                user.getUsername(),
                (active ? "Activated user: " : "Deactivated user: ") + user.getEmail()
        );
        return UserResponse.from(user);
    }

    @Transactional
    public void deleteUserCompletely(Long userId, Long actorId) {
        User user = requireUser(userId);
        if (user.getRole() == User.UserRole.ADMIN) {
            throw BusinessException.badRequest("管理员账号不支持通过此功能删除");
        }

        String role = user.getRole().name();
        String username = user.getUsername();
        String email = user.getEmail();
        String avatar = user.getAvatar();

        assignServiceClient.deleteUserAssignmentData(userId, role);
        if (user.getRole() == User.UserRole.STUDENT) {
            learnServiceClient.deleteUserLearningData(userId);
        }
        courseServiceClient.deleteUserCourseData(userId, role);
        chatAgentClient.deleteUserChatData(userId);
        notifyServiceClient.deleteNotificationsForUser(userId);
        authServiceClient.deleteAccountCredentials(userId, email);

        mentorRelationRepository.deleteByStudentIdOrMentorId(userId, userId);
        teacherRegistrationApplicationRepository.deleteByEmailOrReviewedByOrCreatedUserId(email, userId, userId);
        userRepository.delete(user);
        avatarStorageService.deleteIfManaged(avatar);

        adminAuditLogService.record(
                actorId,
                "USER_DELETED",
                "USER",
                userId,
                username,
                "Deleted " + role + " user and related learning, course, assignment, notification, chat, and auth data"
        );
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
        sendTeacherApprovalEmail(user);
        adminAuditLogService.record(
                request.getReviewerId(),
                "TEACHER_APPLICATION_APPROVED",
                "TEACHER_REGISTRATION_APPLICATION",
                application.getId(),
                application.getUsername(),
                "Approved teacher application and created user: " + user.getEmail()
        );
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
        adminAuditLogService.record(
                request.getReviewerId(),
                "TEACHER_APPLICATION_REJECTED",
                "TEACHER_REGISTRATION_APPLICATION",
                application.getId(),
                application.getUsername(),
                "Rejected teacher application: " + application.getEmail()
        );
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

    private User requireUser(Long userId) {
        if (userId == null) {
            throw BusinessException.unauthorized("未提供用户身份");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
    }

    private MentorRelation requirePendingMentorApplicationForTeacher(Long relationId, Long teacherId) {
        MentorRelation relation = mentorRelationRepository.findByIdAndMentorId(relationId, teacherId)
                .orElseThrow(() -> BusinessException.notFound("导师申请不存在"));
        if (relation.getStatus() != MentorRelation.Status.PENDING) {
            throw BusinessException.conflict("导师申请已处理");
        }
        return relation;
    }

    private MentorRelationResponse toMentorRelationResponse(MentorRelation relation) {
        User student = userRepository.findById(relation.getStudentId()).orElse(null);
        User mentor = userRepository.findById(relation.getMentorId()).orElse(null);
        return MentorRelationResponse.from(relation, student, mentor);
    }

    private void rejectOtherPendingMentorApplications(MentorRelation approvedRelation) {
        List<MentorRelation> pendingApplications = mentorRelationRepository
                .findAllByStudentIdAndStatusOrderByRequestedAtDesc(
                        approvedRelation.getStudentId(),
                        MentorRelation.Status.PENDING);
        for (MentorRelation pending : pendingApplications) {
            if (pending.getId().equals(approvedRelation.getId())) {
                continue;
            }
            pending.setStatus(MentorRelation.Status.REJECTED);
            pending.setReviewedAt(LocalDateTime.now());
            pending.setReviewNote("已通过其他导师申请");
            mentorRelationRepository.save(pending);
        }
    }

    private void notifyMentorApplicationSubmitted(MentorRelation relation, User student, User mentor) {
        try {
            notifyServiceClient.createNotification(new CreateNotificationRequest(
                    mentor.getId(),
                    "SYSTEM",
                    "新的导师申请",
                    String.format("学生 %s（%s）申请成为你的指导学生，请及时处理。", student.getUsername(), student.getEmail()),
                    "MENTOR_RELATION",
                    relation.getId(),
                    "/mentor?view=applications"
            ));
        } catch (Exception e) {
            log.warn("Failed to notify mentor {} for mentor application {}", mentor.getId(), relation.getId(), e);
        }
    }

    private void notifyMentorApplicationApproved(MentorRelation relation, User student, User mentor) {
        try {
            notifyServiceClient.createNotification(new CreateNotificationRequest(
                    student.getId(),
                    "SYSTEM",
                    "导师申请已通过",
                    String.format("%s 老师已同意你的导师申请。", mentor.getUsername()),
                    "MENTOR_RELATION",
                    relation.getId(),
                    "/mentor"
            ));
        } catch (Exception e) {
            log.warn("Failed to notify student {} for approved mentor application {}", student.getId(), relation.getId(), e);
        }
    }

    private void notifyMentorApplicationRejected(MentorRelation relation, User student, User mentor) {
        try {
            notifyServiceClient.createNotification(new CreateNotificationRequest(
                    student.getId(),
                    "SYSTEM",
                    "导师申请未通过",
                    String.format("%s 老师暂未同意你的导师申请。", mentor.getUsername()),
                    "MENTOR_RELATION",
                    relation.getId(),
                    "/mentor"
            ));
        } catch (Exception e) {
            log.warn("Failed to notify student {} for rejected mentor application {}", student.getId(), relation.getId(), e);
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

    private void sendTeacherApprovalEmail(User user) {
        try {
            authServiceClient.sendTeacherApprovalEmail(user.getEmail(), user.getUsername());
        } catch (Exception e) {
            log.warn("Failed to send teacher approval email for user {}", user.getId(), e);
        }
    }

    private void sendTeacherRejectionNotification(TeacherRegistrationApplication application) {
        log.info("Teacher registration application rejected notification prepared: email={}", application.getEmail());
    }

    private int toPageIndex(int page) {
        return Math.max(page, 1) - 1;
    }

    private int toPageSize(int pageSize) {
        return Math.max(1, Math.min(pageSize, 100));
    }

    private Specification<User> withKeyword(String keyword) {
        return (root, query, criteriaBuilder) -> {
            if (!StringUtils.hasText(keyword)) {
                return criteriaBuilder.conjunction();
            }
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            return criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("username")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), pattern)
            );
        };
    }

    private Specification<User> withRole(String role) {
        return (root, query, criteriaBuilder) -> {
            if (!StringUtils.hasText(role)) {
                return criteriaBuilder.conjunction();
            }
            try {
                return criteriaBuilder.equal(root.get("role"), User.UserRole.valueOf(role.trim().toUpperCase()));
            } catch (IllegalArgumentException ex) {
                throw BusinessException.badRequest("用户角色无效");
            }
        };
    }

    private Specification<User> withActive(Boolean active) {
        return (root, query, criteriaBuilder) -> active == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.equal(root.get("isActive"), active);
    }
}
