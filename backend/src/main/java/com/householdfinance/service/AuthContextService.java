package com.householdfinance.service;

import org.springframework.stereotype.Service;

@Service
public class AuthContextService {
    public String currentRole() {
        return "planner";
    }
}
