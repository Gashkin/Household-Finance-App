package com.householdfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "transactions")
public class TransactionEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false)
    private LocalDate txnDate;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(nullable = false)
    private double amount;

    @Column(length = 64)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TransactionType txnType;

    @Column(length = 64)
    private String sourceMonthlyCommitmentId;

    public TransactionEntity() {
    }

    public TransactionEntity(
            String id,
            LocalDate txnDate,
            String title,
            double amount,
            String category,
            TransactionType txnType,
            String sourceMonthlyCommitmentId
    ) {
        this.id = id;
        this.txnDate = txnDate;
        this.title = title;
        this.amount = amount;
        this.category = category;
        this.txnType = txnType;
        this.sourceMonthlyCommitmentId = sourceMonthlyCommitmentId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public LocalDate getTxnDate() {
        return txnDate;
    }

    public void setTxnDate(LocalDate txnDate) {
        this.txnDate = txnDate;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public TransactionType getTxnType() {
        return txnType;
    }

    public void setTxnType(TransactionType txnType) {
        this.txnType = txnType;
    }

    public String getSourceMonthlyCommitmentId() {
        return sourceMonthlyCommitmentId;
    }

    public void setSourceMonthlyCommitmentId(String sourceMonthlyCommitmentId) {
        this.sourceMonthlyCommitmentId = sourceMonthlyCommitmentId;
    }
}
