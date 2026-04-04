package com.cloudteachingai.auth.controller;

import com.cloudteachingai.auth.dto.ApiResponse;
import com.cloudteachingai.auth.dto.LoginRequest;
import com.cloudteachingai.auth.dto.LoginResponse;
import com.cloudteachingai.auth.dto.RegisterRequest;
import com.cloudteachingai.auth.dto.SendCodeRequest;
import com.cloudteachingai.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request for email: {}", request.getEmail());
        LoginResponse response = authService.login(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam String refreshToken) {
        String accessToken = authHeader.replace("Bearer ", "");
        authService.logout(accessToken, refreshToken);
        return ApiResponse.success(null);
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@RequestParam String refreshToken) {
        LoginResponse response = authService.refreshToken(refreshToken);
        return ApiResponse.success(response);
    }

    @PostMapping("/send-code")
    public ApiResponse<Void> sendVerificationCode(@Valid @RequestBody SendCodeRequest request) {
        log.info("Send verification code request for email: {}", request.getEmail());
        authService.sendVerificationCode(request);
        return ApiResponse.success(null);
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Register request for email: {}", request.getEmail());
        authService.register(request);
        return ApiResponse.success(null);
    }
}
