package com.cloudteachingai.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReviewTeacherRegistrationApplicationRequest {

    @NotNull(message = "reviewerId is required")
    private Long reviewerId;

    @Size(max = 500, message = "reviewNote length must be less than 500")
    private String reviewNote;
}
