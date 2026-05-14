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
import com.cloudteachingai.auth.repository.PasswordResetTokenRepository;
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

    private static final String LOGIN_FAIL_KEY_PREFIX = "login_fail:";
    private static final String TOKEN_BLACKLIST_PREFIX = "token_blacklist:";
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;
    private static final int CODE_EXPIRATION_MINUTES = 15;
    private static final int CODE_LENGTH = 6;

    private final AuthCredentialRepository authCredentialRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;
    private final UserServiceClient userServiceClient;
    private final MailService mailService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = request.getEmail();
        String failKey = LOGIN_FAIL_KEY_PREFIX + email;
        String failCountStr = redisTemplate.opsForValue().get(failKey);
        int failCount = failCountStr != null ? Integer.parseInt(failCountStr) : 0;

        if (failCount >= MAX_LOGIN_ATTEMPTS) {
            throw BusinessException.forbidden("账号已锁定，请 15 分钟后重试");
        }

        AuthCredential credential = authCredentialRepository.findByEmail(email)
                .orElseThrow(() -> BusinessException.unauthorized("用户名或密码错误"));

        if (credential.isLocked()) {
            throw BusinessException.forbidden("账号已锁定，请稍后重试");
        }

        if (!passwordEncoder.matches(request.getPassword(), credential.getPasswordHash())) {
            redisTemplate.opsForValue().increment(failKey);
            redisTemplate.expire(failKey, LOCK_DURATION_MINUTES, TimeUnit.MINUTES);

            credential.incrementFailCount();
            authCredentialRepository.save(credential);

            throw BusinessException.unauthorized("用户名或密码错误");
        }

        redisTemplate.delete(failKey);
        credential.resetFailCount();
        credential.setLastLoginAt(LocalDateTime.now());
        authCredentialRepository.save(credential);

        String role = getUserRole(credential.getUserId());
        String accessToken = jwtUtil.generateAccessToken(credential.getUserId(), role);
        String refreshTokenId = UUID.randomUUID().toString();

        RefreshToken refreshToken = RefreshToken.builder()
                .tokenId(refreshTokenId)
                .userId(credential.getUserId())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

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
        String blacklistKey = TOKEN_BLACKLIST_PREFIX + accessToken;
        redisTemplate.opsForValue().set(blacklistKey, "1", 2, TimeUnit.HOURS);

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

        String role = getUserRole(refreshToken.getUserId());
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
        createCredentialWithHash(userId, email, passwordEncoder.encode(password));
    }

    @Transactional
    public void createCredentialWithHash(Long userId, String email, String passwordHash) {
        if (authCredentialRepository.existsByEmail(email)) {
            throw BusinessException.conflict("邮箱已被注册");
        }

        AuthCredential credential = AuthCredential.builder()
                .userId(userId)
                .email(email)
                .passwordHash(passwordHash)
                .loginFailCount(0)
                .build();

        authCredentialRepository.save(credential);
    }

    public String encodePassword(String password) {
        return passwordEncoder.encode(password);
    }

    @Transactional
    public void deleteAccountCredentials(Long userId, String email) {
        refreshTokenRepository.deleteByUserId(userId);
        passwordResetTokenRepository.deleteByUserId(userId);
        authCredentialRepository.deleteByUserId(userId);
        if (email != null && !email.isBlank()) {
            verificationCodeRepository.deleteByEmail(email);
        }
    }

    @Transactional
    public void sendVerificationCode(SendCodeRequest request) {
        String email = request.getEmail();

        if (authCredentialRepository.existsByEmail(email)) {
            throw BusinessException.conflict("该邮箱已被注册");
        }

        verificationCodeRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc(email)
                .ifPresent(existingCode -> {
                    if (existingCode.getCreatedAt().plusSeconds(60).isAfter(LocalDateTime.now())) {
                        throw BusinessException.conflict("验证码发送过于频繁，请 60 秒后重试");
                    }
                });

        String code = generateCode();
        VerificationCode verificationCode = VerificationCode.builder()
                .email(email)
                .code(code)
                .expiresAt(LocalDateTime.now().plusMinutes(CODE_EXPIRATION_MINUTES))
                .used(false)
                .build();
        verificationCodeRepository.save(verificationCode);

        mailService.sendVerificationCode(email, code);
        log.info("Verification code sent successfully: email={}", email);
    }

    @Transactional
    public void register(RegisterRequest request) {
        String email = request.getEmail();

        if (authCredentialRepository.existsByEmail(email)) {
            throw BusinessException.conflict("该邮箱已被注册");
        }

        VerificationCode verificationCode = verificationCodeRepository
                .findTopByEmailAndUsedFalseOrderByCreatedAtDesc(email)
                .orElseThrow(() -> BusinessException.unauthorized("验证码无效或已过期"));

        if (!verificationCode.isValid()) {
            throw BusinessException.unauthorized("验证码无效或已过期");
        }

        if (!verificationCode.getCode().equals(request.getCode())) {
            throw BusinessException.unauthorized("验证码错误");
        }

        verificationCode.setUsed(true);
        verificationCodeRepository.save(verificationCode);

        RegisterUserRequest registerUserRequest = RegisterUserRequest.builder()
                .username(request.getUsername())
                .email(email)
                .password(request.getPassword())
                .role(request.getRole())
                .build();

        UserRoleResponse response = userServiceClient.registerUser(registerUserRequest);
        if (response == null || response.getData() == null) {
            throw BusinessException.internalError("创建用户失败");
        }

        Long userId = response.getData().getId();
        createCredential(userId, email, request.getPassword());
        log.info("User registered successfully: email={}, userId={}", email, userId);
    }

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
        log.info("Login event: userId={}, email={}, success={}", userId, email, success);
    }
}
