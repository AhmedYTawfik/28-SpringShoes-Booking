package com.team28.booking.provider.service;

import com.team28.booking.provider.dto.ProviderDashboardDTO;
import com.team28.booking.provider.dto.ProviderRepoDashboardReturn;
import com.team28.booking.provider.model.Provider;
import com.team28.booking.provider.repository.ProviderRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProviderDashboardService {
    private final ProviderRepository providerRepository;

    public ProviderDashboardService(ProviderRepository providerRepository) {
        this.providerRepository = providerRepository;
    }

    @Cacheable(cacheNames = "provider-service::S2-F12", key = "#id")
    public ProviderDashboardDTO getProviderDashboard(Long id) {
        Provider provider = providerRepository.findById(id).orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND)
        );
        ProviderRepoDashboardReturn dashboardSummary = providerRepository.getProviderDashboardSummary(id);

        // The rating should already be the average, I don't get why the document
        // also wanted the total number of ratings
        Double providerRating = provider.getRating();
        Double utilizationRate = providerRepository.getUtilizationRate(id);

        return ProviderDashboardDTO.builder()
                .providerId(provider.getId())
                .name(provider.getName())
                .totalBookings(dashboardSummary.getTotalCompleted())
                .totalRevenue(dashboardSummary.getTotalRevenue())
                .averageBookingValue(dashboardSummary.getAverageBookingVal())
                .averageRating(providerRating)
                .utilizationRate(utilizationRate)
                .build();
    }
}
