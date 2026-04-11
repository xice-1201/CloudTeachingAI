package com.cloudteachingai.auth.service;

import com.cloudteachingai.auth.client.RegisterUserRequest;
import com.cloudteachingai.auth.client.UserRoleResponse;
import com.cloudteachingai.auth.client.UserServiceClient;
import com.cloudteachingai.auth.dto.RegisterRequest;
import com.cloudteachingai.auth.dto.SendCodeRequest;
import com.cloudteachingai.auth.entity.AuthCredential;
import com.cloudteachingai.auth.entity.VerificationCode;
import com.cloudteachingai.auth.exception.BusinessException;
import com.cloudteachingai.auth.repository.AuthCredentialRepository;
import com.cloudteachingai.auth.repository.VerificationCodeRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthRegistrationService {

    private static final int CODE_EXPIRATION_MINUTES = 15;
    private static final int CODE_LENGTH = 6;

    private final AuthCredentialRepository authCredentialRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final UserServiceClient userServiceClient;
    private final MailService mailService;
    private final AuthService authService;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public void sendVerificationCode(SendCodeRequest request) {
        String email = request.getEmail();
        ensureEmailAvailable(email);

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
        ensureEmailAvailable(email);

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
                .build();

        UserRoleResponse response = userServiceClient.registerUser(registerUserRequest);
        if (response == null || response.getData() == null) {
            throw BusinessException.internalError("创建用户失败");
        }

        Long userId = response.getData().getId();
        authService.createCredential(userId, email, request.getPassword());

        log.info("User registered successfully: email={}, userId={}", email, userId);
    }

    private void ensureEmailAvailable(String email) {
        AuthCredential existingCredential = authCredentialRepository.findByEmail(email).orElse(null);
        if (existingCredential == null) {
            return;
        }

        if (userExistsByEmail(email)) {
            throw BusinessException.conflict("邮箱已被注册");
        }

        log.warn("Found orphan auth credential for email={}, removing stale credential before registration", email);
        authCredentialRepository.deleteByEmail(email);
    }

    private boolean userExistsByEmail(String email) {
        try {
            UserRoleResponse response = userServiceClient.getUserByEmail(email);
            return response != null && response.getData() != null;
        } catch (FeignException.NotFound e) {
            return false;
        } catch (FeignException e) {
            log.error("Failed to verify user existence for email={}", email, e);
            throw BusinessException.internalError("用户服务暂时不可用，请稍后重试");
        }
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }
}
