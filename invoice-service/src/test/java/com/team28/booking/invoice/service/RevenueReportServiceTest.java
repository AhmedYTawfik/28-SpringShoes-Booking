package com.team28.booking.invoice.service;

import com.team28.booking.invoice.cache.CacheInvalidator;
import com.team28.booking.invoice.adapter.ObjectArrayDtoAdapter;
import com.team28.booking.invoice.dto.RevenueReportDTO;
import com.team28.booking.invoice.exception.BadRequestException;
import com.team28.booking.invoice.repository.InvoiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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

    @Mock
    private CacheInvalidator cacheInvalidator;

    @Spy
    private ObjectArrayDtoAdapter objectArrayDtoAdapter = new ObjectArrayDtoAdapter();

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

        assertThat(dto.startDate()).isEqualTo(START);
        assertThat(dto.endDate()).isEqualTo(END);
        assertThat(dto.totalRevenue()).isEqualByComparingTo(BigDecimal.valueOf(600.0));
        assertThat(dto.totalInvoices()).isEqualTo(4L);
        assertThat(dto.completedInvoices()).isEqualTo(3L);
        assertThat(dto.refundedAmount()).isEqualByComparingTo(BigDecimal.valueOf(100.0));
        assertThat(dto.netRevenue()).isEqualByComparingTo(BigDecimal.valueOf(500.0));          // 600 - 100
        assertThat(dto.averageInvoiceAmount()).isEqualByComparingTo(BigDecimal.valueOf(150.0));
    }

    @Test
    void getRevenueReport_noData_returnsAllZeroes() {
        when(invoiceRepository.getRevenueStats(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(statsRow(0.0, 0L, 0L, 0.0, 0.0));

        RevenueReportDTO dto = invoiceService.getRevenueReport(START, END);

        assertThat(dto.totalRevenue()).isZero();
        assertThat(dto.totalInvoices()).isZero();
        assertThat(dto.completedInvoices()).isZero();
        assertThat(dto.refundedAmount()).isZero();
        assertThat(dto.netRevenue()).isZero();
        assertThat(dto.averageInvoiceAmount()).isZero();
    }

    @Test
    void getRevenueReport_netRevenue_isTotalMinusRefunded() {
        when(invoiceRepository.getRevenueStats(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(statsRow(1000.0, 5L, 4L, 250.0, 200.0));

        RevenueReportDTO dto = invoiceService.getRevenueReport(START, END);

        assertThat(dto.netRevenue()).isEqualByComparingTo(BigDecimal.valueOf(750.0));
    }

    @Test
    void getRevenueReport_sameDayRange_isAllowed() {
        LocalDate sameDay = LocalDate.of(2026, 3, 15);
        when(invoiceRepository.getRevenueStats(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(statsRow(100.0, 1L, 1L, 0.0, 100.0));

        RevenueReportDTO dto = invoiceService.getRevenueReport(sameDay, sameDay);

        assertThat(dto.totalRevenue()).isEqualByComparingTo(BigDecimal.valueOf(100.0));
    }

    // ── null values from DB ───────────────────────────────────────────────────

    @Test
    void getRevenueReport_nullDbValues_defaultToZero() {
        when(invoiceRepository.getRevenueStats(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(new Object[]{null, null, null, null, null});

        RevenueReportDTO dto = invoiceService.getRevenueReport(START, END);

        assertThat(dto.totalRevenue()).isZero();
        assertThat(dto.totalInvoices()).isZero();
        assertThat(dto.refundedAmount()).isZero();
        assertThat(dto.averageInvoiceAmount()).isZero();
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
