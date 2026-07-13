package com.householdfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "monthly_commitments")
public class MonthlyCommitmentEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(nullable = false)
    private double amount;

    @Column(nullable = false)
    private int dayOfMonth;

    @Column(length = 64)
    private String category;

    public MonthlyCommitmentEntity() {
    }

    public MonthlyCommitmentEntity(String id, String title, double amount, int dayOfMonth, String category) {
        this.id = id;
        this.title = title;
        this.amount = amount;
        this.dayOfMonth = dayOfMonth;
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

    public int getDayOfMonth() {
        return dayOfMonth;
    }

    public void setDayOfMonth(int dayOfMonth) {
        this.dayOfMonth = dayOfMonth;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
