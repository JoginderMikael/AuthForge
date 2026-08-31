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
@Schema(description = "User Information Response Payload")
public class UserResponse {
    @Schema(description = "User unique ID")
    private UUID id;
    @Schema(example = "user@example.com")
    private String email;
    @Schema(example = "John")
    private String firstName;
    @Schema(example = "Doe")
    private String lastName;
    @Schema(example = "[\"ROLE_USER\"]")
    private List<String> roles;
    @Schema(example = "[\"authforge_a1b2c3d4\"]")
    private List<String> clients;
    @Schema(example = "true")
    private boolean enabled;
}
