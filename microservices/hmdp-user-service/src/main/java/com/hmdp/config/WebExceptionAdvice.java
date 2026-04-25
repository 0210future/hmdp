package com.hmdp.config;

import com.hmdp.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    @ExceptionHandler(IllegalArgumentException.class)
    public Result handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Illegal argument exception", e);
        return Result.fail(messageOrDefault(e.getMessage(), "Invalid request parameter"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("Request body parse exception", e);
        return Result.fail(messageOrDefault(rootMessage(e), "Request body format error"));
    }

    @ExceptionHandler(RedisSystemException.class)
    public Result handleRedisSystemException(RedisSystemException e) {
        log.error("Redis system exception", e);
        return Result.fail(messageOrDefault(rootMessage(e), "Redis operation failed"));
    }

    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        log.error("Unhandled runtime exception", e);
        return Result.fail(messageOrDefault(e.getMessage(), "Server exception"));
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    private String messageOrDefault(String message, String defaultMessage) {
        return message == null || message.trim().isEmpty() ? defaultMessage : message;
    }
}
