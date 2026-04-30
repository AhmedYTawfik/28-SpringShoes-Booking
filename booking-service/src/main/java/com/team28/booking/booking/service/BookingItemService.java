package com.team28.booking.booking.service;

import com.team28.booking.booking.cache.CacheInvalidator;
import com.team28.booking.booking.model.Booking;
import com.team28.booking.booking.model.BookingItem;
import com.team28.booking.booking.repository.BookingItemRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class BookingItemService {

    private final BookingItemRepository bookingItemRepository;
    private final BookingService bookingService;
    private final CacheInvalidator cacheInvalidator;

    public BookingItemService(BookingItemRepository bookingItemRepository,
                               BookingService bookingService,
                               CacheInvalidator cacheInvalidator) {
        this.bookingItemRepository = bookingItemRepository;
        this.bookingService = bookingService;
        this.cacheInvalidator = cacheInvalidator;
    }

    // ── writes ───────────────────────────────────────────────────────────────

    public BookingItem createBookingItem(Long bookingId, BookingItem bookingItem) {
        Booking booking = bookingService.findById(bookingId);
        bookingItem.setId(null);
        bookingItem.setBooking(booking);
        BookingItem saved = bookingItemRepository.save(bookingItem);
        // invalidate parent booking detail + analytics (§4.4.4)
        cacheInvalidator.deleteKey("booking-service::booking::" + bookingId);
        cacheInvalidator.wildcardDelete("booking-service::S3-F10::*");
        bookingService.emitServicesAdded(booking, saved.getId());
        return saved;
    }

    public BookingItem updateBookingItem(Long id, BookingItem updated) {
        BookingItem existing = findById(id);

        if (updated.getServiceOrder() != null) existing.setServiceOrder(updated.getServiceOrder());
        if (updated.getServiceName() != null) existing.setServiceName(updated.getServiceName());
        if (updated.getDuration() != null) existing.setDuration(updated.getDuration());
        if (updated.getPrice() != null) existing.setPrice(updated.getPrice());
        if (updated.getStatus() != null) existing.setStatus(updated.getStatus());
        if (updated.getMetadata() != null) existing.setMetadata(updated.getMetadata());

        BookingItem saved = bookingItemRepository.save(existing);
        // invalidate entity detail + analytics (§4.4.4)
        cacheInvalidator.deleteKey("booking-service::booking-item::" + id);
        cacheInvalidator.wildcardDelete("booking-service::S3-F10::*");
        return saved;
    }

    public void deleteBookingItem(Long id) {
        BookingItem item = findById(id);
        Long bookingId = item.getBooking() != null ? item.getBooking().getId() : null;
        bookingItemRepository.delete(item);
        // invalidate entity detail + parent booking + analytics (§4.4.4)
        cacheInvalidator.deleteKey("booking-service::booking-item::" + id);
        if (bookingId != null) {
            cacheInvalidator.deleteKey("booking-service::booking::" + bookingId);
        }
        cacheInvalidator.wildcardDelete("booking-service::S3-F10::*");
    }

    // ── reads (cached) ───────────────────────────────────────────────────────

    /** CRUD GET-by-ID — 15 min TTL (§4.4.2). List endpoint NOT cached (§4.4.2). */
    @Cacheable(cacheNames = "booking-service::booking-item", key = "#id")
    @Transactional(readOnly = true)
    public BookingItem getBookingItemById(Long id) {
        return findById(id);
    }

    public List<BookingItem> getAllBookingItems() {
        return bookingItemRepository.findAll();
    }

    // ── internal helpers ─────────────────────────────────────────────────────

    private BookingItem findById(Long id) {
        return bookingItemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BookingItem not found with id: " + id));
    }
}
