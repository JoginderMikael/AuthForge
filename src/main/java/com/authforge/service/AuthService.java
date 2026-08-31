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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final TokenService tokenService;
    private final ClientService clientService;
    private final LoginProtectionService loginProtectionService;

    public TokenResponse authenticateUser(LoginRequest loginRequest) {
        log.debug("Authenticating user: {}", loginRequest.getEmail());
        Client client = clientService.requireEnabledClient(loginRequest.getClientId());
        if (loginProtectionService.isBlocked(client.getClientId(), loginRequest.getEmail())) {
            throw new AuthException("Account temporarily locked due to failed login attempts", HttpStatus.LOCKED);
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));
        } catch (org.springframework.security.core.AuthenticationException exception) {
            loginProtectionService.recordFailure(client.getClientId(), loginRequest.getEmail());
            throw exception;
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AuthException("Invalid credentials", HttpStatus.UNAUTHORIZED));
        if (user.getClients().stream().noneMatch(item -> item.getId().equals(client.getId()))) {
            loginProtectionService.recordFailure(client.getClientId(), loginRequest.getEmail());
            throw new AuthException("Invalid credentials", HttpStatus.UNAUTHORIZED);
        }
        loginProtectionService.recordSuccess(client.getClientId(), loginRequest.getEmail());
        
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
        String jwt = jwtProvider.generateToken(user.getEmail(), client.getClientId(), roles);

        TokenService.IssuedRefreshToken refreshToken = tokenService.createRefreshToken(user.getId(), client.getId());

        log.info("User {} authenticated successfully", user.getEmail());
        return TokenResponse.builder()
                .accessToken(jwt)
                .refreshToken(refreshToken.value())
                .id(user.getId())
                .email(user.getEmail())
                .clientId(client.getClientId())
                .roles(roles)
                .build();
    }

    @Transactional
    public AuthResponse registerUser(RegisterRequest registerRequest) {
        Client client = clientService.requireEnabledClient(registerRequest.getClientId());
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            log.warn("Registration failed: Email {} is already in use", registerRequest.getEmail());
            throw new AuthException("Email is already in use!", HttpStatus.BAD_REQUEST);
        }

        User user = User.builder()
                .email(registerRequest.getEmail())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .firstName(registerRequest.getFirstName())
                .lastName(registerRequest.getLastName())
                .build();

        Set<Role> roles = new HashSet<>();
        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new AuthException("Default role is not configured", HttpStatus.INTERNAL_SERVER_ERROR));
        roles.add(userRole);
        user.setRoles(roles);
        user.setClients(new HashSet<>(Set.of(client)));

        userRepository.save(user);
        log.info("Successfully registered user: {}", user.getEmail());

        return AuthResponse.builder()
                .message("User registered successfully!")
                .success(true)
                .build();
    }

    public TokenResponse refreshToken(RefreshTokenRequest request) {
        TokenService.IssuedRefreshToken rotated = tokenService.rotateRefreshToken(request.getRefreshToken());
        User user = rotated.user();
        Client client = rotated.client();
        List<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toList());
        String token = jwtProvider.generateToken(user.getEmail(), client.getClientId(), roles);
        return TokenResponse.builder()
                .accessToken(token)
                .refreshToken(rotated.value())
                .id(user.getId())
                .email(user.getEmail())
                .clientId(client.getClientId())
                .roles(roles)
                .build();
    }
}
