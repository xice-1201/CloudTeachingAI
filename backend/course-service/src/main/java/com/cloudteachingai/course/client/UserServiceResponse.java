package com.cloudteachingai.course.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserServiceResponse {

    private Integer code;
    private String message;
    private UserData data;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserData {
        private Long id;
        private String username;
        private String email;
        private String role;
        private Boolean isActive;
        private String avatar;
        private String createdAt;
    }
}
