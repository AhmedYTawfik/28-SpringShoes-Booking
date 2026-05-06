package com.team28.booking.provider.service;

import com.team28.booking.provider.cache.CacheInvalidator;
import com.team28.booking.provider.model.Provider;
import com.team28.booking.provider.model.ProviderCertification;
import com.team28.booking.provider.repository.ProviderCertificationRepository;
import com.team28.booking.provider.repository.ProviderRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProviderCertificationService {
    private final ProviderCertificationRepository providerCertificationRepository;
    private final ProviderRepository providerRepository;
    private final CacheInvalidator cacheInvalidator;

    public ProviderCertificationService(ProviderCertificationRepository providerCertificationRepository,
                                        ProviderRepository providerRepository,
                                        CacheInvalidator cacheInvalidator) {
        this.providerCertificationRepository = providerCertificationRepository;
        this.providerRepository = providerRepository;
        this.cacheInvalidator = cacheInvalidator;
    }

    public ProviderCertification createCertification(Long providerId, ProviderCertification certification) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("Provider not found with id: " + providerId));
        certification.setProvider(provider);
        ProviderCertification saved = providerCertificationRepository.save(certification);
        cacheInvalidator.wildcardDelete("provider-service::provider-certification::*");
        return saved;
    }

    public List<ProviderCertification> getAllCertifications() {
        return providerCertificationRepository.findAll();
    }

    /** CRUD GET-by-ID — 15 min TTL (§4.4.2). */
    @Cacheable(cacheNames = "provider-service::provider-certification", key = "#id")
    public ProviderCertification getCertificationById(Long id) {
        return providerCertificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Certification not found with id: " + id));
    }

    public ProviderCertification updateCertification(Long id, ProviderCertification updatedCertification) {
        ProviderCertification existingCertification = findById(id);

        existingCertification.setType(updatedCertification.getType());
        existingCertification.setDocumentUrl(updatedCertification.getDocumentUrl());
        existingCertification.setExpiryDate(updatedCertification.getExpiryDate());
        existingCertification.setVerified(updatedCertification.getVerified());
        existingCertification.setMetadata(updatedCertification.getMetadata());

        ProviderCertification saved = providerCertificationRepository.save(existingCertification);
        cacheInvalidator.deleteKey("provider-service::provider-certification::" + id);
        return saved;
    }

    public void deleteCertification(Long id) {
        ProviderCertification certification = findById(id);
        providerCertificationRepository.delete(certification);
        cacheInvalidator.deleteKey("provider-service::provider-certification::" + id);
    }

    public boolean verifyCertificateAdmin(Long id) {
        return providerCertificationRepository.verifyVerifierIsAdmin(id);
    }

    public boolean belongsToProvider(Long certId, Long providerId) {
        return providerCertificationRepository.existsByIdAndProvider_Id(certId, providerId);
    }

    /** Non-cached DB fetch used by all write paths (§4.4.4). */
    ProviderCertification findById(Long id) {
        return providerCertificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Certification not found with id: " + id));
    }
}
