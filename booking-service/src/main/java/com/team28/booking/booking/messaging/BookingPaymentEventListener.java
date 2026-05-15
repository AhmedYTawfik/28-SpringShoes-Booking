package com.team28.booking.booking.messaging;

import com.team28.booking.booking.repository.BookingRepository;
import com.team28.booking.contracts.events.PaymentCompletedEvent;
import com.team28.booking.contracts.events.PaymentFailedEvent;
import com.team28.booking.contracts.events.PaymentInitiatedEvent;
import com.team28.booking.contracts.events.PaymentRefundedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RabbitListener(queues = "booking.saga-feedback")
public class BookingPaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(BookingPaymentEventListener.class);

    private final BookingRepository bookingRepository;
    private final BookingEventPublisher publisher;

    public BookingPaymentEventListener(BookingRepository bookingRepository,
                                       BookingEventPublisher publisher) {
        this.bookingRepository = bookingRepository;
        this.publisher = publisher;
    }

    @RabbitHandler
    @Transactional
    public void handlePaymentInitiated(PaymentInitiatedEvent event) {
        log.info("Received payment.initiated: invoiceId={} bookingId={}", event.invoiceId(), event.bookingId());
        int updated = bookingRepository.updateStatusToPaymentPending(event.bookingId());
        if (updated == 0) {
            log.info("Booking {} already past PAYMENT_PENDING — idempotent skip", event.bookingId());
        }
    }

    @RabbitHandler
    @Transactional
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("Received payment.completed: invoiceId={} bookingId={}", event.invoiceId(), event.bookingId());
        int updated = bookingRepository.updateStatusToPaid(event.bookingId());
        if (updated == 0) {
            log.info("Booking {} already past PAID — idempotent skip", event.bookingId());
        }
    }

    @RabbitHandler
    @Transactional
    public void handlePaymentFailed(PaymentFailedEvent event) {
        log.info("Received payment.failed: invoiceId={} bookingId={} reason={}", event.invoiceId(), event.bookingId(), event.reason());
        int updated = bookingRepository.updateStatusToPaymentFailed(event.bookingId());
        if (updated == 0) {
            log.info("Booking {} already past PAYMENT_FAILED — idempotent skip", event.bookingId());
            return;
        }

        bookingRepository.findById(event.bookingId()).ifPresent(booking ->
                publishAfterCommit(() -> publisher.publishBookingCancelled(
                        booking.getId(),
                        booking.getUserId(),
                        booking.getProviderId(),
                        event.reason() != null ? event.reason() : "payment failed"
                )));
    }

    @RabbitHandler
    @Transactional
    public void handlePaymentRefunded(PaymentRefundedEvent event) {
        log.info("Received payment.refunded: invoiceId={} bookingId={}", event.invoiceId(), event.bookingId());
        int updated = bookingRepository.updateStatusToRefunded(event.bookingId());
        if (updated == 0) {
            log.info("Booking {} already REFUNDED — idempotent skip", event.bookingId());
        }
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
