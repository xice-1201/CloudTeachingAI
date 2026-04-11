package com.cloudteachingai.course.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public static BusinessException unauthorized(String message) {
        return new BusinessException(40101, message);
    }
}
