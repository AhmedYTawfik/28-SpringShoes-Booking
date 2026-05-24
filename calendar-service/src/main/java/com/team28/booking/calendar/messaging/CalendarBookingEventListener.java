package com.team28.booking.calendar.messaging;

import com.team28.booking.contracts.dto.BookingDTO;
import com.team28.booking.contracts.events.BookingCancelledEvent;
import com.team28.booking.contracts.events.BookingCompletedEvent;
import com.team28.booking.contracts.events.BookingPlacedEvent;
import com.team28.booking.contracts.feign.BookingServiceClient;
import com.team28.booking.calendar.repository.TimeSlotRepository;
import com.team28.booking.calendar.service.TimeSlotService;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RabbitListener(queues = "calendar.booking.saga-listener")
public class CalendarBookingEventListener {

    private static final Logger log = LoggerFactory.getLogger(CalendarBookingEventListener.class);

    private final TimeSlotRepository timeSlotRepository;
    private final BookingServiceClient bookingServiceClient;
    private final CalendarEventPublisher publisher;
    private final TimeSlotService timeSlotService;

    public CalendarBookingEventListener(TimeSlotRepository timeSlotRepository,
                                        BookingServiceClient bookingServiceClient,
                                        CalendarEventPublisher publisher,
                                        TimeSlotService timeSlotService) {
        this.timeSlotRepository = timeSlotRepository;
        this.bookingServiceClient = bookingServiceClient;
        this.publisher = publisher;
        this.timeSlotService = timeSlotService;
    }

    @RabbitHandler
    public void handleBookingPlaced(BookingPlacedEvent event) {
        log.info("Received booking.placed: bookingId={} providerId={}", event.bookingId(), event.providerId());

        BookingDTO booking = fetchBooking(event.bookingId());
        if (booking == null) return;

        // Idempotency guard: atomic UPDATE WHERE available = true
        int updated = timeSlotRepository.reserveSlot(
                event.providerId(),
                booking.appointmentDate(),
                booking.startTime()
        );

        if (updated == 0) {
            log.info("Slot already reserved or not found — idempotent skip: bookingId={}", event.bookingId());
            return;
        }

        timeSlotRepository.findByProviderIdAndDateAndStartTimeLessThanEqualAndEndTimeGreaterThan(
                event.providerId(), booking.appointmentDate(), booking.startTime(), booking.startTime()
        ).ifPresent(slot -> publisher.publishSlotReserved(slot.getId(), event.providerId(), event.bookingId()));
    }

    @RabbitHandler
    public void handleBookingCancelled(BookingCancelledEvent event) {
        log.info("Received booking.cancelled: bookingId={} providerId={}", event.bookingId(), event.providerId());

        BookingDTO booking = fetchBooking(event.bookingId());
        if (booking == null) return;

        // Idempotency guard: atomic UPDATE WHERE available = false
        int updated = timeSlotRepository.releaseSlot(
                event.providerId(),
                booking.appointmentDate(),
                booking.startTime()
        );

        if (updated == 0) {
            log.info("Slot already available or not found — idempotent skip: bookingId={}", event.bookingId());
            return;
        }

        timeSlotRepository.findByProviderIdAndDateAndStartTimeLessThanEqualAndEndTimeGreaterThan(
                event.providerId(), booking.appointmentDate(), booking.startTime(), booking.startTime()
        ).ifPresent(slot -> publisher.publishSlotReleased(slot.getId(), event.providerId(), event.bookingId()));
    }

    @RabbitHandler
    public void handleBookingCompleted(BookingCompletedEvent event) {
        log.info("Received booking.completed: bookingId={} providerId={} - recording calendar audit event",
                event.bookingId(), event.providerId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("bookingId", event.bookingId());
        payload.put("userId", event.userId());
        payload.put("providerId", event.providerId());
        payload.put("totalPrice", event.totalPrice());
        timeSlotService.fireEvent("TRIP_COMPLETED", payload);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private BookingDTO fetchBooking(Long bookingId) {
        try {
            return bookingServiceClient.getBooking(bookingId);
        } catch (FeignException.NotFound e) {
            log.warn("Booking {} not found in booking-service — skipping slot update", bookingId);
            return null;
        } catch (FeignException e) {
            log.error("booking-service unavailable while fetching booking {}: {}", bookingId, e.getMessage());
            // Re-throw so AMQP retries up to max-attempts, then routes to DLQ
            throw new RuntimeException("booking-service unavailable", e);
        }
    }
}
