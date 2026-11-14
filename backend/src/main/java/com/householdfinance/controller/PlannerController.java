package com.householdfinance.controller;

import com.householdfinance.service.PlannerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlannerController {

    private final PlannerService plannerService;

    public PlannerController(PlannerService plannerService) {
        this.plannerService = plannerService;
    }

    @GetMapping("/planner")
    public PlannerService.PlannerData plannerData() {
        return plannerService.getPlannerData();
    }

    @PostMapping("/planner/accounts")
    public ResponseEntity<PlannerService.Account> createAccount(@RequestBody PlannerService.AccountUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(plannerService.createAccount(request));
    }

    @PutMapping("/planner/accounts/{id}")
    public PlannerService.Account updateAccount(
            @PathVariable("id") String id,
            @RequestBody PlannerService.AccountUpsertRequest request
    ) {
        return plannerService.updateAccount(id, request);
    }

    @DeleteMapping("/planner/accounts/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable("id") String id) {
        plannerService.deleteAccount(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/planner/categories")
    public ResponseEntity<PlannerService.Category> createCategory(@RequestBody PlannerService.CategoryUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(plannerService.createCategory(request));
    }

    @PutMapping("/planner/categories/{id}")
    public PlannerService.Category updateCategory(
            @PathVariable("id") String id,
            @RequestBody PlannerService.CategoryUpsertRequest request
    ) {
        return plannerService.updateCategory(id, request);
    }

    @DeleteMapping("/planner/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable("id") String id) {
        plannerService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/planner/commitments")
    public ResponseEntity<PlannerService.Commitment> createCommitment(@RequestBody PlannerService.CommitmentUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(plannerService.createCommitment(request));
    }

    @PutMapping("/planner/commitments/{id}")
    public PlannerService.Commitment updateCommitment(
            @PathVariable("id") String id,
            @RequestBody PlannerService.CommitmentUpsertRequest request
    ) {
        return plannerService.updateCommitment(id, request);
    }

    @DeleteMapping("/planner/commitments/{id}")
    public ResponseEntity<Void> deleteCommitment(@PathVariable("id") String id) {
        plannerService.deleteCommitment(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }
}
