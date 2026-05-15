package com.team28.booking.contracts.feign;

import com.team28.booking.contracts.dto.InvoiceAmountDTO;
import com.team28.booking.contracts.dto.InvoiceAmountsRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.Map;

@FeignClient(name = "invoice-service", url = "${feign.invoice-service.url}")
public interface InvoiceServiceClient {
    @GetMapping("/api/invoices/user/{userId}/total")
    BigDecimal getUserInvoiceTotal(
            @PathVariable("userId") Long userId,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate
    );

    @PostMapping("/api/invoices/by-bookings")
    Map<Long, InvoiceAmountDTO> getInvoiceAmountsByBookings(@RequestBody InvoiceAmountsRequest body);
}
