package com.authforge.security.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtTokenValidator {

    private final JwtProvider jwtProvider;

    public boolean isValid(String token) {
        return jwtProvider.isValid(token);
    }
}
