package com.cloudteachingai.auth.controller;

import com.cloudteachingai.auth.dto.ApiResponse;
import com.cloudteachingai.auth.service.AuthService;
import com.cloudteachingai.auth.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth/internal")
@RequiredArgsConstructor
public class InternalAuthController {

    private final AuthService authService;
    private final MailService mailService;

    @PostMapping("/create-credential")
    public ApiResponse<Void> createCredential(
            @RequestParam Long userId,
            @RequestParam String email,
            @RequestParam String password) {
        log.info("Create credential request: userId={}, email={}", userId, email);
        authService.createCredential(userId, email, password);
        return ApiResponse.success(null);
    }

    @PostMapping("/create-credential-with-hash")
    public ApiResponse<Void> createCredentialWithHash(
            @RequestParam Long userId,
            @RequestParam String email,
            @RequestParam String passwordHash) {
        log.info("Create credential with hash request: userId={}, email={}", userId, email);
        authService.createCredentialWithHash(userId, email, passwordHash);
        return ApiResponse.success(null);
    }

    @PostMapping("/send-teacher-approval-email")
    public ApiResponse<Void> sendTeacherApprovalEmail(
            @RequestParam String email,
            @RequestParam String username) {
        log.info("Send teacher approval email request: email={}", email);
        mailService.sendTeacherApprovalEmail(email, username);
        return ApiResponse.success(null);
    }
}
