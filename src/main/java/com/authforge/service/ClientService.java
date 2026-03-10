package com.authforge.service;

import com.authforge.entity.Client;
import com.authforge.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;

    public Client registerClient(String name, String redirectUri) {
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
        
        // Return with raw secret once for the user to save
        return Client.builder()
                .clientId(savedClient.getClientId())
                .clientSecret(rawSecret)
                .name(savedClient.getName())
                .redirectUri(savedClient.getRedirectUri())
                .build();
    }
}
