package com.team28.booking.invoice.strategy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RefundStrategySelector {

    public RefundStrategy select(Map<String, Object> bookingData) {
        String status = (String) bookingData.get("status");
        LocalDate appointmentDate = (LocalDate) bookingData.get("appointmentDate");

        if (!("REQUESTED".equals(status) || "CONFIRMED".equals(status))) {
            return new NoRefundStrategy();
        }

        long hoursUntil = appointmentDate != null
                ? ChronoUnit.HOURS.between(LocalDateTime.now(), appointmentDate.atStartOfDay())
                : 0;

        if (hoursUntil > 24) {
            return new FullRefundStrategy();
        }
        return new PartialRefundStrategy();
    }
}
