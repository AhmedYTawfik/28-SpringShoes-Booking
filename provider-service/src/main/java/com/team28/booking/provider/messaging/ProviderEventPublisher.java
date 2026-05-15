package com.team28.booking.provider.messaging;

import com.team28.booking.contracts.events.ProviderCertificationVerifiedEvent;
import com.team28.booking.contracts.events.ProviderRatedEvent;
import com.team28.booking.contracts.events.ProviderStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProviderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProviderEventPublisher.class);
    private static final String EXCHANGE = "provider.events";

    private final RabbitTemplate rabbitTemplate;

    public ProviderEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishStatusChanged(Long providerId, String oldStatus, String newStatus) {
        ProviderStatusChangedEvent event = new ProviderStatusChangedEvent(providerId, oldStatus, newStatus);
        rabbitTemplate.convertAndSend(EXCHANGE, "provider.status-changed", event);
        log.info("Published provider.status-changed: providerId={} {}→{}", providerId, oldStatus, newStatus);
    }

    public void publishProviderRated(Long providerId, Long bookingId, Double rating, Long userId) {
        ProviderRatedEvent event = new ProviderRatedEvent(providerId, bookingId, rating, userId);
        rabbitTemplate.convertAndSend(EXCHANGE, "provider.rated", event);
        log.info("Published provider.rated: providerId={} bookingId={} userId={} rating={}",
                providerId, bookingId, userId, rating);
    }

    public void publishCertificationVerified(Long providerId, Long certificationId, Long verifiedBy) {
        ProviderCertificationVerifiedEvent event = new ProviderCertificationVerifiedEvent(providerId, certificationId, verifiedBy);
        rabbitTemplate.convertAndSend(EXCHANGE, "provider.certification.verified", event);
        log.info("Published provider.certification.verified: providerId={} certId={}", providerId, certificationId);
    }
}
