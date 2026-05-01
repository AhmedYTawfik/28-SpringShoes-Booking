package com.team28.booking.booking.dto;

import com.team28.booking.booking.model.Booking;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class BookingDetailsDTOBuilder {
    private Long bookingId;
    private Long userId;
    private Long providerId;
    private Booking.Status status;
    private BigDecimal totalPrice;
    private Map<String, Object> metadata;
    private List<ServiceDetailsDTO> services;
    private Integer totalServices;
    private Integer completedServices;

    public BookingDetailsDTOBuilder bookingId(Long bookingId) { this.bookingId = bookingId; return this; }
    public BookingDetailsDTOBuilder userId(Long userId) { this.userId = userId; return this; }
    public BookingDetailsDTOBuilder providerId(Long providerId) { this.providerId = providerId; return this; }
    public BookingDetailsDTOBuilder status(Booking.Status status) { this.status = status; return this; }
    public BookingDetailsDTOBuilder totalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; return this; }
    public BookingDetailsDTOBuilder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
    public BookingDetailsDTOBuilder services(List<ServiceDetailsDTO> services) { this.services = services; return this; }
    public BookingDetailsDTOBuilder totalServices(Integer totalServices) { this.totalServices = totalServices; return this; }
    public BookingDetailsDTOBuilder completedServices(Integer completedServices) { this.completedServices = completedServices; return this; }

    public BookingDetailsDTO build() {
        return new BookingDetailsDTO(bookingId, userId, providerId, status, totalPrice,
                metadata, services, totalServices, completedServices);
    }
}
