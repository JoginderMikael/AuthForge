package com.authforge.repository;

import com.authforge.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    @EntityGraph(attributePaths = {"roles", "roles.permissions", "clients"})
    Optional<User> findByEmail(String email);
    Boolean existsByEmail(String email);
}
