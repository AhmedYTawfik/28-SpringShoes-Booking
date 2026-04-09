package com.team28.booking.provider.service;

import com.team28.booking.provider.model.Provider;
import com.team28.booking.provider.model.ProviderCertification;
import com.team28.booking.provider.repository.ProviderCertificationRepository;
import com.team28.booking.provider.repository.ProviderRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProviderCertificationService {
    private final ProviderCertificationRepository providerCertificationRepository;
    private final ProviderRepository providerRepository;

    public ProviderCertificationService(ProviderCertificationRepository providerCertificationRepository, ProviderRepository providerRepository) {
        this.providerCertificationRepository = providerCertificationRepository;
        this.providerRepository = providerRepository;
    }

    //create
    public ProviderCertification createCertification(Long providerId, ProviderCertification certification) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("Provider not found with id: " + providerId));

        certification.setProvider(provider);
        return providerCertificationRepository.save(certification);
    }

    //get all
    public List<ProviderCertification> getAllCertifications() {
        return providerCertificationRepository.findAll();
    }

    //get by id
    public ProviderCertification getCertificationById(Long id) {
        return providerCertificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Certification not found with id: " + id));
    }

    //update
    public ProviderCertification updateCertification(Long id, ProviderCertification updatedCertification) {
        ProviderCertification existingCertification = getCertificationById(id);

        existingCertification.setType(updatedCertification.getType());
        existingCertification.setDocumentUrl(updatedCertification.getDocumentUrl());
        existingCertification.setExpiryDate(updatedCertification.getExpiryDate());
        existingCertification.setVerified(updatedCertification.getVerified());
        existingCertification.setMetadata(updatedCertification.getMetadata());

        return providerCertificationRepository.save(existingCertification);
    }

    //delete
    public void deleteCertification(Long id) {
        ProviderCertification certification = getCertificationById(id);
        providerCertificationRepository.delete(certification);
    }
}
