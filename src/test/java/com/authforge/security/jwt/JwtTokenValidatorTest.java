package com.authforge.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenValidatorTest {

    private JwtTokenValidator jwtTokenValidator;
    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(
                "test-only-secret-with-at-least-thirty-two-bytes",
                Duration.ofHours(1),
                "http://localhost:8082");
        jwtTokenValidator = new JwtTokenValidator(jwtProvider);
    }

    @Test
    void isValid_ValidToken_ReturnsTrue() {
        String token = jwtProvider.generateToken("test@example.com", "authforge_test", List.of("ROLE_USER"));
        assertTrue(jwtTokenValidator.isValid(token));
    }

    @Test
    void isValid_InvalidToken_ReturnsFalse() {
        assertFalse(jwtTokenValidator.isValid("invalid.token.here"));
    }

    @Test
    void isValid_NullToken_ReturnsFalse() {
        assertFalse(jwtTokenValidator.isValid(null));
    }
}
