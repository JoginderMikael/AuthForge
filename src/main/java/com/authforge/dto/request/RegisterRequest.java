package com.authforge.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

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

    @Schema(example = "[\"ROLE_USER\"]", description = "Set of roles assigned to the user")
    private Set<String> roles;
}
