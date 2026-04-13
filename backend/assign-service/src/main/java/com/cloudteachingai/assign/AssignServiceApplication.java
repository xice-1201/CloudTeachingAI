package com.cloudteachingai.assign;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class AssignServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssignServiceApplication.class, args);
    }
}
