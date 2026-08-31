package com.authforge.service;

import com.authforge.dto.response.TokenResponse;
import com.authforge.exception.AuthException;
import com.authforge.security.TokenHasher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class OAuth2ExchangeCodeService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TokenHasher tokenHasher;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${authforge.oauth2.exchange-code-ttl}")
    private Duration codeTtl;

    public String issue(TokenResponse tokenResponse) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try {
            redisTemplate.opsForValue().set(key(code), objectMapper.writeValueAsString(tokenResponse), codeTtl);
            return code;
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new AuthException("OAuth2 exchange service is unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    public TokenResponse consume(String code) {
        try {
            String serialized = redisTemplate.opsForValue().getAndDelete(key(code));
            if (serialized == null) {
                throw new AuthException("OAuth2 exchange code is invalid or expired", HttpStatus.UNAUTHORIZED);
            }
            return objectMapper.readValue(serialized, TokenResponse.class);
        } catch (AuthException exception) {
            throw exception;
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new AuthException("OAuth2 exchange service is unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private String key(String code) {
        return "authforge:oauth2:exchange:" + tokenHasher.hash(code);
    }
}
