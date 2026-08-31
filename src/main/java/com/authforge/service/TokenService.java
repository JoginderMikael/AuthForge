package com.authforge.service;

import com.authforge.entity.RefreshToken;
import com.authforge.entity.Client;
import com.authforge.entity.User;
import com.authforge.exception.AuthException;
import com.authforge.repository.RefreshTokenRepository;
import com.authforge.repository.ClientRepository;
import com.authforge.repository.UserRepository;
import com.authforge.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Duration;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final TokenHasher tokenHasher;

    @Value("${authforge.refresh-token-ttl}")
    private Duration refreshTokenTtl;

    private final SecureRandom secureRandom = new SecureRandom();

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByTokenHash(tokenHasher.hash(token));
    }

    @Transactional
    public IssuedRefreshToken createRefreshToken(UUID userId, UUID clientId) {
        log.debug("Creating refresh token for user ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User not found", HttpStatus.NOT_FOUND));
        Client client = clientRepository.findById(clientId)
                .filter(Client::isEnabled)
                .orElseThrow(() -> new AuthException("Client not found or disabled", HttpStatus.NOT_FOUND));

        return issue(user, client);
    }

    @Transactional(noRollbackFor = AuthException.class)
    public IssuedRefreshToken rotateRefreshToken(String rawToken) {
        RefreshToken current = refreshTokenRepository.findForUpdateByTokenHash(tokenHasher.hash(rawToken))
                .orElseThrow(() -> new AuthException("Refresh token is invalid", HttpStatus.FORBIDDEN));

        verifyExpiration(current);
        User user = current.getUser();
        Client client = current.getClient();
        refreshTokenRepository.delete(current);
        refreshTokenRepository.flush();

        if (!client.isEnabled()) {
            throw new AuthException("Client is disabled", HttpStatus.FORBIDDEN);
        }

        return issue(user, client);
    }

    private IssuedRefreshToken issue(User user, Client client) {
        String rawToken = newTokenValue();
        RefreshToken refreshToken = new RefreshToken();

        refreshToken.setUser(user);
        refreshToken.setClient(client);
        refreshToken.setExpiryDate(Instant.now().plus(refreshTokenTtl));
        refreshToken.setTokenHash(tokenHasher.hash(rawToken));

        refreshToken = refreshTokenRepository.save(refreshToken);
        log.info("Refresh token created for user: {}", user.getEmail());
        List<String> roles = user.getRoles().stream()
                .map(role -> role.getName())
                .toList();
        return new IssuedRefreshToken(
                rawToken,
                refreshToken,
                user.getId(),
                user.getEmail(),
                client.getClientId(),
                roles);
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            log.warn("Refresh token expired for user: {}", token.getUser().getEmail());
            refreshTokenRepository.delete(token);
            throw new AuthException("Refresh token was expired. Please make a new signin request", HttpStatus.FORBIDDEN);
        }

        return token;
    }

    @Transactional
    public int deleteByUserId(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User not found", HttpStatus.NOT_FOUND));
        return refreshTokenRepository.deleteByUser(user);
    }

    private String newTokenValue() {
        byte[] value = new byte[32];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public record IssuedRefreshToken(
            String value,
            RefreshToken entity,
            UUID userId,
            String email,
            String clientId,
            List<String> roles) {
    }
}
