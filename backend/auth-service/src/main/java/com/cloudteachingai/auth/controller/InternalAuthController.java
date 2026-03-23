package com.cloudteachingai.auth.controller;

import com.cloudteachingai.auth.dto.ApiResponse;
import com.cloudteachingai.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth/internal")
@RequiredArgsConstructor
public class InternalAuthController {

    private final AuthService authService;

    @PostMapping("/create-credential")
    public ApiResponse<Void> createCredential(
            @RequestParam Long userId,
            @RequestParam String email,
            @RequestParam String password) {
        log.info("Create credential request: userId={}, email={}", userId, email);
        authService.createCredential(userId, email, password);
        return ApiResponse.success(null);
    }
}
