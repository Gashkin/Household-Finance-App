package com.householdfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "commitments")
public class CommitmentEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(nullable = false)
    private double amount;

    @Column(nullable = false, length = 32)
    private String dueDate;

    @Column(length = 64)
    private String category;

    public CommitmentEntity() {
    }

    public CommitmentEntity(String id, String title, double amount, String dueDate) {
        this(id, title, amount, dueDate, null);
    }

    public CommitmentEntity(String id, String title, double amount, String dueDate, String category) {
        this.id = id;
        this.title = title;
        this.amount = amount;
        this.dueDate = dueDate;
        this.category = category;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getDueDate() {
        return dueDate;
    }

    public void setDueDate(String dueDate) {
        this.dueDate = dueDate;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
