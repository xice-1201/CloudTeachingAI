package com.cloudteachingai.user.dto;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String username;
    private String avatar;
}
