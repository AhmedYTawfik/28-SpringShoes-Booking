package com.team28.booking.invoice.dto;

import java.time.LocalDateTime;

import com.team28.booking.invoice.model.Discount;

public record AppliedDiscountDTO(
        String discountCode,
        Discount.DiscountType discountType,
        Double discountApplied,
        LocalDateTime appliedAt
) {}
