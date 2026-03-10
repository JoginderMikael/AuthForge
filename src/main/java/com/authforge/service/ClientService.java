package com.authforge.service;

import com.authforge.entity.Client;
import com.authforge.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientService {

    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;

    public Client registerClient(String name, String redirectUri) {
        log.debug("Registering new client: {}", name);
        String clientId = "authforge_" + UUID.randomUUID().toString().substring(0, 8);
        String rawSecret = "sec_" + UUID.randomUUID().toString().replace("-", "");
        
        Client client = Client.builder()
                .name(name)
                .clientId(clientId)
                .clientSecret(passwordEncoder.encode(rawSecret))
                .redirectUri(redirectUri)
                .enabled(true)
                .build();
        
        Client savedClient = clientRepository.save(client);
        log.info("Client application {} registered successfully with ID {}", name, clientId);
        
        // Return with raw secret once for the user to save
        return Client.builder()
                .clientId(savedClient.getClientId())
                .clientSecret(rawSecret)
                .name(savedClient.getName())
                .redirectUri(savedClient.getRedirectUri())
                .build();
    }
}
