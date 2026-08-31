package com.authforge.service;

import com.authforge.dto.request.LoginRequest;
import com.authforge.dto.request.RefreshTokenRequest;
import com.authforge.dto.request.RegisterRequest;
import com.authforge.dto.response.AuthResponse;
import com.authforge.dto.response.TokenResponse;
import com.authforge.entity.Client;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock AuthenticationManager authenticationManager;
    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtProvider jwtProvider;
    @Mock TokenService tokenService;
    @Mock ClientService clientService;
    @Mock LoginProtectionService loginProtectionService;

    @InjectMocks
    private AuthService authService;

    private User user;
    private Role role;
    private Client client;

    @BeforeEach
    void setUp() {
        role = Role.builder().name("ROLE_USER").build();
        role.setId(UUID.randomUUID());
        client = Client.builder().clientId("authforge_test").clientSecret("encoded").name("Test").build();
        client.setId(UUID.randomUUID());
        user = User.builder()
                .email("test@example.com")
                .password("encodedPassword")
                .roles(new HashSet<>(Set.of(role)))
                .clients(new HashSet<>(Set.of(client)))
                .build();
        user.setId(UUID.randomUUID());
    }

    @Test
    void authenticateUser_Success() {
        LoginRequest request = new LoginRequest("test@example.com", "password", client.getClientId());
        Authentication authentication = mock(Authentication.class);
        UserDetails userDetails = mock(UserDetails.class);

        when(clientService.requireEnabledClient(client.getClientId())).thenReturn(client);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn(user.getEmail());
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_USER"))).when(userDetails).getAuthorities();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(jwtProvider.generateToken(user.getEmail(), client.getClientId(), List.of("ROLE_USER"))).thenReturn("jwt-token");
        when(tokenService.createRefreshToken(user.getId(), client.getId()))
                .thenReturn(new TokenService.IssuedRefreshToken("refresh-token", new RefreshToken(), user, client));

        TokenResponse response = authService.authenticateUser(request);

        assertEquals("jwt-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals(client.getClientId(), response.getClientId());
        verify(loginProtectionService).recordSuccess(client.getClientId(), user.getEmail());
    }

    @Test
    void registerUser_AssignsDefaultRoleAndClientBeforeSave() {
        RegisterRequest request = registrationRequest("new@example.com");
        when(clientService.requireEnabledClient(client.getClientId())).thenReturn(client);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encodedPassword");
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(role));

        AuthResponse response = authService.registerUser(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertTrue(response.isSuccess());
        assertEquals(Set.of(role), saved.getRoles());
        assertEquals(Set.of(client), saved.getClients());
        assertNotEquals(request.getPassword(), saved.getPassword());
    }

    @Test
    void registerUser_EmailAlreadyExists_ThrowsException() {
        RegisterRequest request = registrationRequest("test@example.com");
        when(clientService.requireEnabledClient(client.getClientId())).thenReturn(client);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        AuthException exception = assertThrows(AuthException.class, () -> authService.registerUser(request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(userRepository, never()).save(any());
    }

    @Test
    void refreshToken_RotatesRefreshToken() {
        RefreshTokenRequest request = new RefreshTokenRequest("old-refresh-token");
        when(tokenService.rotateRefreshToken("old-refresh-token"))
                .thenReturn(new TokenService.IssuedRefreshToken("new-refresh-token", new RefreshToken(), user, client));
        when(jwtProvider.generateToken(user.getEmail(), client.getClientId(), List.of("ROLE_USER")))
                .thenReturn("new-jwt-token");

        TokenResponse response = authService.refreshToken(request);

        assertEquals("new-jwt-token", response.getAccessToken());
        assertEquals("new-refresh-token", response.getRefreshToken());
        assertEquals(client.getClientId(), response.getClientId());
    }

    @Test
    void refreshToken_InvalidToken_PropagatesForbidden() {
        RefreshTokenRequest request = new RefreshTokenRequest("invalid-token");
        when(tokenService.rotateRefreshToken("invalid-token"))
                .thenThrow(new AuthException("Refresh token is invalid", HttpStatus.FORBIDDEN));

        AuthException exception = assertThrows(AuthException.class, () -> authService.refreshToken(request));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    private RegisterRequest registrationRequest(String email) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setPassword("password123");
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setClientId(client.getClientId());
        return request;
    }
}
