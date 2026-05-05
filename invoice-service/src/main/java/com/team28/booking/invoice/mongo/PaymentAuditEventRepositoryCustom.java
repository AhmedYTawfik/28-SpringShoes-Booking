package com.team28.booking.invoice.mongo;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentAuditEventRepositoryCustom {
    List<PaymentMethodBreakdown> findMethodBreakdown(LocalDateTime start, LocalDateTime end, List<String> actions);
}
