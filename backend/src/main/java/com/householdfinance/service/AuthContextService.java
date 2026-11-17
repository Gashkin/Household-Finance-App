package com.householdfinance.service;

import org.springframework.stereotype.Service;

@Service
public class AuthContextService {
    public String currentRole() {
        var actor=com.householdfinance.access.AccessContext.actor();
        com.householdfinance.access.AccessContext.userId();
        return actor.edits()?"planner":actor.reads()?"consumer":"household";
    }
}
