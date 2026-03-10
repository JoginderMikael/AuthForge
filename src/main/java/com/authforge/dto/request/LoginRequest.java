package com.authforge.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Login Request Payload")
public class LoginRequest {
    @NotBlank
    @Email
    @Schema(example = "user@example.com", description = "User's email address")
    private String email;

    @NotBlank
    @Schema(example = "password123", description = "User's password")
    private String password;
}
