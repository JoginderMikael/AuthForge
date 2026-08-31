package com.authforge.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "User Registration Request Payload")
public class RegisterRequest {
    @NotBlank
    @Email
    @Schema(example = "newuser@example.com", description = "User's email address")
    private String email;

    @NotBlank
    @Size(min = 6, max = 40)
    @Schema(example = "securePassword123", description = "User's password (min 6 characters)")
    private String password;

    @Schema(example = "John", description = "User's first name")
    private String firstName;

    @Schema(example = "Doe", description = "User's last name")
    private String lastName;

    @NotBlank
    @Schema(example = "authforge_a1b2c3d4", description = "Client application / tenant identifier")
    private String clientId;
}
