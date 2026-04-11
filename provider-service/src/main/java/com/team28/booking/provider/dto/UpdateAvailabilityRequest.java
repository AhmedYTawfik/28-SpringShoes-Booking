package com.team28.booking.provider.dto;

import com.team28.booking.provider.model.Provider;

public record UpdateAvailabilityRequest(Provider.ProviderStatus status) {
}