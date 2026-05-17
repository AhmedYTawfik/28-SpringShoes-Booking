package com.team28.booking.contracts.feign;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeignFallbackTest {

    @Test
    void fallbacksReturnSafeValuesWhenCircuitBreakerDelegatesToThem() {
        assertThat(new BookingServiceClientFallback().getActiveBookingCount(1L)).isZero();
        assertThat(new InvoiceServiceClientFallback().getUserInvoiceTotal(1L, "2026-01-01", "2026-12-31"))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(new ProviderServiceClientFallback().getProviderAvailability(1L).status()).isEqualTo("UNAVAILABLE");
        assertThat(new UserServiceClientFallback().getUser(1L).status()).isEqualTo("UNAVAILABLE");
        assertThat(new CalendarServiceClientFallback().getProviderUtilization(1L, "2026-01-01", "2026-12-31").totalSlots())
                .isZero();
        assertThat(new InvoiceServiceClientFallback().getInvoiceAmountsByBookings(new com.team28.booking.contracts.dto.InvoiceAmountsRequest(List.of(1L))))
                .isEmpty();
    }
}
