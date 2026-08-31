package com.authforge.service;

import com.authforge.dto.response.TokenResponse;
import com.authforge.exception.AuthException;
import com.authforge.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OAuth2ExchangeCodeService {

    private final StringRedisTemplate redisTemplate;
    private final TokenHasher tokenHasher;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${authforge.oauth2.exchange-code-ttl}")
    private Duration codeTtl;

    public String issue(TokenResponse tokenResponse) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try {
            redisTemplate.opsForValue().set(key(code), serialize(tokenResponse), codeTtl);
            return code;
        } catch (RuntimeException exception) {
            throw new AuthException("OAuth2 exchange service is unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    public TokenResponse consume(String code) {
        try {
            String serialized = redisTemplate.opsForValue().getAndDelete(key(code));
            if (serialized == null) {
                throw new AuthException("OAuth2 exchange code is invalid or expired", HttpStatus.UNAUTHORIZED);
            }
            return deserialize(serialized);
        } catch (AuthException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AuthException("OAuth2 exchange service is unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private String key(String code) {
        return "authforge:oauth2:exchange:" + tokenHasher.hash(code);
    }

    private String serialize(TokenResponse response) {
        return String.join(".",
                encode(response.getAccessToken()),
                encode(response.getRefreshToken()),
                encode(response.getTokenType()),
                encode(response.getId().toString()),
                encode(response.getEmail()),
                encode(response.getClientId()),
                encode(String.join(",", response.getRoles())));
    }

    private TokenResponse deserialize(String value) {
        String[] fields = value.split("\\.", -1);
        if (fields.length != 7) {
            throw new IllegalArgumentException("Malformed OAuth2 exchange payload");
        }
        String serializedRoles = decode(fields[6]);
        List<String> roles = serializedRoles.isBlank()
                ? List.of()
                : Arrays.asList(serializedRoles.split(","));
        return TokenResponse.builder()
                .accessToken(decode(fields[0]))
                .refreshToken(decode(fields[1]))
                .tokenType(decode(fields[2]))
                .id(UUID.fromString(decode(fields[3])))
                .email(decode(fields[4]))
                .clientId(decode(fields[5]))
                .roles(roles)
                .build();
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
