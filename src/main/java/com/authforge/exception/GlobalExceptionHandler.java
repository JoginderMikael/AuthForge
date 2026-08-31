package com.authforge.exception;

import com.authforge.dto.response.AuthResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<AuthResponse> handleAuthException(AuthException ex) {
        log.error("Authentication error: {} - Status: {}", ex.getMessage(), ex.getStatus());
        return ResponseEntity
                .status(ex.getStatus())
                .body(AuthResponse.builder()
                        .message(ex.getMessage())
                        .success(false)
                        .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            errors.put(error.getField(), error.getDefaultMessage()));
        log.warn("Validation failed: {}", errors);
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<AuthResponse> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getClass().getSimpleName());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(AuthResponse.builder()
                        .message("Invalid credentials")
                        .success(false)
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AuthResponse> handleGeneralException(Exception ex) {
        log.error("Unexpected error occurred: ", ex);
        return ResponseEntity
                .internalServerError()
                .body(AuthResponse.builder()
                        .message("An unexpected error occurred")
                        .success(false)
                        .build());
    }
}
