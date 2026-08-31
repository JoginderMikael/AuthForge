package com.authforge.security.oauth;

import com.authforge.entity.Client;
import com.authforge.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class JpaRegisteredClientRepository implements RegisteredClientRepository {

    private final ClientRepository clientRepository;

    @Override
    public void save(RegisteredClient registeredClient) {
        throw new UnsupportedOperationException("Register clients through POST /api/clients/register");
    }

    @Override
    public RegisteredClient findById(String id) {
        try {
            return clientRepository.findById(java.util.UUID.fromString(id))
                    .filter(Client::isEnabled)
                    .map(this::toRegisteredClient)
                    .orElse(null);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        return clientRepository.findByClientId(clientId)
                .filter(Client::isEnabled)
                .map(this::toRegisteredClient)
                .orElse(null);
    }

    private RegisteredClient toRegisteredClient(Client client) {
        RegisteredClient.Builder builder = RegisteredClient.withId(client.getId().toString())
                .clientId(client.getClientId())
                .clientSecret(client.getClientSecret())
                .clientName(client.getName())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(1))
                        .build());

        Arrays.stream(client.getScopes().split("\\s+"))
                .filter(scope -> !scope.isBlank())
                .forEach(builder::scope);
        return builder.build();
    }
}
