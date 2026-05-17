package com.team28.booking.invoice.service;

import com.team28.booking.invoice.cache.CacheInvalidator;
import com.team28.booking.invoice.dto.RetryInvoiceRequest;
import com.team28.booking.invoice.exception.BadRequestException;
import com.team28.booking.invoice.exception.ResourceNotFoundException;
import com.team28.booking.invoice.messaging.PaymentEventPublisher;
import com.team28.booking.invoice.model.Invoice;
import com.team28.booking.invoice.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * [S5-F7] Retry Failed Invoice — unit tests
 * feat(invoice-service): add S5-F7 retry failed invoice (55-24423)
 */
@ExtendWith(MockitoExtension.class)
class RetryInvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private CacheInvalidator cacheInvalidator;

    @Mock
    private PaymentEventPublisher eventPublisher;

    @InjectMocks
    private InvoiceService invoiceService;

    private Invoice failedInvoice;

    @BeforeEach
    void setUp() {
        failedInvoice = new Invoice();
        failedInvoice.setStatus(Invoice.InvoiceStatus.FAILED);
        failedInvoice.setMethod(Invoice.PaymentMethod.CREDIT_CARD);
        failedInvoice.setAmount(BigDecimal.valueOf(200.0));
        failedInvoice.setCreatedAt(LocalDateTime.now());

        Map<String, Object> details = new HashMap<>();
        details.put("failureReason", "Insufficient funds");
        failedInvoice.setTransactionDetails(details);
    }

    private RetryInvoiceRequest requestWithMethod(String method) {
        RetryInvoiceRequest req = new RetryInvoiceRequest();
        req.setMethod(method);
        return req;
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    void retryInvoice_failedInvoice_returnsCompleted() {
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(failedInvoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));

        Invoice result = invoiceService.retryFailedInvoice(1L, requestWithMethod("WALLET"));

        assertThat(result.getStatus()).isEqualTo(Invoice.InvoiceStatus.COMPLETED);
        assertThat(result.getMethod()).isEqualTo(Invoice.PaymentMethod.WALLET);
        verify(invoiceRepository).save(failedInvoice);
    }

    @Test
    void retryInvoice_addsRetryAtAndCompletedAtToTransactionDetails() {
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(failedInvoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));

        Invoice result = invoiceService.retryFailedInvoice(1L, requestWithMethod(null));

        assertThat(result.getTransactionDetails()).containsKey("retryAt");
        assertThat(result.getTransactionDetails()).containsKey("completedAt");
    }

    @Test
    void retryInvoice_retainsExistingTransactionDetails() {
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(failedInvoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));

        Invoice result = invoiceService.retryFailedInvoice(1L, requestWithMethod(null));

        assertThat(result.getTransactionDetails()).containsKey("failureReason");
        assertThat(result.getTransactionDetails().get("failureReason")).isEqualTo("Insufficient funds");
    }

    @Test
    void retryInvoice_setsRetryCountToOne_onFirstRetry() {
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(failedInvoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));

        Invoice result = invoiceService.retryFailedInvoice(1L, requestWithMethod(null));

        assertThat(result.getTransactionDetails().get("retryCount")).isEqualTo(1);
    }

    @Test
    void retryInvoice_incrementsRetryCount_onSubsequentRetry() {
        // Simulate invoice that was already retried once but failed again
        failedInvoice.getTransactionDetails().put("retryCount", 1);
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(failedInvoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));

        Invoice result = invoiceService.retryFailedInvoice(1L, requestWithMethod(null));

        assertThat(result.getTransactionDetails().get("retryCount")).isEqualTo(2);
    }

    @Test
    void retryInvoice_noMethodProvided_keepsOriginalMethod() {
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(failedInvoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));

        Invoice result = invoiceService.retryFailedInvoice(1L, requestWithMethod(null));

        assertThat(result.getMethod()).isEqualTo(Invoice.PaymentMethod.CREDIT_CARD);
    }

    @Test
    void retryInvoice_emptyMethodProvided_keepsOriginalMethod() {
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(failedInvoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));

        Invoice result = invoiceService.retryFailedInvoice(1L, requestWithMethod("  "));

        assertThat(result.getMethod()).isEqualTo(Invoice.PaymentMethod.CREDIT_CARD);
    }

    // ── invoice not found ─────────────────────────────────────────────────────

    @Test
    void retryInvoice_invoiceNotFound_throws404() {
        when(invoiceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.retryFailedInvoice(99L, requestWithMethod(null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");

        verify(invoiceRepository, never()).save(any());
    }

    // ── wrong status ──────────────────────────────────────────────────────────

    @Test
    void retryInvoice_completedInvoice_throws400() {
        failedInvoice.setStatus(Invoice.InvoiceStatus.COMPLETED);
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(failedInvoice));

        assertThatThrownBy(() -> invoiceService.retryFailedInvoice(1L, requestWithMethod(null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("FAILED");

        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void retryInvoice_pendingInvoice_throws400() {
        failedInvoice.setStatus(Invoice.InvoiceStatus.PENDING);
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(failedInvoice));

        assertThatThrownBy(() -> invoiceService.retryFailedInvoice(1L, requestWithMethod(null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void retryInvoice_refundedInvoice_throws400() {
        failedInvoice.setStatus(Invoice.InvoiceStatus.REFUNDED);
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(failedInvoice));

        assertThatThrownBy(() -> invoiceService.retryFailedInvoice(1L, requestWithMethod(null)))
                .isInstanceOf(BadRequestException.class);
    }

    // ── null transactionDetails ───────────────────────────────────────────────

    @Test
    void retryInvoice_nullTransactionDetails_createsNewMap() {
        failedInvoice.setTransactionDetails(null);
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(failedInvoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));

        Invoice result = invoiceService.retryFailedInvoice(1L, requestWithMethod(null));

        assertThat(result.getTransactionDetails()).isNotNull();
        assertThat(result.getTransactionDetails()).containsKey("retryAt");
    }
}
