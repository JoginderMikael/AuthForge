package com.authforge.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

class JwtProviderTest {

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(
                "test-only-secret-with-at-least-thirty-two-bytes",
                Duration.ofHours(1),
                "http://localhost:8082");
    }

    @Test
    void generateToken_FromAuthentication_Success() {
        Authentication authentication = mock(Authentication.class);
        UserDetails userDetails = mock(UserDetails.class);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn("test@example.com");

        doReturn(List.of(new SimpleGrantedAuthority("ROLE_USER"))).when(authentication).getAuthorities();

        String token = jwtProvider.generateToken(authentication, "authforge_test");

        assertNotNull(token);
        assertEquals("test@example.com", jwtProvider.getUsernameFromToken(token));
        assertEquals("authforge_test", jwtProvider.getClientIdFromToken(token));
    }

    @Test
    void generateToken_FromOAuth2Principal_UsesEmailWithoutClassCast() {
        Authentication authentication = mock(Authentication.class);
        DefaultOAuth2User oauth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("sub", "provider-id", "email", "oauth@example.com"),
                "sub");
        when(authentication.getPrincipal()).thenReturn(oauth2User);
        doReturn(oauth2User.getAuthorities()).when(authentication).getAuthorities();

        String token = jwtProvider.generateToken(authentication, "authforge_test");

        assertNotNull(token);
        assertEquals("oauth@example.com", jwtProvider.getUsernameFromToken(token));
    }

    @Test
    void validateToken_Valid_ReturnsTrue() {
        String username = "user@example.com";
        String token = jwtProvider.generateToken(username, "authforge_test", List.of("ROLE_USER"));
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn(username);

        assertTrue(jwtProvider.validateToken(token, userDetails));
    }

    @Test
    void validateToken_InvalidUsername_ReturnsFalse() {
        String username = "user@example.com";
        String token = jwtProvider.generateToken(username, "authforge_test", List.of("ROLE_USER"));
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("other@example.com");

        assertFalse(jwtProvider.validateToken(token, userDetails));
    }
}
