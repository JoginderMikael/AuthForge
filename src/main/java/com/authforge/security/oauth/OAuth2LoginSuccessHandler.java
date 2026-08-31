package com.authforge.security.oauth;

import com.authforge.dto.response.TokenResponse;
import com.authforge.entity.Client;
import com.authforge.entity.Role;
import com.authforge.entity.User;
import com.authforge.exception.AuthException;
import com.authforge.repository.UserRepository;
import com.authforge.security.jwt.JwtProvider;
import com.authforge.service.ClientService;
import com.authforge.service.OAuth2ExchangeCodeService;
import com.authforge.service.TokenService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;
    private final ClientService clientService;
    private final TokenService tokenService;
    private final OAuth2ExchangeCodeService exchangeCodeService;

    @Value("${authforge.oauth2.success-redirect}")
    private String successRedirect;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        
        String clientId = readClientIdCookie(request);
        Client client = clientService.requireEnabledClient(clientId);
        String email = jwtProvider.extractSubject(authentication);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException("OAuth2 user was not persisted", HttpStatus.UNAUTHORIZED));
        if (user.getClients().stream().noneMatch(existing -> existing.getId().equals(client.getId()))) {
            user.setClients(new HashSet<>(user.getClients()));
            user.getClients().add(client);
            userRepository.save(user);
        }

        List<String> roles = user.getRoles().stream().map(Role::getName).toList();
        String accessToken = jwtProvider.generateToken(email, client.getClientId(), roles);
        TokenService.IssuedRefreshToken refreshToken = tokenService.createRefreshToken(user.getId(), client.getId());
        String code = exchangeCodeService.issue(TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.value())
                .id(user.getId())
                .email(email)
                .clientId(client.getClientId())
                .roles(roles)
                .build());

        Cookie clearClient = new Cookie("AUTHFORGE_OAUTH_CLIENT", "");
        clearClient.setHttpOnly(true);
        clearClient.setPath("/");
        clearClient.setMaxAge(0);
        response.addCookie(clearClient);

        String targetUrl = UriComponentsBuilder.fromUriString(successRedirect)
                .queryParam("code", code)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private String readClientIdCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            throw new AuthException("OAuth2 client context is missing", HttpStatus.BAD_REQUEST);
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> "AUTHFORGE_OAUTH_CLIENT".equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElseThrow(() -> new AuthException("OAuth2 client context is missing", HttpStatus.BAD_REQUEST));
    }
}
