package com.team28.booking.invoice.dto;

import com.team28.booking.invoice.model.Discount;

public record DiscountUsageDTO(
Long discountId,
String code,
Discount.DiscountType discountType,
Double discountValue,
Integer timesUsed,
Double totalDiscountGiven,
Boolean active,
Boolean expired
) {}