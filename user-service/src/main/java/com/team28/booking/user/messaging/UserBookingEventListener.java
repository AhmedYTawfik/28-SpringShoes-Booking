package com.team28.booking.user.messaging;

import com.team28.booking.contracts.events.BookingCancelledEvent;
import com.team28.booking.contracts.events.BookingCompletedEvent;
import com.team28.booking.user.model.User;
import com.team28.booking.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RabbitListener(queues = "user.booking.saga-listener")
public class UserBookingEventListener {

    private static final Logger log = LoggerFactory.getLogger(UserBookingEventListener.class);

    private final UserRepository userRepository;

    public UserBookingEventListener(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @RabbitHandler
    @Transactional
    public void handleBookingCompleted(BookingCompletedEvent event) {
        log.info("user-service received booking.completed: bookingId={} userId={} totalPrice={}",
                event.bookingId(), event.userId(), event.totalPrice());
        userRepository.findById(event.userId()).ifPresentOrElse(user -> {
            Map<String, Object> stats = mutableStats(user);
            List<Long> completed = processedIds(stats, "processedCompletedBookingIds");
            if (completed.contains(event.bookingId())) {
                log.info("booking.completed already applied for bookingId={} - idempotent skip", event.bookingId());
                return;
            }

            completed.add(event.bookingId());
            stats.put("processedCompletedBookingIds", completed);
            stats.put("totalBookings", longValue(stats.get("totalBookings")) + 1L);
            stats.put("completedBookings", longValue(stats.get("completedBookings")) + 1L);
            BigDecimal eventAmount = amount(event);
            stats.put("totalSpent", decimalValue(stats.get("totalSpent")).add(eventAmount).doubleValue());
            Map<String, Double> completedAmounts = completedAmounts(stats);
            completedAmounts.put(String.valueOf(event.bookingId()), eventAmount.doubleValue());
            stats.put("completedAmountsByBookingId", completedAmounts);
            user.setBookingStats(stats);
            userRepository.save(user);
        }, () -> log.warn("User {} not found while applying booking.completed {}", event.userId(), event.bookingId()));
    }

    @RabbitHandler
    @Transactional
    public void handleBookingCancelled(BookingCancelledEvent event) {
        log.info("user-service received booking.cancelled: bookingId={} userId={} reason={}",
                event.bookingId(), event.userId(), event.reason());
        userRepository.findById(event.userId()).ifPresentOrElse(user -> {
            Map<String, Object> stats = mutableStats(user);
            List<Long> cancelled = processedIds(stats, "processedCancelledBookingIds");
            if (cancelled.contains(event.bookingId())) {
                log.info("booking.cancelled already applied for bookingId={} - idempotent skip", event.bookingId());
                return;
            }

            cancelled.add(event.bookingId());
            stats.put("processedCancelledBookingIds", cancelled);
            stats.put("cancelledBookings", longValue(stats.get("cancelledBookings")) + 1L);

            List<Long> completed = processedIds(stats, "processedCompletedBookingIds");
            if (completed.contains(event.bookingId())) {
                stats.put("totalBookings", Math.max(0L, longValue(stats.get("totalBookings")) - 1L));
                stats.put("completedBookings", Math.max(0L, longValue(stats.get("completedBookings")) - 1L));
                Map<String, Double> completedAmounts = completedAmounts(stats);
                Double amount = completedAmounts.remove(String.valueOf(event.bookingId()));
                if (amount != null) {
                    BigDecimal totalSpent = decimalValue(stats.get("totalSpent")).subtract(BigDecimal.valueOf(amount));
                    stats.put("totalSpent", totalSpent.max(BigDecimal.ZERO).doubleValue());
                }
                stats.put("completedAmountsByBookingId", completedAmounts);
            }

            user.setBookingStats(stats);
            userRepository.save(user);
        }, () -> log.warn("User {} not found while applying booking.cancelled {}", event.userId(), event.bookingId()));
    }

    private Map<String, Object> mutableStats(User user) {
        return user.getBookingStats() != null ? new HashMap<>(user.getBookingStats()) : new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<Long> processedIds(Map<String, Object> stats, String key) {
        Object value = stats.get(key);
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

    private BigDecimal decimalValue(Object value) {
        return value instanceof Number number ? BigDecimal.valueOf(number.doubleValue()) : BigDecimal.ZERO;
    }

    private BigDecimal amount(BookingCompletedEvent event) {
        return event.totalPrice() != null ? BigDecimal.valueOf(event.totalPrice()) : BigDecimal.ZERO;
    }

    private Map<String, Double> completedAmounts(Map<String, Object> stats) {
        Object value = stats.get("completedAmountsByBookingId");
        Map<String, Double> amounts = new HashMap<>();
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() instanceof Number number) {
                    amounts.put(String.valueOf(entry.getKey()), number.doubleValue());
                }
            }
        }
        return amounts;
    }
}
