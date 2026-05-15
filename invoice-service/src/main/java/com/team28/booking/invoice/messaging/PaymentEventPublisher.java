package com.team28.booking.invoice.messaging;

import com.team28.booking.contracts.events.PaymentCompletedEvent;
import com.team28.booking.contracts.events.PaymentFailedEvent;
import com.team28.booking.contracts.events.PaymentInitiatedEvent;
import com.team28.booking.contracts.events.PaymentRefundedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);
    private static final String EXCHANGE = "payment.events";

    private final RabbitTemplate rabbitTemplate;

    public PaymentEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishPaymentInitiated(Long invoiceId, Long bookingId, Double amount) {
        PaymentInitiatedEvent event = new PaymentInitiatedEvent(invoiceId, bookingId, amount);
        rabbitTemplate.convertAndSend(EXCHANGE, "payment.initiated", event);
        log.info("Published payment.initiated: invoiceId={} bookingId={}", invoiceId, bookingId);
    }

    public void publishPaymentCompleted(Long invoiceId, Long bookingId, Double amount) {
        PaymentCompletedEvent event = new PaymentCompletedEvent(invoiceId, bookingId, amount);
        rabbitTemplate.convertAndSend(EXCHANGE, "payment.completed", event);
        log.info("Published payment.completed: invoiceId={} bookingId={}", invoiceId, bookingId);
    }

    public void publishPaymentFailed(Long invoiceId, Long bookingId, String reason) {
        PaymentFailedEvent event = new PaymentFailedEvent(invoiceId, bookingId, reason);
        rabbitTemplate.convertAndSend(EXCHANGE, "payment.failed", event);
        log.info("Published payment.failed: invoiceId={} bookingId={}", invoiceId, bookingId);
    }

    public void publishPaymentRefunded(Long invoiceId, Long bookingId, Double refundAmount) {
        PaymentRefundedEvent event = new PaymentRefundedEvent(invoiceId, bookingId, refundAmount);
        rabbitTemplate.convertAndSend(EXCHANGE, "payment.refunded", event);
        log.info("Published payment.refunded: invoiceId={} bookingId={}", invoiceId, bookingId);
    }
}
