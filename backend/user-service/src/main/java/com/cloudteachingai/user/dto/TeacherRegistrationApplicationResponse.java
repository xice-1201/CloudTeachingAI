package com.cloudteachingai.user.dto;

import com.cloudteachingai.user.entity.TeacherRegistrationApplication;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherRegistrationApplicationResponse {

    private Long id;
    private String username;
    private String email;
    private TeacherRegistrationApplication.Status status;
    private String reviewNote;
    private Long reviewedBy;
    private Long createdUserId;
    private LocalDateTime requestedAt;
    private LocalDateTime reviewedAt;

    public static TeacherRegistrationApplicationResponse from(TeacherRegistrationApplication application) {
        return TeacherRegistrationApplicationResponse.builder()
                .id(application.getId())
                .username(application.getUsername())
                .email(application.getEmail())
                .status(application.getStatus())
                .reviewNote(application.getReviewNote())
                .reviewedBy(application.getReviewedBy())
                .createdUserId(application.getCreatedUserId())
                .requestedAt(application.getRequestedAt())
                .reviewedAt(application.getReviewedAt())
                .build();
    }
}
