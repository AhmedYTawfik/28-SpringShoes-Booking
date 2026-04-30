package com.team28.booking.invoice.dto;

import java.math.BigDecimal;

import com.team28.booking.invoice.model.Discount;

public record DiscountUsageDTO(
Long discountId,
String code,
Discount.DiscountType discountType,
BigDecimal discountValue,
Integer timesUsed,
BigDecimal totalDiscountGiven,
Boolean active,
Boolean expired
) {
    public static DiscountUsageDTOBuilder builder() { return new DiscountUsageDTOBuilder(); }
}