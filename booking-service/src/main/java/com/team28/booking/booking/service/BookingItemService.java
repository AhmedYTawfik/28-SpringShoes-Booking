package com.team28.booking.booking.service;

import com.team28.booking.booking.model.Booking;
import com.team28.booking.booking.model.BookingItem;
import com.team28.booking.booking.repository.BookingItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class BookingItemService {

    private final BookingItemRepository bookingItemRepository;
    private final BookingService bookingService;

    public BookingItemService(BookingItemRepository bookingItemRepository, BookingService bookingService) {
        this.bookingItemRepository = bookingItemRepository;
        this.bookingService = bookingService;
    }

    public BookingItem createBookingItem(Long bookingId, BookingItem bookingItem) {
        Booking booking = bookingService.getBookingById(bookingId);
        bookingItem.setId(null);
        bookingItem.setBooking(booking);
        BookingItem saved = bookingItemRepository.save(bookingItem);
        bookingService.emitServicesAdded(booking, saved.getId());
        return saved;
    }

    public List<BookingItem> getAllBookingItems() {
        return bookingItemRepository.findAll();
    }

    public BookingItem getBookingItemById(Long id) {
        return bookingItemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BookingItem not found with id: " + id));
    }

    public BookingItem updateBookingItem(Long id, BookingItem updated) {
        BookingItem existing = getBookingItemById(id);

        if (updated.getServiceOrder() != null) existing.setServiceOrder(updated.getServiceOrder());
        if (updated.getServiceName() != null) existing.setServiceName(updated.getServiceName());
        if (updated.getDuration() != null) existing.setDuration(updated.getDuration());
        if (updated.getPrice() != null) existing.setPrice(updated.getPrice());
        if (updated.getStatus() != null) existing.setStatus(updated.getStatus());
        if (updated.getMetadata() != null) existing.setMetadata(updated.getMetadata());

        return bookingItemRepository.save(existing);
    }

    public void deleteBookingItem(Long id) {
        BookingItem item = getBookingItemById(id);
        bookingItemRepository.delete(item);
    }
}
