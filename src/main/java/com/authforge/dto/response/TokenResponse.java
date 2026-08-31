package com.authforge.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "Authentication Token Response Payload")
public class TokenResponse {
    @Schema(description = "JWT Access Token")
    private String accessToken;
    @Schema(description = "Refresh Token")
    private String refreshToken;
    @Builder.Default
    @Schema(example = "Bearer")
    private String tokenType = "Bearer";
    @Schema(description = "User unique ID")
    private UUID id;
    @Schema(example = "user@example.com")
    private String email;
    @Schema(example = "authforge_a1b2c3d4")
    private String clientId;
    @Schema(example = "[\"ROLE_USER\"]")
    private List<String> roles;
}
