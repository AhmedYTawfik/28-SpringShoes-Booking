package com.team28.booking.calendar.messaging;

import com.team28.booking.contracts.events.SlotReleasedEvent;
import com.team28.booking.contracts.events.SlotReservedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class CalendarEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(CalendarEventPublisher.class);

    private static final String EXCHANGE = "calendar.events";

    private final RabbitTemplate rabbitTemplate;

    public CalendarEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishSlotReserved(Long slotId, Long providerId, Long bookingId) {
        SlotReservedEvent event = new SlotReservedEvent(slotId, providerId, bookingId);
        rabbitTemplate.convertAndSend(EXCHANGE, "slot.reserved", event);
        log.info("Published slot.reserved: slotId={} providerId={} bookingId={}", slotId, providerId, bookingId);
    }

    public void publishSlotReleased(Long slotId, Long providerId, Long bookingId) {
        SlotReleasedEvent event = new SlotReleasedEvent(slotId, providerId, bookingId);
        rabbitTemplate.convertAndSend(EXCHANGE, "slot.released", event);
        log.info("Published slot.released: slotId={} providerId={} bookingId={}", slotId, providerId, bookingId);
    }
}
