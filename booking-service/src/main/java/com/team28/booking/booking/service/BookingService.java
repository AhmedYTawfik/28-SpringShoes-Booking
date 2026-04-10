package com.team28.booking.booking.service;

import com.team28.booking.booking.model.Booking;
import com.team28.booking.booking.repository.BookingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;

    public BookingService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    public Booking createBooking(Booking booking) {
        booking.setId(null);
        return bookingRepository.save(booking);
    }

    public List<Booking> getAllBookings() {
        return bookingRepository.findAll();
    }

    public Booking getBookingById(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found with id: " + id));
    }

    public Booking updateBooking(Long id, Booking updated) {
        Booking existing = getBookingById(id);

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

    public void deleteBooking(Long id) {
        Booking booking = getBookingById(id);
        bookingRepository.delete(booking);
    }

    @Transactional
    public Booking cancelBooking(Long id) {
        Booking booking = getBookingById(id);

        if (booking.getStatus() != Booking.Status.REQUESTED && booking.getStatus() != Booking.Status.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking can only be cancelled if it is REQUESTED or CONFIRMED");
        }

        booking.setStatus(Booking.Status.CANCELLED);

        if (booking.getProviderId() != null) {
            bookingRepository.updateProviderStatusToAvailable(booking.getProviderId());
        }

        return bookingRepository.save(booking);
    }
}
