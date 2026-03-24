package com.cloudteachingai.auth.client;

import lombok.Data;

@Data
public class UserRoleResponse {
    private Integer code;
    private String message;
    private UserData data;

    @Data
    public static class UserData {
        private Long id;
        private String username;
        private String email;
        private String role;
        private Boolean isActive;
    }
}
