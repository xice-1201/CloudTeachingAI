package com.cloudteachingai.learn.exception;

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

    public static BusinessException forbidden(String message) {
        return new BusinessException(40301, message);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(40401, message);
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(40001, message);
    }
}
