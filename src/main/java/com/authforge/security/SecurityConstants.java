package com.authforge.security;

public class SecurityConstants {
    public static final long ACCESS_TOKEN_EXPIRATION = 3600000; // 1 hour
    public static final long REFRESH_TOKEN_EXPIRATION = 604800000; // 7 days
    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String HEADER_STRING = "Authorization";
    
    // In a real application, these should be in application.yml
    public static final String JWT_SECRET = "verySecretKeyThatShouldBeLongAndStoredInConfiguration";
}
