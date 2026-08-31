package com.authforge.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtProvider {

    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final String issuer;

    public JwtProvider(
            @Value("${authforge.jwt.secret}") String secret,
            @Value("${authforge.jwt.access-token-ttl}") Duration accessTokenTtl,
            @Value("${authforge.jwt.issuer}") String issuer) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("AUTHFORGE_JWT_SECRET must contain at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = accessTokenTtl;
        this.issuer = issuer;
    }

    public String generateToken(Authentication authentication, String clientId) {
        return generateToken(
                extractSubject(authentication),
                clientId,
                authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }

    public String generateToken(String username, String clientId, Collection<String> authorities) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("client_id", clientId);
        claims.put("roles", List.copyOf(authorities));
        claims.put("aud", List.of(clientId));
        return createToken(claims, username);
    }

    public String extractSubject(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        if (principal instanceof OAuth2User oauth2User) {
            Object email = oauth2User.getAttributes().get("email");
            if (email instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        String name = authentication.getName();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Authenticated principal does not expose a stable subject");
        }
        return name;
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuer(issuer)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + accessTokenTtl.toMillis()))
                .signWith(key)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parser().verifyWith(key).requireIssuer(issuer).build().parseSignedClaims(token).getPayload();
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        final String username = getUsernameFromToken(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    private boolean isTokenExpired(String token) {
        final Date expiration = getClaimFromToken(token, Claims::getExpiration);
        return expiration.before(new Date());
    }

    public String getClientIdFromToken(String token) {
        return getClaimFromToken(token, claims -> claims.get("client_id", String.class));
    }

    public boolean isValid(String token) {
        try {
            getAllClaimsFromToken(token);
            return !isTokenExpired(token);
        } catch (Exception ignored) {
            return false;
        }
    }
}
