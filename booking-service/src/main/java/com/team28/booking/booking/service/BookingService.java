package com.team28.booking.booking.service;

import com.team28.booking.booking.dto.AddServiceItemDTO;
import com.team28.booking.booking.dto.BookingDetailsDTO;
import com.team28.booking.booking.dto.BookingEstimateDTO;
import com.team28.booking.booking.dto.BookingEstimateRequestDTO;
import com.team28.booking.booking.dto.EstimateServiceItemDTO;
import com.team28.booking.booking.dto.ServiceDetailsDTO;
import com.team28.booking.booking.model.Booking;
import com.team28.booking.booking.model.BookingItem;
import com.team28.booking.booking.repository.BookingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found with id: " + id));
    }

    public Booking updateBooking(Long id, Booking updated) {
        Booking existing = getBookingById(id);

        if (updated.getUserId() != null)
            existing.setUserId(updated.getUserId());
        existing.setProviderId(updated.getProviderId());
        if (updated.getAppointmentDate() != null)
            existing.setAppointmentDate(updated.getAppointmentDate());
        if (updated.getStartTime() != null)
            existing.setStartTime(updated.getStartTime());
        if (updated.getEndTime() != null)
            existing.setEndTime(updated.getEndTime());
        if (updated.getStatus() != null)
            existing.setStatus(updated.getStatus());
        existing.setTotalPrice(updated.getTotalPrice());
        if (updated.getMetadata() != null)
            existing.setMetadata(updated.getMetadata());
        existing.setCompletedAt(updated.getCompletedAt());

        return bookingRepository.save(existing);
    }

    public void deleteBooking(Long id) {
        Booking booking = getBookingById(id);
        bookingRepository.delete(booking);
    }

    public BookingEstimateDTO getEstimate(BookingEstimateRequestDTO request) {
        if (request.services() == null || request.services().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Services list must not be empty");
        }
        for (EstimateServiceItemDTO service : request.services()) {
            if (service.duration() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service duration must be positive");
            }
        }

        int totalDuration = request.services().stream()
                .mapToInt(EstimateServiceItemDTO::duration)
                .sum();

        BigDecimal basePrice = BigDecimal.valueOf(5.0).multiply(BigDecimal.valueOf(totalDuration));

        Long activeCount = bookingRepository.countActiveBookingsByProviderAndDate(
                request.providerId(), request.appointmentDate());

        BigDecimal demandMultiplier;
        if (activeCount <= 3) {
            demandMultiplier = BigDecimal.valueOf(1.0);
        } else if (activeCount <= 7) {
            demandMultiplier = BigDecimal.valueOf(1.25);
        } else {
            demandMultiplier = BigDecimal.valueOf(1.5);
        }

        BigDecimal estimatedPrice = basePrice.multiply(demandMultiplier);

        return new BookingEstimateDTO(totalDuration, basePrice, estimatedPrice, demandMultiplier);
    }

    @Transactional(readOnly = true)
    public List<Booking> searchByMetadata(String key, String value) {
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Metadata key must not be blank");
        }
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Metadata value must not be blank");
        }
        return bookingRepository.findByMetadataKeyValue(key, value);
    }

    @Transactional
    public Booking cancelBooking(Long id) {
        Booking booking = getBookingById(id);

        if (booking.getStatus() != Booking.Status.REQUESTED && booking.getStatus() != Booking.Status.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Booking can only be cancelled if it is REQUESTED or CONFIRMED");
        }

        booking.setStatus(Booking.Status.CANCELLED);

        if (booking.getProviderId() != null) {
            bookingRepository.updateProviderStatusToAvailable(booking.getProviderId());
        }

        return bookingRepository.save(booking);
    }

    @Transactional
    public Booking addServicesToBooking(Long bookingId, List<AddServiceItemDTO> services) {
        if (services == null || services.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Services list must not be empty");
        }

        Booking booking = getBookingById(bookingId);

        if (booking.getStatus() != Booking.Status.REQUESTED && booking.getStatus() != Booking.Status.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Services can only be added when booking is REQUESTED or CONFIRMED");
        }

        for (AddServiceItemDTO service : services) {
            if (service == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service item must not be null");
            }
            if (service.serviceName() == null || service.serviceName().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service name is required");
            }
            if (service.duration() == null || service.duration() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service duration must be positive");
            }
            if (service.price() == null || service.price().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service price must be positive");
            }
        }

        int maxOrder = booking.getBookingServices().stream()
                .mapToInt(BookingItem::getServiceOrder)
                .max()
                .orElse(0);

        for (AddServiceItemDTO serviceData : services) {
            maxOrder++;
            BookingItem item = new BookingItem();
            item.setServiceName(serviceData.serviceName());
            item.setDuration(serviceData.duration());
            item.setPrice(serviceData.price());
            item.setMetadata(serviceData.metadata());
            item.setServiceOrder(maxOrder);
            item.setStatus(BookingItem.Status.PENDING);
            item.setBooking(booking);
            booking.getBookingServices().add(item);
        }

        Booking savedBooking = bookingRepository.save(booking);

        savedBooking.getBookingServices().sort(Comparator.comparing(BookingItem::getServiceOrder));

        return savedBooking;
    }
  
    @Transactional(readOnly = true)
    public BookingDetailsDTO getBookingDetails(Long id) {
        Booking booking = getBookingById(id);

        List<ServiceDetailsDTO> services = Optional.ofNullable(booking.getBookingServices())
                .orElse(List.of())
                .stream()
                .sorted(Comparator.comparing(BookingItem::getServiceOrder))
                .map(item -> new ServiceDetailsDTO(
                        item.getId(),
                        item.getServiceOrder(),
                        item.getServiceName(),
                        item.getDuration(),
                        item.getPrice(),
                        item.getStatus(),
                        item.getMetadata()))
                .toList();

        int totalServices = services.size();
        int completedServices = (int) services.stream()
                .filter(s -> s.status() == BookingItem.Status.COMPLETED)
                .count();

        return new BookingDetailsDTO(
                booking.getId(),
                booking.getUserId(),
                booking.getProviderId(),
                booking.getStatus(),
                booking.getTotalPrice(),
                booking.getMetadata(),
                services,
                totalServices,
                completedServices);
    }
}
