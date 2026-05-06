package com.cloudteachingai.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "system-health")
public class SystemHealthProperties {

    private int timeoutSeconds = 5;
    private List<Target> targets = new ArrayList<>();

    @Data
    public static class Target {
        private String key;
        private String name;
        private String url;
    }
}
