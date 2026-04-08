package com.team28.booking.invoice.dto;

import java.time.LocalDateTime;

import com.team28.booking.invoice.model.Discount;

public class AppliedDiscountDTO {
    private String discountCode;
    private Discount.DiscountType discountType;
    private Double discountApplied;
    private LocalDateTime appliedAt;

    public String getDiscountCode() {
        return discountCode;
    }

    public void setDiscountCode(String discountCode) {
        this.discountCode = discountCode;
    }

    public Discount.DiscountType getDiscountType() {
        return discountType;
    }

    public void setDiscountType(Discount.DiscountType discountType) {
        this.discountType = discountType;
    }

    public Double getDiscountApplied() {
        return discountApplied;
    }

    public void setDiscountApplied(Double discountApplied) {
        this.discountApplied = discountApplied;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }
}