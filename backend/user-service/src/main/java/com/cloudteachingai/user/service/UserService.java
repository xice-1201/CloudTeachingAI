package com.cloudteachingai.user.service;

import com.cloudteachingai.user.client.AuthServiceClient;
import com.cloudteachingai.user.dto.CreateUserRequest;
import com.cloudteachingai.user.dto.PageResponse;
import com.cloudteachingai.user.dto.UpdateProfileRequest;
import com.cloudteachingai.user.dto.UserResponse;
import com.cloudteachingai.user.entity.User;
import com.cloudteachingai.user.exception.BusinessException;
import com.cloudteachingai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AuthServiceClient authServiceClient;

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
}
