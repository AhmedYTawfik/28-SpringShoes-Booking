package com.team28.booking.user.messaging;

import com.team28.booking.contracts.events.BookingCancelledEvent;
import com.team28.booking.contracts.events.BookingCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
@RabbitListener(queues = "user.booking.saga-listener")
public class UserBookingEventListener {

    private static final Logger log = LoggerFactory.getLogger(UserBookingEventListener.class);

    @RabbitHandler
    public void handleBookingCompleted(BookingCompletedEvent event) {
        log.info("user-service observed booking.completed: bookingId={} userId={} totalPrice={}",
                event.bookingId(), event.userId(), event.totalPrice());
    }

    @RabbitHandler
    public void handleBookingCancelled(BookingCancelledEvent event) {
        log.info("user-service observed booking.cancelled: bookingId={} userId={} reason={}",
                event.bookingId(), event.userId(), event.reason());
    }
}
