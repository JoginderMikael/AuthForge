package com.authforge.repository;

import com.authforge.entity.SocialAccount;
import com.authforge.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SocialAccountRepository extends JpaRepository<SocialAccount, UUID> {
    Optional<SocialAccount> findByProviderAndProviderId(String provider, String providerId);
    Optional<SocialAccount> findByUserAndProvider(User user, String provider);
}
