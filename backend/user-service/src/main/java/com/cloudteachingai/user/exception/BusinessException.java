package com.cloudteachingai.user.exception;

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

    public static BusinessException conflict(String message) {
        return new BusinessException(40901, message);
    }

    public static BusinessException internalError(String message) {
        return new BusinessException(50001, message);
    }
}
