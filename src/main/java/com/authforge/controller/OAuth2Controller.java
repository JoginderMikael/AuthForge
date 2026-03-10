package com.authforge.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth/oauth2")
@Slf4j
@Tag(name = "OAuth2", description = "Endpoints for OAuth2 social authentication")
public class OAuth2Controller {

    @Operation(
        summary = "Initiate OAuth2 Login", 
        description = "Redirects to the social provider login page. The endpoint is managed by Spring Security.",
        parameters = {
            @Parameter(name = "provider", description = "OAuth2 provider ID (e.g., google, github)", required = true, example = "google")
        }
    )
    @GetMapping("/authorize/{provider}")
    public void initiateLogin(@PathVariable String provider, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        response.sendRedirect("/oauth2/authorization/" + provider);
    }

    @Operation(summary = "OAuth2 Callback", description = "Landing endpoint after successful social authentication. Frontend should capture the token from URL parameters.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success (usually reached via redirect with token)"),
            @ApiResponse(responseCode = "401", description = "Authentication failed")
    })
    @GetMapping("/callback")
    public ResponseEntity<Map<String, String>> oauth2Callback(
            @RequestParam(required = false) String token,
            @RequestParam(required = false) String error) {
        
        Map<String, String> response = new HashMap<>();
        if (error != null) {
            log.error("OAuth2 callback error: {}", error);
            response.put("error", error);
            return ResponseEntity.status(401).body(response);
        }
        
        log.info("OAuth2 callback successful");
        response.put("token", token);
        response.put("message", "Authentication successful. Please use this token for subsequent requests.");
        return ResponseEntity.ok(response);
    }
}
