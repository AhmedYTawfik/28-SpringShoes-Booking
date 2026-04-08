package com.team28.booking.invoice.service;

import com.team28.booking.invoice.dto.RevenueReportDTO;
import com.team28.booking.invoice.exception.BadRequestException;
import com.team28.booking.invoice.repository.InvoiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * [S5-F6] Revenue Report by Date Range — unit tests
 * feat(invoice-service): add S5-F6 revenue report by date range (55-24423)
 */
@ExtendWith(MockitoExtension.class)
class RevenueReportServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @InjectMocks
    private InvoiceService invoiceService;

    private static final LocalDate START = LocalDate.of(2026, 3, 1);
    private static final LocalDate END   = LocalDate.of(2026, 3, 31);

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Build the Object[] that the native query returns */
    private Object[] statsRow(double totalRevenue, long totalInvoices,
                              long completedInvoices, double refundedAmount,
                              double avgAmount) {
        return new Object[]{totalRevenue, totalInvoices, completedInvoices, refundedAmount, avgAmount};
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    void getRevenueReport_validRange_returnsCorrectDTO() {
        when(invoiceRepository.getRevenueStats(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(statsRow(600.0, 4L, 3L, 100.0, 150.0));

        RevenueReportDTO dto = invoiceService.getRevenueReport(START, END);

        assertThat(dto.getStartDate()).isEqualTo(START);
        assertThat(dto.getEndDate()).isEqualTo(END);
        assertThat(dto.getTotalRevenue()).isEqualTo(600.0);
        assertThat(dto.getTotalInvoices()).isEqualTo(4L);
        assertThat(dto.getCompletedInvoices()).isEqualTo(3L);
        assertThat(dto.getRefundedAmount()).isEqualTo(100.0);
        assertThat(dto.getNetRevenue()).isEqualTo(500.0);          // 600 - 100
        assertThat(dto.getAverageInvoiceAmount()).isEqualTo(150.0);
    }

    @Test
    void getRevenueReport_noData_returnsAllZeroes() {
        when(invoiceRepository.getRevenueStats(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(statsRow(0.0, 0L, 0L, 0.0, 0.0));

        RevenueReportDTO dto = invoiceService.getRevenueReport(START, END);

        assertThat(dto.getTotalRevenue()).isZero();
        assertThat(dto.getTotalInvoices()).isZero();
        assertThat(dto.getCompletedInvoices()).isZero();
        assertThat(dto.getRefundedAmount()).isZero();
        assertThat(dto.getNetRevenue()).isZero();
        assertThat(dto.getAverageInvoiceAmount()).isZero();
    }

    @Test
    void getRevenueReport_netRevenue_isTotalMinusRefunded() {
        when(invoiceRepository.getRevenueStats(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(statsRow(1000.0, 5L, 4L, 250.0, 200.0));

        RevenueReportDTO dto = invoiceService.getRevenueReport(START, END);

        assertThat(dto.getNetRevenue()).isEqualTo(750.0);
    }

    @Test
    void getRevenueReport_sameDayRange_isAllowed() {
        LocalDate sameDay = LocalDate.of(2026, 3, 15);
        when(invoiceRepository.getRevenueStats(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(statsRow(100.0, 1L, 1L, 0.0, 100.0));

        RevenueReportDTO dto = invoiceService.getRevenueReport(sameDay, sameDay);

        assertThat(dto.getTotalRevenue()).isEqualTo(100.0);
    }

    // ── null values from DB ───────────────────────────────────────────────────

    @Test
    void getRevenueReport_nullDbValues_defaultToZero() {
        when(invoiceRepository.getRevenueStats(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(new Object[]{null, null, null, null, null});

        RevenueReportDTO dto = invoiceService.getRevenueReport(START, END);

        assertThat(dto.getTotalRevenue()).isZero();
        assertThat(dto.getTotalInvoices()).isZero();
        assertThat(dto.getRefundedAmount()).isZero();
        assertThat(dto.getAverageInvoiceAmount()).isZero();
    }

    // ── invalid date range ────────────────────────────────────────────────────

    @Test
    void getRevenueReport_startAfterEnd_throws400() {
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end   = LocalDate.of(2026, 3, 1);

        assertThatThrownBy(() -> invoiceService.getRevenueReport(start, end))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("startDate");

        verify(invoiceRepository, never()).getRevenueStats(any(), any());
    }
}
