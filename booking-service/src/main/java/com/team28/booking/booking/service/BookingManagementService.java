package com.team28.booking.booking.service;

import com.team28.booking.booking.model.Booking;
import com.team28.booking.booking.model.BookingService;
import com.team28.booking.booking.repository.BookingRepository;
import com.team28.booking.booking.repository.BookingServiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BookingManagementService {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingServiceRepository bookingServiceRepository;

    // ── Booking CRUD ──────────────────────────────────────────────────────────

    public Booking createBooking(Booking booking) {
        return bookingRepository.save(booking);
    }

    public List<Booking> findAllBookings() {
        return bookingRepository.findAll();
    }

    public Booking findBookingById(Long id) {
        return bookingRepository.findById(id).orElse(null);
    }

    public Booking updateBooking(Long id, Booking updated) {
        Booking existing = bookingRepository.findById(id).orElse(null);
        if (existing == null) return null;

        if (updated.getUserId() != null) existing.setUserId(updated.getUserId());
        existing.setProviderId(updated.getProviderId());
        if (updated.getAppointmentDate() != null) existing.setAppointmentDate(updated.getAppointmentDate());
        if (updated.getStartTime() != null) existing.setStartTime(updated.getStartTime());
        if (updated.getEndTime() != null) existing.setEndTime(updated.getEndTime());
        if (updated.getStatus() != null) existing.setStatus(updated.getStatus());
        existing.setTotalPrice(updated.getTotalPrice());
        if (updated.getMetadata() != null) existing.setMetadata(updated.getMetadata());
        existing.setCompletedAt(updated.getCompletedAt());

        return bookingRepository.save(existing);
    }

    public boolean deleteBooking(Long id) {
        if (!bookingRepository.existsById(id)) return false;
        bookingRepository.deleteById(id);
        return true;
    }

    // ── BookingService CRUD ───────────────────────────────────────────────────

    public BookingService createBookingService(BookingService bookingService) {
        return bookingServiceRepository.save(bookingService);
    }

    public List<BookingService> findAllBookingServices() {
        return bookingServiceRepository.findAll();
    }

    public BookingService findBookingServiceById(Long id) {
        return bookingServiceRepository.findById(id).orElse(null);
    }

    public BookingService updateBookingService(Long id, BookingService updated) {
        BookingService existing = bookingServiceRepository.findById(id).orElse(null);
        if (existing == null) return null;

        if (updated.getServiceOrder() != null) existing.setServiceOrder(updated.getServiceOrder());
        if (updated.getServiceName() != null) existing.setServiceName(updated.getServiceName());
        if (updated.getDuration() != null) existing.setDuration(updated.getDuration());
        if (updated.getPrice() != null) existing.setPrice(updated.getPrice());
        if (updated.getStatus() != null) existing.setStatus(updated.getStatus());
        if (updated.getMetadata() != null) existing.setMetadata(updated.getMetadata());

        return bookingServiceRepository.save(existing);
    }

    public boolean deleteBookingService(Long id) {
        if (!bookingServiceRepository.existsById(id)) return false;
        bookingServiceRepository.deleteById(id);
        return true;
    }
}
