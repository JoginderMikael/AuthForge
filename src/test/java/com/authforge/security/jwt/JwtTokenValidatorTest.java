package com.authforge.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenValidatorTest {

    private JwtTokenValidator jwtTokenValidator;
    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtTokenValidator = new JwtTokenValidator();
        jwtProvider = new JwtProvider();
    }

    @Test
    void isValid_ValidToken_ReturnsTrue() {
        String token = jwtProvider.generateToken("test@example.com");
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
