package com.authforge.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "social_accounts", uniqueConstraints =
        @UniqueConstraint(name = "uk_social_provider_account", columnNames = {"provider", "provider_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialAccount extends BaseEntity {

    @Column(nullable = false)
    private String provider; // e.g., GOOGLE, GITHUB

    @Column(nullable = false)
    private String providerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
