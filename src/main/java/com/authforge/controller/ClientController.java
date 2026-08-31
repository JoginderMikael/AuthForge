package com.authforge.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.authforge.dto.request.ClientRegistrationRequest;
import com.authforge.dto.response.ClientRegistrationResponse;
import com.authforge.service.ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Clients", description = "Endpoints for managing client applications")
public class ClientController {

    private final ClientService clientService;

    @Operation(summary = "Register a client", description = "Registers a new application client and returns its credentials")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Client registered successfully")
    })
    @PostMapping("/register")
    public ResponseEntity<ClientRegistrationResponse> registerClient(
            @RequestHeader("X-AuthForge-Bootstrap-Token") String bootstrapToken,
            @Valid @RequestBody ClientRegistrationRequest request) {
        log.info("Registering client application: {}", request.getName());
        return ResponseEntity.ok(clientService.registerClient(request, bootstrapToken));
    }
}
