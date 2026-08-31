package com.authforge.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OAuth2ExchangeRequest {

    @NotBlank
    private String code;
}
