package com.authforge.service;

import com.authforge.dto.request.ClientRegistrationRequest;
import com.authforge.dto.response.ClientRegistrationResponse;
import com.authforge.entity.Client;
import com.authforge.exception.AuthException;
import com.authforge.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientService {

    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${authforge.bootstrap-token}")
    private String bootstrapToken;

    public ClientRegistrationResponse registerClient(ClientRegistrationRequest request, String suppliedBootstrapToken) {
        verifyBootstrapToken(suppliedBootstrapToken);
        log.debug("Registering new client: {}", request.getName());
        String clientId = "authforge_" + UUID.randomUUID().toString().substring(0, 8);
        String rawSecret = "sec_" + UUID.randomUUID().toString().replace("-", "");
        Set<String> scopes = normalizeScopes(request.getScopes());
        
        Client client = Client.builder()
                .name(request.getName())
                .clientId(clientId)
                .clientSecret("{bcrypt}" + passwordEncoder.encode(rawSecret))
                .redirectUri(request.getRedirectUri())
                .scopes(String.join(" ", scopes))
                .enabled(true)
                .build();
        
        Client savedClient = clientRepository.save(client);
        log.info("Client application {} registered successfully with ID {}", request.getName(), clientId);
        
        return ClientRegistrationResponse.builder()
                .clientId(savedClient.getClientId())
                .clientSecret(rawSecret)
                .name(savedClient.getName())
                .redirectUri(savedClient.getRedirectUri())
                .scopes(scopes)
                .tokenEndpoint("/oauth2/token")
                .build();
    }

    public Client requireEnabledClient(String clientId) {
        return clientRepository.findByClientId(clientId)
                .filter(Client::isEnabled)
                .orElseThrow(() -> new AuthException("Unknown or disabled client", HttpStatus.UNAUTHORIZED));
    }

    public Set<String> scopes(Client client) {
        return normalizeScopes(Set.of(client.getScopes().split("\\s+")));
    }

    private Set<String> normalizeScopes(Set<String> requestedScopes) {
        Set<String> source = requestedScopes == null || requestedScopes.isEmpty()
                ? Set.of("api.read")
                : requestedScopes;
        return source.stream()
                .map(String::trim)
                .filter(scope -> !scope.isBlank())
                .filter(scope -> scope.matches("[A-Za-z0-9._:-]+"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void verifyBootstrapToken(String suppliedToken) {
        if (suppliedToken == null || !MessageDigest.isEqual(
                bootstrapToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new AuthException("Invalid bootstrap token", HttpStatus.UNAUTHORIZED);
        }
    }
}
