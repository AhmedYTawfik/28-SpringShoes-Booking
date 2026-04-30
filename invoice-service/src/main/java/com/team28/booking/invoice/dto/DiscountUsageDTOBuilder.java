package com.team28.booking.invoice.dto;

import com.team28.booking.invoice.model.Discount;

import java.math.BigDecimal;

public class DiscountUsageDTOBuilder {
    private Long discountId;
    private String code;
    private Discount.DiscountType discountType;
    private BigDecimal discountValue;
    private Integer timesUsed;
    private BigDecimal totalDiscountGiven;
    private Boolean active;
    private Boolean expired;

    public DiscountUsageDTOBuilder discountId(Long discountId) { this.discountId = discountId; return this; }
    public DiscountUsageDTOBuilder code(String code) { this.code = code; return this; }
    public DiscountUsageDTOBuilder discountType(Discount.DiscountType discountType) { this.discountType = discountType; return this; }
    public DiscountUsageDTOBuilder discountValue(BigDecimal discountValue) { this.discountValue = discountValue; return this; }
    public DiscountUsageDTOBuilder timesUsed(Integer timesUsed) { this.timesUsed = timesUsed; return this; }
    public DiscountUsageDTOBuilder totalDiscountGiven(BigDecimal totalDiscountGiven) { this.totalDiscountGiven = totalDiscountGiven; return this; }
    public DiscountUsageDTOBuilder active(Boolean active) { this.active = active; return this; }
    public DiscountUsageDTOBuilder expired(Boolean expired) { this.expired = expired; return this; }

    public DiscountUsageDTO build() {
        return new DiscountUsageDTO(discountId, code, discountType, discountValue,
                timesUsed, totalDiscountGiven, active, expired);
    }
}
