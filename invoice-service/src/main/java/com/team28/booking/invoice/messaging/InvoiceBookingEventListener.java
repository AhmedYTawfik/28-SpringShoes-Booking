package com.team28.booking.invoice.messaging;

import com.team28.booking.contracts.events.BookingCancelledEvent;
import com.team28.booking.contracts.events.BookingCompletedEvent;
import com.team28.booking.contracts.dto.BookingDTO;
import com.team28.booking.contracts.feign.BookingServiceClient;
import com.team28.booking.invoice.model.Invoice;
import com.team28.booking.invoice.repository.InvoiceRepository;
import com.team28.booking.invoice.strategy.RefundResult;
import com.team28.booking.invoice.strategy.RefundStrategy;
import com.team28.booking.invoice.strategy.RefundStrategySelector;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RabbitListener(queues = "payment.saga-listener")
public class InvoiceBookingEventListener {

    private static final Logger log = LoggerFactory.getLogger(InvoiceBookingEventListener.class);

    private final InvoiceRepository invoiceRepository;
    private final PaymentEventPublisher publisher;
    private final BookingServiceClient bookingServiceClient;
    private final RefundStrategySelector refundStrategySelector;

    public InvoiceBookingEventListener(InvoiceRepository invoiceRepository,
                                       PaymentEventPublisher publisher,
                                       BookingServiceClient bookingServiceClient,
                                       RefundStrategySelector refundStrategySelector) {
        this.invoiceRepository = invoiceRepository;
        this.publisher = publisher;
        this.bookingServiceClient = bookingServiceClient;
        this.refundStrategySelector = refundStrategySelector;
    }

    @RabbitHandler
    @Transactional
    public void handleBookingCompleted(BookingCompletedEvent event) {
        log.info("Received booking.completed: bookingId={} userId={}", event.bookingId(), event.userId());

        if (invoiceRepository.findByBookingIdForUpdate(event.bookingId()).isPresent()) {
            log.info("Invoice already exists for bookingId={} — idempotent skip", event.bookingId());
            return;
        }

        double amount = event.totalPrice() != null ? event.totalPrice() : 0.0;

        Invoice invoice = new Invoice();
        invoice.setBookingId(event.bookingId());
        invoice.setUserId(event.userId());
        invoice.setAmount(BigDecimal.valueOf(amount));
        invoice.setMethod(Invoice.PaymentMethod.CASH);
        invoice.setStatus(Invoice.InvoiceStatus.PENDING);
        invoice.setCreatedAt(LocalDateTime.now());

        Invoice saved;
        try {
            saved = invoiceRepository.saveAndFlush(invoice);
        } catch (DataIntegrityViolationException e) {
            log.info("Invoice already inserted concurrently for bookingId={} - idempotent skip", event.bookingId());
            return;
        }
        log.info("Created invoice id={} for bookingId={}", saved.getId(), event.bookingId());

        publishAfterCommit(() -> publisher.publishPaymentInitiated(saved.getId(), event.bookingId(), amount));
    }

    @RabbitHandler
    @Transactional
    public void handleBookingCancelled(BookingCancelledEvent event) {
        log.info("Received booking.cancelled: bookingId={}", event.bookingId());

        Optional<Invoice> existing = invoiceRepository.findByBookingIdForUpdate(event.bookingId());
        if (existing.isEmpty()) {
            log.info("No invoice found for bookingId={} — nothing to refund", event.bookingId());
            return;
        }

        Invoice invoice = existing.get();
        if (invoice.getStatus() == Invoice.InvoiceStatus.REFUNDED) {
            log.info("Invoice {} already REFUNDED — idempotent skip", invoice.getId());
            return;
        }
        if (invoice.getStatus() != Invoice.InvoiceStatus.PENDING
                && invoice.getStatus() != Invoice.InvoiceStatus.COMPLETED) {
            log.info("Invoice {} is {} — skipping refund on cancellation", invoice.getId(), invoice.getStatus());
            return;
        }

        BookingDTO booking = fetchBooking(event.bookingId());
        RefundResult refund = calculateRefund(invoice, booking, event.reason());

        invoice.setStatus(Invoice.InvoiceStatus.REFUNDED);
        Map<String, Object> details = invoice.getTransactionDetails();
        if (details == null) {
            details = new HashMap<>();
        }
        details.put("refundAmount", refund.getRefundAmount());
        details.put("cancellationFee", refund.getCancellationFee());
        details.put("refundReason", event.reason());
        details.put("strategyReason", refund.getReasonCode());
        details.put("refundedAt", LocalDateTime.now().toString());
        invoice.setTransactionDetails(details);

        Invoice saved = invoiceRepository.save(invoice);

        double refundAmount = refund.getRefundAmount() != null ? refund.getRefundAmount().doubleValue() : 0.0;
        publishAfterCommit(() -> publisher.publishPaymentRefunded(saved.getId(), event.bookingId(), refundAmount));
    }

    private BookingDTO fetchBooking(Long bookingId) {
        try {
            return bookingServiceClient.getBooking(bookingId);
        } catch (FeignException.NotFound e) {
            log.warn("Booking {} not found in booking-service while processing refund", bookingId);
            return null;
        } catch (FeignException e) {
            log.error("booking-service unavailable while processing refund for booking {}: {}", bookingId, e.getMessage());
            throw new RuntimeException("booking-service unavailable", e);
        }
    }

    private RefundResult calculateRefund(Invoice invoice, BookingDTO booking, String reason) {
        Map<String, Object> bookingData = new HashMap<>();
        bookingData.put("status", booking != null ? booking.status() : "CONFIRMED");
        bookingData.put("appointmentDate", booking != null ? booking.appointmentDate() : null);
        RefundStrategy strategy = refundStrategySelector.select(bookingData);
        return strategy.calculateRefund(invoice.getAmount(), bookingData, reason);
    }

    private void publishAfterCommit(Runnable publish) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }
}
