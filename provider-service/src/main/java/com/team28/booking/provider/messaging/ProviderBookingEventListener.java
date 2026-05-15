package com.team28.booking.provider.messaging;

import com.team28.booking.contracts.events.BookingCancelledEvent;
import com.team28.booking.contracts.events.BookingCompletedEvent;
import com.team28.booking.contracts.events.BookingPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
@RabbitListener(queues = "provider.booking.saga-listener")
public class ProviderBookingEventListener {

    private static final Logger log = LoggerFactory.getLogger(ProviderBookingEventListener.class);

    @RabbitHandler
    public void handleBookingPlaced(BookingPlacedEvent event) {
        log.info("provider-service observed booking.placed: bookingId={} providerId={}",
                event.bookingId(), event.providerId());
    }

    @RabbitHandler
    public void handleBookingCompleted(BookingCompletedEvent event) {
        log.info("provider-service observed booking.completed: bookingId={} providerId={} totalPrice={}",
                event.bookingId(), event.providerId(), event.totalPrice());
    }

    @RabbitHandler
    public void handleBookingCancelled(BookingCancelledEvent event) {
        log.info("provider-service observed booking.cancelled: bookingId={} providerId={} reason={}",
                event.bookingId(), event.providerId(), event.reason());
    }
}
