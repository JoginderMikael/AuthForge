package com.authforge.service;

import com.authforge.dto.request.LoginRequest;
import com.authforge.dto.request.RefreshTokenRequest;
import com.authforge.dto.request.RegisterRequest;
import com.authforge.dto.response.AuthResponse;
import com.authforge.dto.response.TokenResponse;
import com.authforge.entity.RefreshToken;
import com.authforge.entity.Role;
import com.authforge.entity.User;
import com.authforge.exception.AuthException;
import com.authforge.repository.RoleRepository;
import com.authforge.repository.UserRepository;
import com.authforge.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private AuthService authService;

    private User user;
    private Role role;

    @BeforeEach
    void setUp() {
        role = new Role();
        role.setName("ROLE_USER");
        role.setId(UUID.randomUUID());

        user = User.builder()
                .email("test@example.com")
                .password("encodedPassword")
                .roles(Collections.singleton(role))
                .build();
        user.setId(UUID.randomUUID());
    }

    @Test
    void authenticateUser_Success() {
        LoginRequest loginRequest = new LoginRequest("test@example.com", "password");
        Authentication authentication = mock(Authentication.class);
        UserDetails userDetails = mock(UserDetails.class);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn("test@example.com");
        doReturn(Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")))
                .when(userDetails)
                .getAuthorities();
        when(jwtProvider.generateToken(authentication)).thenReturn("jwt-token");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("refresh-token");
        when(tokenService.createRefreshToken(user.getId())).thenReturn(refreshToken);

        TokenResponse response = authService.authenticateUser(loginRequest);

        assertNotNull(response);
        assertEquals("jwt-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals(user.getEmail(), response.getEmail());
        assertTrue(response.getRoles().contains("ROLE_USER"));
    }

    @Test
    void registerUser_Success() {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail("new@example.com");
        registerRequest.setPassword("password");
        registerRequest.setFirstName("John");
        registerRequest.setLastName("Doe");

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password")).thenReturn("encodedPassword");
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(role));

        AuthResponse response = authService.registerUser(registerRequest);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("User registered successfully!", response.getMessage());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void registerUser_EmailAlreadyExists_ThrowsException() {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail("test@example.com");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        AuthException exception = assertThrows(AuthException.class, () -> authService.registerUser(registerRequest));
        assertEquals("Email is already in use!", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void refreshToken_Success() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("valid-refresh-token");
        refreshToken.setUser(user);
        refreshToken.setExpiryDate(Instant.now().plusSeconds(3600));

        when(tokenService.findByToken("valid-refresh-token")).thenReturn(Optional.of(refreshToken));
        when(tokenService.verifyExpiration(refreshToken)).thenReturn(refreshToken);
        when(jwtProvider.generateToken(user.getEmail())).thenReturn("new-jwt-token");

        TokenResponse response = authService.refreshToken(request);

        assertNotNull(response);
        assertEquals("new-jwt-token", response.getAccessToken());
        assertEquals("valid-refresh-token", response.getRefreshToken());
        assertEquals(user.getEmail(), response.getEmail());
    }

    @Test
    void refreshToken_InvalidToken_ThrowsException() {
        RefreshTokenRequest request = new RefreshTokenRequest("invalid-token");

        when(tokenService.findByToken("invalid-token")).thenReturn(Optional.empty());

        AuthException exception = assertThrows(AuthException.class, () -> authService.refreshToken(request));
        assertEquals("Refresh token is not in database!", exception.getMessage());
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }
}
