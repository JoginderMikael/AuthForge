package com.authforge.controller;

import com.authforge.dto.request.LoginRequest;
import com.authforge.dto.request.RefreshTokenRequest;
import com.authforge.dto.request.RegisterRequest;
import com.authforge.dto.response.AuthResponse;
import com.authforge.dto.response.TokenResponse;
import com.authforge.security.jwt.JwtAuthenticationFilter;
import com.authforge.security.jwt.JwtTokenValidator;
import com.authforge.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = AuthController.class, excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenValidator jwtTokenValidator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void authenticateUser_Success() throws Exception {
        LoginRequest loginRequest = new LoginRequest("test@example.com", "password");
        TokenResponse tokenResponse = TokenResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .email("test@example.com")
                .id(UUID.randomUUID())
                .roles(Collections.singletonList("ROLE_USER"))
                .build();

        when(authService.authenticateUser(any(LoginRequest.class))).thenReturn(tokenResponse);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    void registerUser_Success() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail("new@example.com");
        registerRequest.setPassword("password123");
        registerRequest.setFirstName("John");
        registerRequest.setLastName("Doe");

        AuthResponse authResponse = AuthResponse.builder()
                .message("User registered successfully!")
                .success(true)
                .build();

        when(authService.registerUser(any(RegisterRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User registered successfully!"))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void refreshToken_Success() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");
        TokenResponse tokenResponse = TokenResponse.builder()
                .accessToken("new-access-token")
                .refreshToken("valid-refresh-token")
                .build();

        when(authService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(tokenResponse);

        mockMvc.perform(post("/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"));
    }

    @Test
    void validateToken_Success() throws Exception {
        String token = "Bearer valid-token";
        when(jwtTokenValidator.isValid("valid-token")).thenReturn(true);

        mockMvc.perform(post("/auth/validate")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }
}
