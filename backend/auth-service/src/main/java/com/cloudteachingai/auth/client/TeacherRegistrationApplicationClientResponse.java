package com.cloudteachingai.auth.client;

import lombok.Data;

@Data
public class TeacherRegistrationApplicationClientResponse {
    private Integer code;
    private String message;
    private TeacherRegistrationApplicationData data;

    @Data
    public static class TeacherRegistrationApplicationData {
        private Long id;
        private String username;
        private String email;
        private String status;
    }
}
