package com.authforge.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
public class ClientRegistrationResponse {
    private String clientId;
    private String clientSecret;
    private String name;
    private String redirectUri;
    private Set<String> scopes;
    private String tokenEndpoint;
}
