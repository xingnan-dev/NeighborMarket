package com.neighbormarket.web;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        var field = ex.getBindingResult().getFieldError();
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", field == null ? "请求参数不正确" : field.getDefaultMessage()));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", ex.getMessage()));
    }
    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    ResponseEntity<ApiError> badCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("BAD_CREDENTIALS", "账号或密码错误"));
    }
}
