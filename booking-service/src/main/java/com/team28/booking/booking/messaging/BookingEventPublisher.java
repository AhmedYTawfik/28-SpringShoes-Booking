package com.team28.booking.booking.messaging;

import com.team28.booking.contracts.events.BookingCancelledEvent;
import com.team28.booking.contracts.events.BookingCompletedEvent;
import com.team28.booking.contracts.events.BookingPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class BookingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BookingEventPublisher.class);
    private static final String EXCHANGE = "booking.events";

    private final RabbitTemplate rabbitTemplate;

    public BookingEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishBookingPlaced(Long bookingId, Long userId, Long providerId) {
        BookingPlacedEvent event = new BookingPlacedEvent(bookingId, userId, providerId);
        rabbitTemplate.convertAndSend(EXCHANGE, "booking.placed", event);
        log.info("Published booking.placed: bookingId={} userId={} providerId={}", bookingId, userId, providerId);
    }

    public void publishBookingCompleted(Long bookingId, Long userId, Long providerId, Double totalPrice) {
        BookingCompletedEvent event = new BookingCompletedEvent(bookingId, userId, providerId, totalPrice);
        rabbitTemplate.convertAndSend(EXCHANGE, "booking.completed", event);
        log.info("Published booking.completed: bookingId={} userId={} providerId={}", bookingId, userId, providerId);
    }

    public void publishBookingCancelled(Long bookingId, Long userId, Long providerId, String reason) {
        BookingCancelledEvent event = new BookingCancelledEvent(bookingId, userId, providerId, reason);
        rabbitTemplate.convertAndSend(EXCHANGE, "booking.cancelled", event);
        log.info("Published booking.cancelled: bookingId={} userId={} providerId={}", bookingId, userId, providerId);
    }
}
