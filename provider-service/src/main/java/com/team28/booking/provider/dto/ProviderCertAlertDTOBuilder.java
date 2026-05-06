package com.team28.booking.provider.dto;

import com.team28.booking.provider.model.Provider;
import com.team28.booking.provider.model.ProviderCertification;

import java.util.List;

public class ProviderCertAlertDTOBuilder {
    private Long providerId;
    private String providerName;
    private Provider.ProviderStatus providerStatus;
    private List<ProviderCertification> expiredCertifications;
    private Integer expiredCount;

    public ProviderCertAlertDTOBuilder providerId(Long providerId) { this.providerId = providerId; return this; }
    public ProviderCertAlertDTOBuilder providerName(String providerName) { this.providerName = providerName; return this; }
    public ProviderCertAlertDTOBuilder providerStatus(Provider.ProviderStatus providerStatus) { this.providerStatus = providerStatus; return this; }
    public ProviderCertAlertDTOBuilder expiredCertifications(List<ProviderCertification> expiredCertifications) { this.expiredCertifications = expiredCertifications; return this; }
    public ProviderCertAlertDTOBuilder expiredCount(Integer expiredCount) { this.expiredCount = expiredCount; return this; }

    public ProviderCertAlertDTO build() {
        return new ProviderCertAlertDTO(providerId, providerName, providerStatus, expiredCertifications, expiredCount);
    }
}
