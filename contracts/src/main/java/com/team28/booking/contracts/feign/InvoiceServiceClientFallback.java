package com.team28.booking.contracts.feign;

import com.team28.booking.contracts.dto.InvoiceAmountDTO;
import com.team28.booking.contracts.dto.InvoiceAmountsRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class InvoiceServiceClientFallback implements InvoiceServiceClient {
    @Override
    public BigDecimal getUserInvoiceTotal(Long userId, String startDate, String endDate) {
        return BigDecimal.ZERO;
    }

    @Override
    public Map<Long, InvoiceAmountDTO> getInvoiceAmountsByBookings(InvoiceAmountsRequest body) {
        return Map.of();
    }
}
