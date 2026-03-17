package com.authforge.service;

import com.authforge.entity.RefreshToken;
import com.authforge.entity.User;
import com.authforge.exception.AuthException;
import com.authforge.repository.RefreshTokenRepository;
import com.authforge.repository.UserRepository;
import com.authforge.security.SecurityConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TokenService tokenService;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
        user.setEmail("test@example.com");
    }

    @Test
    void findByToken_Success() {
        String token = "some-token";
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(token);
        
        when(refreshTokenRepository.findByToken(token)).thenReturn(Optional.of(refreshToken));
        
        Optional<RefreshToken> result = tokenService.findByToken(token);
        
        assertTrue(result.isPresent());
        assertEquals(token, result.get().getToken());
    }

    @Test
    void createRefreshToken_Success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        RefreshToken result = tokenService.createRefreshToken(userId);
        
        assertNotNull(result);
        assertEquals(user, result.getUser());
        assertNotNull(result.getToken());
        assertNotNull(result.getExpiryDate());
        assertTrue(result.getExpiryDate().isAfter(Instant.now()));
    }

    @Test
    void createRefreshToken_UserNotFound_ThrowsException() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        
        AuthException exception = assertThrows(AuthException.class, () -> tokenService.createRefreshToken(userId));
        assertEquals("User not found", exception.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void verifyExpiration_ValidToken_ReturnsToken() {
        RefreshToken token = new RefreshToken();
        token.setExpiryDate(Instant.now().plusSeconds(3600));
        
        RefreshToken result = tokenService.verifyExpiration(token);
        
        assertEquals(token, result);
        verify(refreshTokenRepository, never()).delete(any());
    }

    @Test
    void verifyExpiration_ExpiredToken_ThrowsExceptionAndDeletes() {
        RefreshToken token = new RefreshToken();
        token.setExpiryDate(Instant.now().minusSeconds(3600));
        token.setUser(user);
        
        AuthException exception = assertThrows(AuthException.class, () -> tokenService.verifyExpiration(token));
        assertEquals("Refresh token was expired. Please make a new signin request", exception.getMessage());
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verify(refreshTokenRepository, times(1)).delete(token);
    }

    @Test
    void deleteByUserId_Success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.deleteByUser(user)).thenReturn(1);
        
        int result = tokenService.deleteByUserId(userId);
        
        assertEquals(1, result);
        verify(refreshTokenRepository, times(1)).deleteByUser(user);
    }
}
