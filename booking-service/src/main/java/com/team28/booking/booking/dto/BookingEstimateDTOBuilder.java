package com.team28.booking.booking.dto;

import java.math.BigDecimal;

public class BookingEstimateDTOBuilder {
    private int totalDuration;
    private BigDecimal basePrice;
    private BigDecimal estimatedPrice;
    private BigDecimal demandMultiplier;

    public BookingEstimateDTOBuilder totalDuration(int totalDuration) { this.totalDuration = totalDuration; return this; }
    public BookingEstimateDTOBuilder basePrice(BigDecimal basePrice) { this.basePrice = basePrice; return this; }
    public BookingEstimateDTOBuilder estimatedPrice(BigDecimal estimatedPrice) { this.estimatedPrice = estimatedPrice; return this; }
    public BookingEstimateDTOBuilder demandMultiplier(BigDecimal demandMultiplier) { this.demandMultiplier = demandMultiplier; return this; }

    public BookingEstimateDTO build() {
        return new BookingEstimateDTO(totalDuration, basePrice, estimatedPrice, demandMultiplier);
    }
}
