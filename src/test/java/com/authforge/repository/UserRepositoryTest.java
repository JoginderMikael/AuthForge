package com.authforge.repository;

import com.authforge.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.persistence.EntityManager;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class UserRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByEmail_Success() {
        User user = User.builder()
                .email("test@example.com")
                .password("password")
                .build();
        entityManager.persist(user);
        entityManager.flush();

        Optional<User> found = userRepository.findByEmail("test@example.com");

        assertTrue(found.isPresent());
        assertEquals(user.getEmail(), found.get().getEmail());
    }

    @Test
    void existsByEmail_ReturnsTrue() {
        User user = User.builder()
                .email("exists@example.com")
                .password("password")
                .build();
        entityManager.persist(user);
        entityManager.flush();

        Boolean exists = userRepository.existsByEmail("exists@example.com");

        assertTrue(exists);
    }

    @Test
    void existsByEmail_ReturnsFalse() {
        Boolean exists = userRepository.existsByEmail("notfound@example.com");

        assertFalse(exists);
    }
}
