package com.team28.booking.provider.messaging;

import com.team28.booking.contracts.events.BookingCancelledEvent;
import com.team28.booking.contracts.events.BookingCompletedEvent;
import com.team28.booking.contracts.events.BookingPlacedEvent;
import com.team28.booking.provider.model.Provider;
import com.team28.booking.provider.repository.ProviderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RabbitListener(queues = "provider.booking.saga-listener")
public class ProviderBookingEventListener {

    private static final Logger log = LoggerFactory.getLogger(ProviderBookingEventListener.class);

    private final ProviderRepository providerRepository;

    public ProviderBookingEventListener(ProviderRepository providerRepository) {
        this.providerRepository = providerRepository;
    }

    @RabbitHandler
    @Transactional
    public void handleBookingPlaced(BookingPlacedEvent event) {
        log.info("provider-service received booking.placed: bookingId={} providerId={}",
                event.bookingId(), event.providerId());
        providerRepository.findById(event.providerId()).ifPresentOrElse(provider -> {
            Map<String, Object> details = mutableDetails(provider);
            List<Long> placed = processedIds(details, "processedPlacedBookingIds");
            if (placed.contains(event.bookingId())) {
                log.info("booking.placed already applied for bookingId={} - idempotent skip", event.bookingId());
                return;
            }
            placed.add(event.bookingId());
            details.put("processedPlacedBookingIds", placed);
            provider.setServiceDetails(details);
            provider.setStatus(Provider.ProviderStatus.BUSY);
            providerRepository.save(provider);
        }, () -> log.warn("Provider {} not found while applying booking.placed {}", event.providerId(), event.bookingId()));
    }

    @RabbitHandler
    @Transactional
    public void handleBookingCompleted(BookingCompletedEvent event) {
        log.info("provider-service received booking.completed: bookingId={} providerId={} totalPrice={}",
                event.bookingId(), event.providerId(), event.totalPrice());
        providerRepository.findById(event.providerId()).ifPresentOrElse(provider -> {
            Map<String, Object> details = mutableDetails(provider);
            List<Long> completed = processedIds(details, "processedCompletedBookingIds");
            if (!completed.contains(event.bookingId())) {
                completed.add(event.bookingId());
                details.put("processedCompletedBookingIds", completed);
                details.put("completedBookings", longValue(details.get("completedBookings")) + 1L);
            }
            provider.setServiceDetails(details);
            provider.setStatus(Provider.ProviderStatus.AVAILABLE);
            providerRepository.save(provider);
        }, () -> log.warn("Provider {} not found while applying booking.completed {}", event.providerId(), event.bookingId()));
    }

    @RabbitHandler
    @Transactional
    public void handleBookingCancelled(BookingCancelledEvent event) {
        log.info("provider-service received booking.cancelled: bookingId={} providerId={} reason={}",
                event.bookingId(), event.providerId(), event.reason());
        providerRepository.findById(event.providerId()).ifPresentOrElse(provider -> {
            Map<String, Object> details = mutableDetails(provider);
            List<Long> cancelled = processedIds(details, "processedCancelledBookingIds");
            if (cancelled.contains(event.bookingId())) {
                log.info("booking.cancelled already applied for bookingId={} - idempotent skip", event.bookingId());
                return;
            }

            cancelled.add(event.bookingId());
            details.put("processedCancelledBookingIds", cancelled);
            List<Long> completed = processedIds(details, "processedCompletedBookingIds");
            if (completed.contains(event.bookingId())) {
                details.put("completedBookings", Math.max(0L, longValue(details.get("completedBookings")) - 1L));
            }

            provider.setServiceDetails(details);
            provider.setStatus(Provider.ProviderStatus.AVAILABLE);
            providerRepository.save(provider);
        }, () -> log.warn("Provider {} not found while applying booking.cancelled {}", event.providerId(), event.bookingId()));
    }

    private Map<String, Object> mutableDetails(Provider provider) {
        return provider.getServiceDetails() != null ? new HashMap<>(provider.getServiceDetails()) : new HashMap<>();
    }

    private List<Long> processedIds(Map<String, Object> details, String key) {
        Object value = details.get(key);
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Long> ids = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Number number) {
                ids.add(number.longValue());
            }
        }
        return ids;
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
