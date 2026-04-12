package com.team28.booking.invoice.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface DiscountUsageProjection {
    Long getDiscountId();
    String getCode();
    String getDiscountType();
    BigDecimal getDiscountValue();
    Integer getTimesUsed();
    BigDecimal getTotalDiscountGiven();
    Boolean getActive();
    LocalDateTime getExpiryDate();
}
