package com.authforge.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "clients", indexes = @Index(name = "idx_clients_client_id", columnList = "client_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String clientId;

    @Column(nullable = false)
    private String clientSecret;

    @Column(nullable = false)
    private String name;

    private String redirectUri;

    @Column(nullable = false, length = 1000)
    @Builder.Default
    private String scopes = "api.read";

    @Builder.Default
    private boolean enabled = true;
}
