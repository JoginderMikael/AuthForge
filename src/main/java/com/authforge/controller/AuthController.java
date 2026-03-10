package com.authforge.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.authforge.dto.request.LoginRequest;
import com.authforge.dto.request.RefreshTokenRequest;
import com.authforge.dto.request.RegisterRequest;
import com.authforge.dto.response.AuthResponse;
import com.authforge.dto.response.TokenResponse;
import com.authforge.security.jwt.JwtTokenValidator;
import com.authforge.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Endpoints for user authentication and registration")
public class AuthController {

    private final AuthService authService;
    private final JwtTokenValidator jwtTokenValidator;

    @Operation(summary = "Login a user", description = "Authenticates user credentials and returns access & refresh tokens")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully authenticated"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        log.info("Login attempt for user: {}", loginRequest.getEmail());
        return ResponseEntity.ok(authService.authenticateUser(loginRequest));
    }

    @Operation(summary = "Register a new user", description = "Creates a new user account with specified roles")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User registered successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request or email already in use")
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        log.info("Registering new user: {}", registerRequest.getEmail());
        return ResponseEntity.ok(authService.registerUser(registerRequest));
    }

    @Operation(summary = "Refresh access token", description = "Uses a valid refresh token to obtain a new access token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
            @ApiResponse(responseCode = "403", description = "Refresh token expired or invalid")
    })
    @PostMapping("/refresh-token")
    public ResponseEntity<TokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("Refreshing token for request");
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    @Operation(summary = "Validate access token", description = "Checks if the provided JWT token is valid")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Validation result returned")
    })
    @PostMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestHeader("Authorization") String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        log.debug("Validating token");
        boolean isValid = jwtTokenValidator.isValid(token);
        return ResponseEntity.ok(Map.of("valid", isValid));
    }
}
