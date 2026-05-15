package com.team28.booking.provider.service;

import com.team28.booking.contracts.dto.ProviderBookingSummaryDTO;
import com.team28.booking.contracts.dto.ProviderUtilizationDTO;
import com.team28.booking.contracts.feign.BookingServiceClient;
import com.team28.booking.contracts.feign.CalendarServiceClient;
import com.team28.booking.provider.dto.ProviderDashboardDTO;
import com.team28.booking.provider.model.Provider;
import com.team28.booking.provider.repository.ProviderRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@Service
public class ProviderDashboardService {
    private final ProviderRepository providerRepository;
    private final BookingServiceClient bookingServiceClient;
    private final CalendarServiceClient calendarServiceClient;

    public ProviderDashboardService(ProviderRepository providerRepository,
                                    BookingServiceClient bookingServiceClient,
                                    CalendarServiceClient calendarServiceClient) {
        this.providerRepository = providerRepository;
        this.bookingServiceClient = bookingServiceClient;
        this.calendarServiceClient = calendarServiceClient;
    }

    @Cacheable(cacheNames = "provider-service::S2-F12", key = "#id")
    public ProviderDashboardDTO getProviderDashboard(Long id) {
        Provider provider = providerRepository.findById(id).orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND)
        );

        //feign -> booking-service (reuses S2-F3 endpoint, no date filter = all-time)
        long totalBookings = 0;
        double totalRevenue = 0.0;
        double avgBookingValue = 0.0;
        try {
            ProviderBookingSummaryDTO summary =
                    bookingServiceClient.getProviderBookingSummary(id, null, null);
            totalBookings = summary.totalBookings();
            totalRevenue = summary.totalEarnings().doubleValue();
            avgBookingValue = summary.averageBookingPrice().doubleValue();
        } catch (Exception e) {
            // graceful degradation - dashboard still shows local data
        }

        //feign -> calendar-service (current month utilization)
        double utilizationRate = 0.0;
        try {
            LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
            LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
            ProviderUtilizationDTO utilization = calendarServiceClient.getProviderUtilization(
                    id, monthStart.toString(), monthEnd.toString()
            );
            utilizationRate = utilization.utilizationRate();
        } catch (Exception e) {
            // graceful degradation
        }

        return ProviderDashboardDTO.builder()
                .providerId(provider.getId())
                .name(provider.getName())
                .totalBookings(totalBookings)
                .totalRevenue(totalRevenue)
                .averageBookingValue(avgBookingValue)
                .averageRating(provider.getRating())
                .utilizationRate(utilizationRate)
                .build();
    }
}