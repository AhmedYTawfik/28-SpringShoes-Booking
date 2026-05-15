package com.team28.booking.invoice.messaging;

import com.team28.booking.contracts.events.BookingCancelledEvent;
import com.team28.booking.contracts.events.BookingCompletedEvent;
import com.team28.booking.invoice.model.Invoice;
import com.team28.booking.invoice.repository.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RabbitListener(queues = "payment.saga-listener")
public class InvoiceBookingEventListener {

    private static final Logger log = LoggerFactory.getLogger(InvoiceBookingEventListener.class);

    private final InvoiceRepository invoiceRepository;
    private final PaymentEventPublisher publisher;

    public InvoiceBookingEventListener(InvoiceRepository invoiceRepository,
                                       PaymentEventPublisher publisher) {
        this.invoiceRepository = invoiceRepository;
        this.publisher = publisher;
    }

    @RabbitHandler
    @Transactional
    public void handleBookingCompleted(BookingCompletedEvent event) {
        log.info("Received booking.completed: bookingId={} userId={}", event.bookingId(), event.userId());

        // Idempotency: skip if invoice already exists for this booking
        if (invoiceRepository.existsByBookingId(event.bookingId())) {
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

        Invoice saved = invoiceRepository.save(invoice);
        log.info("Created invoice id={} for bookingId={}", saved.getId(), event.bookingId());

        publisher.publishPaymentInitiated(saved.getId(), event.bookingId(), amount);
    }

    @RabbitHandler
    @Transactional
    public void handleBookingCancelled(BookingCancelledEvent event) {
        log.info("Received booking.cancelled: bookingId={}", event.bookingId());

        Optional<Invoice> existing = invoiceRepository.findByBookingId(event.bookingId());
        if (existing.isEmpty()) {
            log.info("No invoice found for bookingId={} — nothing to refund", event.bookingId());
            return;
        }

        Invoice invoice = existing.get();
        if (invoice.getStatus() != Invoice.InvoiceStatus.PENDING) {
            log.info("Invoice {} is {} — skipping refund on cancellation", invoice.getId(), invoice.getStatus());
            return;
        }

        invoice.setStatus(Invoice.InvoiceStatus.REFUNDED);
        invoiceRepository.save(invoice);

        double refundAmount = invoice.getAmount() != null ? invoice.getAmount().doubleValue() : 0.0;
        publisher.publishPaymentRefunded(invoice.getId(), event.bookingId(), refundAmount);
    }
}
