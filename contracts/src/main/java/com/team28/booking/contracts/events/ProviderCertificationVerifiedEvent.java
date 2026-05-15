package com.team28.booking.contracts.events;

public record ProviderCertificationVerifiedEvent(Long providerId, Long certificationId, Long verifiedBy) {
}
