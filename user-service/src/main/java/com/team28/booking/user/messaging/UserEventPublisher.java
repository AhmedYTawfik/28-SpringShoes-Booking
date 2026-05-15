package com.team28.booking.user.messaging;

import com.team28.booking.contracts.events.UserDeactivatedEvent;
import com.team28.booking.contracts.events.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);
    private static final String EXCHANGE = "user.events";

    private final RabbitTemplate rabbitTemplate;

    public UserEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishUserRegistered(Long userId, String email, String role) {
        UserRegisteredEvent event = new UserRegisteredEvent(userId, email, role);
        rabbitTemplate.convertAndSend(EXCHANGE, "user.registered", event);
        log.info("Published user.registered: userId={} email={}", userId, email);
    }

    public void publishUserDeactivated(Long userId) {
        UserDeactivatedEvent event = new UserDeactivatedEvent(userId);
        rabbitTemplate.convertAndSend(EXCHANGE, "user.deactivated", event);
        log.info("Published user.deactivated: userId={}", userId);
    }
}
