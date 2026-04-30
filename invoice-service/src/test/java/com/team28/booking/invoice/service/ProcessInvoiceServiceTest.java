package com.team28.booking.invoice.service;

import com.team28.booking.invoice.cache.CacheInvalidator;
import com.team28.booking.invoice.dto.ProcessInvoiceRequest;
import com.team28.booking.invoice.exception.BadRequestException;
import com.team28.booking.invoice.exception.ResourceNotFoundException;
import com.team28.booking.invoice.model.Invoice;
import com.team28.booking.invoice.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * [S5-F4] Process Invoice for Booking — unit tests
 * feat(invoice-service): add S5-F4 process invoice for booking (55-24423)
 */
@ExtendWith(MockitoExtension.class)
class ProcessInvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private CacheInvalidator cacheInvalidator;

    @InjectMocks
    private InvoiceService invoiceService;

    private ProcessInvoiceRequest request;

    @BeforeEach
    void setUp() {
        request = new ProcessInvoiceRequest();
        request.setBookingId(1L);
        request.setUserId(10L);
        request.setMethod("CREDIT_CARD");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Object[] bookingRow(String status, double totalPrice) {
        return new Object[]{status, totalPrice};
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    void processInvoice_completedBooking_createsAndReturnsCompletedInvoice() {
        when(invoiceRepository.findBookingDetails(1L))
                .thenReturn(List.<Object[]>of(bookingRow("COMPLETED", 450.0)));
        when(invoiceRepository.existsByBookingId(1L)).thenReturn(false);
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));

        Invoice result = invoiceService.processInvoiceForBooking(request);

        assertThat(result.getStatus()).isEqualTo(Invoice.InvoiceStatus.COMPLETED);
        assertThat(result.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(450.0));
        assertThat(result.getBookingId()).isEqualTo(1L);
        assertThat(result.getUserId()).isEqualTo(10L);
        assertThat(result.getMethod()).isEqualTo(Invoice.PaymentMethod.CREDIT_CARD);
        assertThat(result.getTransactionDetails()).containsKey("completedAt");
        assertThat(result.getTransactionDetails()).containsKey("gateway");
        assertThat(result.getTransactionDetails()).containsEntry("cancellationFee", 0);
        verify(invoiceRepository).save(any(Invoice.class));
    }

    @Test
    void processInvoice_usesBookingTotalPriceAsAmount() {
        when(invoiceRepository.findBookingDetails(1L))
                .thenReturn(List.<Object[]>of(bookingRow("COMPLETED", 999.99)));
        when(invoiceRepository.existsByBookingId(1L)).thenReturn(false);
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));

        Invoice result = invoiceService.processInvoiceForBooking(request);

        assertThat(result.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(999.99));
    }

    // ── booking not found ─────────────────────────────────────────────────────

    @Test
    void processInvoice_bookingNotFound_throws404() {
        when(invoiceRepository.findBookingDetails(1L)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> invoiceService.processInvoiceForBooking(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Booking not found");

        verify(invoiceRepository, never()).save(any());
    }

    // ── booking not completed ─────────────────────────────────────────────────

    @Test
    void processInvoice_bookingNotCompleted_throws400() {
        when(invoiceRepository.findBookingDetails(1L))
                .thenReturn(List.<Object[]>of(bookingRow("REQUESTED", 200.0)));

        assertThatThrownBy(() -> invoiceService.processInvoiceForBooking(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("COMPLETED");

        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void processInvoice_bookingInProgress_throws400() {
        when(invoiceRepository.findBookingDetails(1L))
                .thenReturn(List.<Object[]>of(bookingRow("IN_PROGRESS", 300.0)));

        assertThatThrownBy(() -> invoiceService.processInvoiceForBooking(request))
                .isInstanceOf(BadRequestException.class);
    }

    // ── invoice already exists ────────────────────────────────────────────────

    @Test
    void processInvoice_invoiceAlreadyExists_throws400() {
        when(invoiceRepository.findBookingDetails(1L))
                .thenReturn(List.<Object[]>of(bookingRow("COMPLETED", 450.0)));
        when(invoiceRepository.existsByBookingId(1L)).thenReturn(true);

        assertThatThrownBy(() -> invoiceService.processInvoiceForBooking(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");

        verify(invoiceRepository, never()).save(any());
    }

    // ── payment method ────────────────────────────────────────────────────────

    @Test
    void processInvoice_walletMethod_setsCorrectMethod() {
        request.setMethod("WALLET");
        when(invoiceRepository.findBookingDetails(1L))
                .thenReturn(List.<Object[]>of(bookingRow("COMPLETED", 100.0)));
        when(invoiceRepository.existsByBookingId(1L)).thenReturn(false);
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));

        Invoice result = invoiceService.processInvoiceForBooking(request);

        assertThat(result.getMethod()).isEqualTo(Invoice.PaymentMethod.WALLET);
    }

    // ── saved entity fields ───────────────────────────────────────────────────

    @Test
    void processInvoice_savedInvoiceHasCreatedAt() {
        when(invoiceRepository.findBookingDetails(1L))
                .thenReturn(List.<Object[]>of(bookingRow("COMPLETED", 200.0)));
        when(invoiceRepository.existsByBookingId(1L)).thenReturn(false);

        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        when(invoiceRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

        invoiceService.processInvoiceForBooking(request);

        Invoice saved = captor.getValue();
        assertThat(saved.getCreatedAt()).isNotNull();
    }
}
