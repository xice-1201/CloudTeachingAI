package com.cloudteachingai.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateTeacherRegistrationApplicationRequest {

    @NotBlank(message = "username is required")
    @Size(min = 2, max = 100, message = "username length must be between 2 and 100")
    private String username;

    @NotBlank(message = "email is required")
    @Email(message = "email format is invalid")
    private String email;

    @NotBlank(message = "passwordHash is required")
    private String passwordHash;
}
