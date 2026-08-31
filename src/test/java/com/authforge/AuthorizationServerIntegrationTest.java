package com.authforge;

import com.authforge.entity.Client;
import com.authforge.repository.ClientRepository;
import com.authforge.repository.RoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationServerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ClientRepository clientRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @MockitoBean
    StringRedisTemplate redisTemplate;

    private Client client;

    @BeforeEach
    void setUp() {
        client = Client.builder()
                .clientId("authforge_integration_" + UUID.randomUUID().toString().substring(0, 8))
                .clientSecret("{bcrypt}" + passwordEncoder.encode("integration-secret"))
                .name("Integration client")
                .scopes("api.read")
                .enabled(true)
                .build();
        client = clientRepository.saveAndFlush(client);
    }

    @AfterEach
    void tearDown() {
        clientRepository.deleteById(client.getId());
    }

    @Test
    void migrationSeedsDefaultRole() {
        org.junit.jupiter.api.Assertions.assertTrue(roleRepository.findByName("ROLE_USER").isPresent());
    }

    @Test
    void clientCredentialsIssuesBearerToken() throws Exception {
        mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic(client.getClientId(), "integration-secret"))
                        .param("grant_type", "client_credentials")
                        .param("scope", "api.read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.scope").value("api.read"));
    }
}
