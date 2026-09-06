package com.jiu.careagent.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@RestControllerAdvice
public class ApiErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    public record ErrorBody(String code, String message, UUID requestId) {
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> api(ApiException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.status())
                .body(new ErrorBody(exception.code(), exception.getMessage(), RequestIds.current(request)));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class})
    ResponseEntity<ErrorBody> malformed(Exception exception, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(new ErrorBody("INVALID_REQUEST", "请求格式不正确", RequestIds.current(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> invalid(MethodArgumentNotValidException exception, HttpServletRequest request) {
        FieldError error = exception.getBindingResult().getFieldError();
        String message = error == null ? "请求参数不正确" : error.getDefaultMessage();
        return ResponseEntity.unprocessableEntity()
                .body(new ErrorBody("VALIDATION_FAILED", message, RequestIds.current(request)));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> unexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled request failure, requestId={}", RequestIds.current(request), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorBody("INTERNAL_ERROR", "服务暂时不可用", RequestIds.current(request)));
    }
}
