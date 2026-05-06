package com.team28.booking.invoice.adapter;

import com.team28.booking.invoice.dto.RevenueReportDTO;
import com.team28.booking.invoice.dto.UserInvoiceSummaryDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class ObjectArrayDtoAdapter {

    public UserInvoiceSummaryDTO toUserInvoiceSummaryDTO(Long userId, List<Object[]> rows) {
        Map<String, BigDecimal> methodBreakdown = new java.util.HashMap<>();
        int totalInvoices = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (Object[] row : rows) {
            String method = (String) row[0];
            long count = ((Number) row[1]).longValue();
            BigDecimal amount = row[2] != null ? new BigDecimal(row[2].toString()) : BigDecimal.ZERO;
            methodBreakdown.put(method, amount);
            totalInvoices += count;
            totalAmount = totalAmount.add(amount);
        }

        return new UserInvoiceSummaryDTO(userId, totalInvoices, totalAmount, methodBreakdown);
    }

    public RevenueReportDTO toRevenueReportDTO(LocalDate startDate, LocalDate endDate, Object[] row) {
        BigDecimal totalRevenue = row[0] != null ? new BigDecimal(row[0].toString()) : BigDecimal.ZERO;
        long totalTransactions = row[1] != null ? ((Number) row[1]).longValue() : 0L;
        BigDecimal refundedAmount = row[2] != null ? new BigDecimal(row[2].toString()) : BigDecimal.ZERO;
        long refundCount = row[3] != null ? ((Number) row[3]).longValue() : 0L;
        BigDecimal averageInvoice = row[4] != null ? new BigDecimal(row[4].toString()) : BigDecimal.ZERO;
        BigDecimal netRevenue = totalRevenue.subtract(refundedAmount);
        return new RevenueReportDTO(startDate, endDate, totalRevenue, totalTransactions,
                refundedAmount, refundCount, netRevenue, averageInvoice);
    }
}
