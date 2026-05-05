package com.team28.booking.provider.dto;

import com.team28.booking.provider.model.Provider;
import com.team28.booking.provider.model.ProviderCertification;

import java.util.List;

public record ProviderCertAlertDTO (
    Long providerId,
    String providerName,
    Provider.ProviderStatus providerStatus,
    List<ProviderCertification> expiredCertifications,
    Integer expiredCount
) { }
