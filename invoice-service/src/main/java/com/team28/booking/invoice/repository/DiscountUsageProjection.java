package com.team28.booking.invoice.repository;

import java.time.LocalDateTime;

public interface DiscountUsageProjection {
    Long getDiscountId();
    String getCode();
    String getDiscountType();
    Double getDiscountValue();
    Integer getTimesUsed();
    Double getTotalDiscountGiven();
    Boolean getActive();
    LocalDateTime getExpiryDate();
}
