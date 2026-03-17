package com.authforge.security.jwt;

import com.authforge.security.SecurityConstants;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtProviderTest {

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider();
    }

    @Test
    void generateToken_FromAuthentication_Success() {
        Authentication authentication = mock(Authentication.class);
        UserDetails userDetails = mock(UserDetails.class);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn("test@example.com");

        String token = jwtProvider.generateToken(authentication);

        assertNotNull(token);
        assertEquals("test@example.com", jwtProvider.getUsernameFromToken(token));
    }

    @Test
    void generateToken_FromUsername_Success() {
        String username = "user@example.com";
        String token = jwtProvider.generateToken(username);

        assertNotNull(token);
        assertEquals(username, jwtProvider.getUsernameFromToken(token));
    }

    @Test
    void validateToken_Valid_ReturnsTrue() {
        String username = "user@example.com";
        String token = jwtProvider.generateToken(username);
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn(username);

        assertTrue(jwtProvider.validateToken(token, userDetails));
    }

    @Test
    void validateToken_InvalidUsername_ReturnsFalse() {
        String username = "user@example.com";
        String token = jwtProvider.generateToken(username);
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("other@example.com");

        assertFalse(jwtProvider.validateToken(token, userDetails));
    }
}
