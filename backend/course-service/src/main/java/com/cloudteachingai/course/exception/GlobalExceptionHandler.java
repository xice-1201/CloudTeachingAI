package com.cloudteachingai.course.exception;

import com.cloudteachingai.course.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.PrintWriter;
import java.io.StringWriter;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.error("Business exception: code={}, message={}", e.getCode(), e.getMessage(), e);
        HttpStatus status = switch (e.getCode()) {
            case 40101 -> HttpStatus.UNAUTHORIZED;
            case 40301 -> HttpStatus.FORBIDDEN;
            case 40401 -> HttpStatus.NOT_FOUND;
            case 40901 -> HttpStatus.CONFLICT;
            case 50001 -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(ApiResponse.error(e.getCode(), e.getMessage(), stackTraceOf(e)));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidationException(Exception e) {
        log.error("Validation exception", e);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(40001, "请求参数不合法", stackTraceOf(e)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unexpected exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(50001, "服务端内部错误", stackTraceOf(e)));
    }

    private String stackTraceOf(Throwable throwable) {
        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }
}
