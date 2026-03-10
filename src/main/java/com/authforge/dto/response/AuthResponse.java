package com.authforge.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "Generic Authentication Response")
public class AuthResponse {
    @Schema(example = "Operation successful")
    private String message;
    @Schema(example = "true")
    private boolean success;
}
