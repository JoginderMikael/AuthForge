package com.authforge.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Refresh Token Request Payload")
public class RefreshTokenRequest {
    @NotBlank
    @Schema(description = "Valid refresh token")
    private String refreshToken;
}
