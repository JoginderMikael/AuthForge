package com.authforge.service;

import com.authforge.entity.Client;
import com.authforge.entity.RefreshToken;
import com.authforge.entity.User;
import com.authforge.exception.AuthException;
import com.authforge.repository.ClientRepository;
import com.authforge.repository.RefreshTokenRepository;
import com.authforge.repository.UserRepository;
import com.authforge.security.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock UserRepository userRepository;
    @Mock ClientRepository clientRepository;

    private final TokenHasher tokenHasher = new TokenHasher();

    @InjectMocks
    private TokenService tokenService;

    private User user;
    private Client client;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService(refreshTokenRepository, userRepository, clientRepository, tokenHasher);
        ReflectionTestUtils.setField(tokenService, "refreshTokenTtl", Duration.ofDays(7));
        user = User.builder().email("test@example.com").password("encoded").build();
        user.setId(UUID.randomUUID());
        client = Client.builder().clientId("authforge_test").clientSecret("encoded").name("Test").enabled(true).build();
        client.setId(UUID.randomUUID());
    }

    @Test
    void findByToken_HashesLookupValue() {
        RefreshToken stored = new RefreshToken();
        when(refreshTokenRepository.findByTokenHash(tokenHasher.hash("raw-token"))).thenReturn(Optional.of(stored));

        assertSame(stored, tokenService.findByToken("raw-token").orElseThrow());
    }

    @Test
    void createRefreshToken_StoresOnlyHash() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(clientRepository.findById(client.getId())).thenReturn(Optional.of(client));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TokenService.IssuedRefreshToken issued = tokenService.createRefreshToken(user.getId(), client.getId());

        assertNotNull(issued.value());
        assertEquals(tokenHasher.hash(issued.value()), issued.entity().getTokenHash());
        assertFalse(issued.entity().getTokenHash().contains(issued.value()));
        assertEquals(client, issued.entity().getClient());
        assertTrue(issued.entity().getExpiryDate().isAfter(Instant.now()));
    }

    @Test
    void rotateRefreshToken_DeletesOldAndReturnsNewValue() {
        RefreshToken old = RefreshToken.builder()
                .tokenHash(tokenHasher.hash("old-token"))
                .expiryDate(Instant.now().plusSeconds(60))
                .user(user)
                .client(client)
                .build();
        when(refreshTokenRepository.findForUpdateByTokenHash(tokenHasher.hash("old-token"))).thenReturn(Optional.of(old));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TokenService.IssuedRefreshToken rotated = tokenService.rotateRefreshToken("old-token");

        verify(refreshTokenRepository).delete(old);
        verify(refreshTokenRepository).flush();
        assertNotEquals("old-token", rotated.value());
        assertEquals(tokenHasher.hash(rotated.value()), rotated.entity().getTokenHash());
    }

    @Test
    void rotateRefreshToken_RejectsReplay() {
        when(refreshTokenRepository.findForUpdateByTokenHash(tokenHasher.hash("used-token"))).thenReturn(Optional.empty());

        AuthException exception = assertThrows(AuthException.class,
                () -> tokenService.rotateRefreshToken("used-token"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void verifyExpiration_ExpiredTokenDeletesAndRejects() {
        RefreshToken token = RefreshToken.builder()
                .expiryDate(Instant.now().minusSeconds(1))
                .user(user)
                .client(client)
                .build();

        AuthException exception = assertThrows(AuthException.class, () -> tokenService.verifyExpiration(token));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verify(refreshTokenRepository).delete(token);
    }

    @Test
    void deleteByUserId_UserMissingIsNotServerError() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.empty());

        AuthException exception = assertThrows(AuthException.class, () -> tokenService.deleteByUserId(user.getId()));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }
}
