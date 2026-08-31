package com.authforge.service;

import com.authforge.dto.request.ClientRegistrationRequest;
import com.authforge.dto.response.ClientRegistrationResponse;
import com.authforge.entity.Client;
import com.authforge.exception.AuthException;
import com.authforge.repository.ClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock ClientRepository clientRepository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks
    private ClientService clientService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(clientService, "bootstrapToken", "bootstrap-secret");
    }

    @Test
    void registerClientStoresHashAndReturnsRawSecretOnce() {
        ClientRegistrationRequest request = new ClientRegistrationRequest();
        request.setName("Inventory API");
        request.setScopes(Set.of("inventory.read", "inventory.write"));
        when(passwordEncoder.encode(any())).thenReturn("bcrypt-hash");
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClientRegistrationResponse response = clientService.registerClient(request, "bootstrap-secret");

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(captor.capture());
        assertEquals("bcrypt-hash", captor.getValue().getClientSecret());
        assertNotEquals(response.getClientSecret(), captor.getValue().getClientSecret());
        assertEquals(Set.of("inventory.read", "inventory.write"), response.getScopes());
        assertEquals("/oauth2/token", response.getTokenEndpoint());
    }

    @Test
    void registerClientRejectsInvalidBootstrapToken() {
        ClientRegistrationRequest request = new ClientRegistrationRequest();
        request.setName("Inventory API");

        assertThrows(AuthException.class, () -> clientService.registerClient(request, "wrong"));

        verify(clientRepository, never()).save(any());
    }
}
