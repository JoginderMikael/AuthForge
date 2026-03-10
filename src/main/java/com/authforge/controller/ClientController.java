package com.authforge.controller;

import com.authforge.entity.Client;
import com.authforge.service.ClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @PostMapping("/register")
    public ResponseEntity<Client> registerClient(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        String redirectUri = request.get("redirectUri");
        return ResponseEntity.ok(clientService.registerClient(name, redirectUri));
    }
}
