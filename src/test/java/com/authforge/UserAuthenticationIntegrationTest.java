package com.authforge;

import com.authforge.entity.Client;
import com.authforge.entity.User;
import com.authforge.repository.ClientRepository;
import com.authforge.repository.RefreshTokenRepository;
import com.authforge.repository.UserRepository;
import com.authforge.security.TokenHasher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserAuthenticationIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ClientRepository clientRepository;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired TokenHasher tokenHasher;

    @MockitoBean
    StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        clientRepository.deleteAll();
    }

    @Test
    void registrationLoginAndRefreshRotationWorkAcrossTransactionBoundaries() throws Exception {
        Client client = clientRepository.saveAndFlush(Client.builder()
                .clientId("authforge_user_flow_" + UUID.randomUUID().toString().substring(0, 8))
                .clientSecret("{noop}not-used-by-user-flow")
                .name("User flow client")
                .scopes("api.read")
                .enabled(true)
                .build());
        String email = "integration-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

        mockMvc.perform(post("/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "integration-password",
                                "firstName", "Integration",
                                "lastName", "Test",
                                "clientId", client.getClientId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        User registered = userRepository.findByEmail(email).orElseThrow();
        assertTrue(registered.getRoles().stream().anyMatch(role -> "ROLE_USER".equals(role.getName())));
        assertTrue(registered.getClients().stream().anyMatch(item -> client.getId().equals(item.getId())));

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "integration-password",
                                "clientId", client.getClientId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"))
                .andReturn();
        JsonNode login = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String oldRefreshToken = login.get("refreshToken").asText();
        assertFalse(oldRefreshToken.isBlank());

        MvcResult refreshResult = mockMvc.perform(post("/auth/refresh-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", oldRefreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(client.getClientId()))
                .andReturn();
        JsonNode refresh = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        String newRefreshToken = refresh.get("refreshToken").asText();

        assertNotEquals(oldRefreshToken, newRefreshToken);
        assertTrue(refreshTokenRepository.findByTokenHash(tokenHasher.hash(newRefreshToken)).isPresent());
        assertTrue(refreshTokenRepository.findByTokenHash(newRefreshToken).isEmpty());

        mockMvc.perform(post("/auth/refresh-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", oldRefreshToken))))
                .andExpect(status().isForbidden());
    }
}
