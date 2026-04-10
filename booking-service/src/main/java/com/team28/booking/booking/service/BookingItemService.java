package com.team28.booking.booking.service;

import com.team28.booking.booking.model.Booking;
import com.team28.booking.booking.model.BookingService;
import com.team28.booking.booking.repository.BookingServiceRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BookingItemService {

    private final BookingServiceRepository bookingServiceRepository;
    private final com.team28.booking.booking.service.BookingService bookingService;

    public BookingItemService(BookingServiceRepository bookingServiceRepository,
                              com.team28.booking.booking.service.BookingService bookingService) {
        this.bookingServiceRepository = bookingServiceRepository;
        this.bookingService = bookingService;
    }

    public BookingService createBookingService(Long bookingId, BookingService bookingServiceItem) {
        Booking booking = bookingService.getBookingById(bookingId);
        bookingServiceItem.setId(null);
        bookingServiceItem.setBooking(booking);
        return bookingServiceRepository.save(bookingServiceItem);
    }

    public List<BookingService> getAllBookingServices() {
        return bookingServiceRepository.findAll();
    }

    public BookingService getBookingServiceById(Long id) {
        return bookingServiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("BookingService not found with id: " + id));
    }

    public BookingService updateBookingService(Long id, BookingService updated) {
        BookingService existing = getBookingServiceById(id);

        if (updated.getServiceOrder() != null) existing.setServiceOrder(updated.getServiceOrder());
        if (updated.getServiceName() != null) existing.setServiceName(updated.getServiceName());
        if (updated.getDuration() != null) existing.setDuration(updated.getDuration());
        if (updated.getPrice() != null) existing.setPrice(updated.getPrice());
        if (updated.getStatus() != null) existing.setStatus(updated.getStatus());
        if (updated.getMetadata() != null) existing.setMetadata(updated.getMetadata());

        return bookingServiceRepository.save(existing);
    }

    public void deleteBookingService(Long id) {
        BookingService item = getBookingServiceById(id);
        bookingServiceRepository.delete(item);
    }
}
