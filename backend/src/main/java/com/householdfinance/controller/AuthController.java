package com.householdfinance.controller;

import com.householdfinance.service.AuthContextService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    private final AuthContextService authContextService;

    public AuthController(AuthContextService authContextService) {
        this.authContextService = authContextService;
    }

    @GetMapping("/auth/me")
    public String currentRole() {
        return authContextService.currentRole();
    }
}
