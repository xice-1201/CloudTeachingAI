package com.cloudteachingai.auth.service;

import com.cloudteachingai.auth.client.RegisterUserRequest;
import com.cloudteachingai.auth.client.UserRoleResponse;
import com.cloudteachingai.auth.client.UserServiceClient;
import com.cloudteachingai.auth.dto.LoginRequest;
import com.cloudteachingai.auth.dto.LoginResponse;
import com.cloudteachingai.auth.dto.RegisterRequest;
import com.cloudteachingai.auth.dto.SendCodeRequest;
import com.cloudteachingai.auth.entity.AuthCredential;
import com.cloudteachingai.auth.entity.RefreshToken;
import com.cloudteachingai.auth.entity.VerificationCode;
import com.cloudteachingai.auth.exception.BusinessException;
import com.cloudteachingai.auth.repository.AuthCredentialRepository;
import com.cloudteachingai.auth.repository.RefreshTokenRepository;
import com.cloudteachingai.auth.repository.VerificationCodeRepository;
import com.cloudteachingai.auth.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthCredentialRepository authCredentialRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;
    private final UserServiceClient userServiceClient;
    private final MailService mailService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
    private final SecureRandom random = new SecureRandom();

    private static final String LOGIN_FAIL_KEY_PREFIX = "login_fail:";
    private static final String TOKEN_BLACKLIST_PREFIX = "token_blacklist:";
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;
    private static final int CODE_EXPIRATION_MINUTES = 15;
    private static final int CODE_LENGTH = 6;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = request.getEmail();

        // Check login failure count in Redis
        String failKey = LOGIN_FAIL_KEY_PREFIX + email;
        String failCountStr = redisTemplate.opsForValue().get(failKey);
        int failCount = failCountStr != null ? Integer.parseInt(failCountStr) : 0;

        if (failCount >= MAX_LOGIN_ATTEMPTS) {
            throw BusinessException.forbidden("账号已锁定，请 15 分钟后重试");
        }

        // Find user credentials
        AuthCredential credential = authCredentialRepository.findByEmail(email)
                .orElseThrow(() -> BusinessException.unauthorized("用户名或密码错误"));

        // Check if account is locked
        if (credential.isLocked()) {
            throw BusinessException.forbidden("账号已锁定，请稍后重试");
        }

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), credential.getPasswordHash())) {
            // Increment fail count
            redisTemplate.opsForValue().increment(failKey);
            redisTemplate.expire(failKey, LOCK_DURATION_MINUTES, TimeUnit.MINUTES);

            credential.incrementFailCount();
            authCredentialRepository.save(credential);

            throw BusinessException.unauthorized("用户名或密码错误");
        }

        // Login successful - reset fail count
        redisTemplate.delete(failKey);
        credential.resetFailCount();
        credential.setLastLoginAt(LocalDateTime.now());
        authCredentialRepository.save(credential);

        // Get user role from user-service (for now, we'll use a placeholder)
        String role = getUserRole(credential.getUserId());

        // Generate tokens
        String accessToken = jwtUtil.generateAccessToken(credential.getUserId(), role);
        String refreshTokenId = UUID.randomUUID().toString();

        // Save refresh token to database
        RefreshToken refreshToken = RefreshToken.builder()
                .tokenId(refreshTokenId)
                .userId(credential.getUserId())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        // Publish login event to Kafka (for analysis-agent)
        publishLoginEvent(credential.getUserId(), email, true);

        LoginResponse.UserInfo userInfo = buildUserInfo(credential.getUserId(), role);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenId)
                .role(role)
                .userId(credential.getUserId())
                .user(userInfo)
                .build();
    }

    @Transactional
    public void logout(String accessToken, String refreshTokenId) {
        // Add access token to blacklist
        Long userId = jwtUtil.getUserIdFromToken(accessToken);
        String blacklistKey = TOKEN_BLACKLIST_PREFIX + accessToken;
        redisTemplate.opsForValue().set(blacklistKey, "1", 2, TimeUnit.HOURS);

        // Revoke refresh token
        refreshTokenRepository.findByTokenIdAndRevokedFalse(refreshTokenId)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    @Transactional
    public LoginResponse refreshToken(String refreshTokenId) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenIdAndRevokedFalse(refreshTokenId)
                .orElseThrow(() -> BusinessException.unauthorized("无效的 refresh token"));

        if (!refreshToken.isValid()) {
            throw BusinessException.unauthorized("refresh token 已过期或已被吊销");
        }

        // Get user role
        String role = getUserRole(refreshToken.getUserId());

        // Generate new access token
        String accessToken = jwtUtil.generateAccessToken(refreshToken.getUserId(), role);

        LoginResponse.UserInfo userInfo = buildUserInfo(refreshToken.getUserId(), role);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenId)
                .role(role)
                .userId(refreshToken.getUserId())
                .user(userInfo)
                .build();
    }

    @Transactional
    public void createCredential(Long userId, String email, String password) {
        if (authCredentialRepository.existsByEmail(email)) {
            throw BusinessException.conflict("邮箱已被注册");
        }

        String passwordHash = passwordEncoder.encode(password);

        AuthCredential credential = AuthCredential.builder()
                .userId(userId)
                .email(email)
                .passwordHash(passwordHash)
                .loginFailCount(0)
                .build();

        authCredentialRepository.save(credential);
    }

    /**
     * 发送验证码
     */
    @Transactional
    public void sendVerificationCode(SendCodeRequest request) {
        String email = request.getEmail();

        // 检查邮箱是否已注册
        if (authCredentialRepository.existsByEmail(email)) {
            throw BusinessException.conflict("该邮箱已被注册");
        }

        // 检查是否有未过期的验证码（60秒内不能重复发送）
        verificationCodeRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc(email)
                .ifPresent(existingCode -> {
                    if (existingCode.getCreatedAt().plusSeconds(60).isAfter(LocalDateTime.now())) {
                        throw BusinessException.conflict("验证码发送过于频繁，请60秒后重试");
                    }
                });

        // 生成6位数字验证码
        String code = generateCode();

        // 保存验证码
        VerificationCode verificationCode = VerificationCode.builder()
                .email(email)
                .code(code)
                .expiresAt(LocalDateTime.now().plusMinutes(CODE_EXPIRATION_MINUTES))
                .used(false)
                .build();
        verificationCodeRepository.save(verificationCode);

        // 发送邮件
        mailService.sendVerificationCode(email, code);

        log.info("验证码发送成功: email={}", email);
    }

    /**
     * 用户注册
     */
    @Transactional
    public void register(RegisterRequest request) {
        String email = request.getEmail();

        // 检查邮箱是否已注册
        if (authCredentialRepository.existsByEmail(email)) {
            throw BusinessException.conflict("该邮箱已被注册");
        }

        // 验证验证码
        VerificationCode verificationCode = verificationCodeRepository
                .findTopByEmailAndUsedFalseOrderByCreatedAtDesc(email)
                .orElseThrow(() -> BusinessException.unauthorized("验证码无效或已过期"));

        if (!verificationCode.isValid()) {
            throw BusinessException.unauthorized("验证码无效或已过期");
        }

        if (!verificationCode.getCode().equals(request.getCode())) {
            throw BusinessException.unauthorized("验证码错误");
        }

        // 标记验证码为已使用
        verificationCode.setUsed(true);
        verificationCodeRepository.save(verificationCode);

        // 创建用户（通过 user-service）
        RegisterUserRequest registerUserRequest = RegisterUserRequest.builder()
                .username(request.getUsername())
                .email(email)
                .password(request.getPassword())
                .build();

        UserRoleResponse response = userServiceClient.registerUser(registerUserRequest);
        if (response == null || response.getData() == null) {
            throw new RuntimeException("创建用户失败");
        }

        Long userId = response.getData().getId();

        // 创建凭证
        createCredential(userId, email, request.getPassword());

        log.info("用户注册成功: email={}, userId={}", email, userId);
    }

    /**
     * 生成6位数字验证码
     */
    private String generateCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    private String getUserRole(Long userId) {
        try {
            UserRoleResponse response = userServiceClient.getUserById(userId);
            if (response != null && response.getData() != null) {
                return response.getData().getRole();
            }
        } catch (Exception e) {
            log.warn("Failed to get user role from user-service for userId={}, defaulting to STUDENT", userId, e);
        }
        return "STUDENT";
    }

    private LoginResponse.UserInfo buildUserInfo(Long userId, String role) {
        try {
            UserRoleResponse response = userServiceClient.getUserById(userId);
            if (response != null && response.getData() != null) {
                UserRoleResponse.UserData data = response.getData();
                return LoginResponse.UserInfo.builder()
                        .id(data.getId())
                        .username(data.getUsername())
                        .email(data.getEmail())
                        .role(data.getRole())
                        .build();
            }
        } catch (Exception e) {
            log.warn("Failed to get user info from user-service for userId={}", userId, e);
        }
        return LoginResponse.UserInfo.builder()
                .id(userId)
                .role(role)
                .build();
    }

    private void publishLoginEvent(Long userId, String email, boolean success) {
        // TODO: Publish to Kafka topic "login.event"
        log.info("Login event: userId={}, email=, success={}", userId, email, success);
    }
}
