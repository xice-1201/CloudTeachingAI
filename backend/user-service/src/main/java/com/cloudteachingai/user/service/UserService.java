package com.cloudteachingai.user.service;

import com.cloudteachingai.user.client.AuthServiceClient;
import com.cloudteachingai.user.dto.CreateUserRequest;
import com.cloudteachingai.user.dto.UserResponse;
import com.cloudteachingai.user.entity.User;
import com.cloudteachingai.user.exception.BusinessException;
import com.cloudteachingai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AuthServiceClient authServiceClient;

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw BusinessException.conflict("邮箱已被注册");
        }

        // Create user in user-db
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .role(request.getRole())
                .isActive(true)
                .build();

        user = userRepository.save(user);

        // Create auth credential in auth-service
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
}
