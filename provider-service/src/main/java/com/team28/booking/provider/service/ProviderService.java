package com.team28.booking.provider.service;

import com.team28.booking.provider.dto.ProviderEarningsDTO;
import com.team28.booking.provider.dto.VerifiedBy;
import com.team28.booking.provider.model.Provider;
import com.team28.booking.provider.model.ProviderCertification;
import com.team28.booking.provider.repository.ProviderRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class ProviderService {
    private final ProviderRepository providerRepository;
    private final ProviderCertificationService certificationService;

    public ProviderService(
            ProviderRepository providerRepository,
            ProviderCertificationService certificationService
    ) {
        this.providerRepository = providerRepository;
        this.certificationService = certificationService;
    }

    //create
    public Provider createProvider(Provider provider) {
        return providerRepository.save(provider);
    }

    //get all
    public List<Provider> getAllProviders() {
        return providerRepository.findAll();
    }

    //get by id
    public Provider getProviderById(Long id) {
        return providerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Provider not found with id: " + id));
    }

    public List<Provider> filterByPricingTier(
            String tier, Provider.ProviderStatus status
    ) {
        if (status == null) return providerRepository.findByTier(tier);
        else return providerRepository.findByTierAndStatus(tier, status.name());
    }

    //update
    public Provider updateProvider(Long id, Provider updatedProvider) {
        Provider existingProvider = getProviderById(id);

        existingProvider.setName(updatedProvider.getName());
        existingProvider.setEmail(updatedProvider.getEmail());
        existingProvider.setPhone(updatedProvider.getPhone());
        existingProvider.setSpecialty(updatedProvider.getSpecialty());
        existingProvider.setStatus(updatedProvider.getStatus());
        //TODO:updating ratings like this isn't correct (for the sake of eny anam will leave it now)
        existingProvider.setRating(updatedProvider.getRating());
        existingProvider.setTotalRatings(updatedProvider.getTotalRatings());
        existingProvider.setServiceDetails(updatedProvider.getServiceDetails());

        return providerRepository.save(existingProvider);
    }

    //delete
    public void deleteProvider(Long id) {
        Provider provider = getProviderById(id);
        providerRepository.delete(provider);
    }

    public ProviderEarningsDTO getProviderEarningsSummary(Long providerId, LocalDate startDate, LocalDate endDate) {
        Provider provider = getProviderById(providerId);
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate cannot be after endDate");
        }

        Object[] result = providerRepository.getProviderEarningsSummary(providerId, startDate, endDate);

        Long totalBookings = 0L;
        Double totalEarnings = 0.0;
        Double averageBookingPrice = 0.0;

        if (result != null) {
            if (result[0] != null) {
                totalBookings = ((Number) result[0]).longValue();
            }
            if (result[1] != null) {
                totalEarnings = ((Number) result[1]).doubleValue();
            }
            if (result[2] != null) {
                averageBookingPrice = ((Number) result[2]).doubleValue();
            }
        }

        return new ProviderEarningsDTO(
                provider.getId(),
                provider.getName(),
                totalBookings,
                totalEarnings,
                averageBookingPrice
        );
    }

    // I am only writing once, but whatever
    @Transactional
    public Provider verifyCertificate(Long providerId, Long certificationId, VerifiedBy verifiedBy) {
        Provider provider;
        ProviderCertification providerCertification;
        try {
            provider = getProviderById(providerId);
            providerCertification = certificationService.getCertificationById(certificationId);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }

        if (providerCertification.getProvider() != provider)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Certificate does not belong to provider");

        LocalDate currentDate = LocalDate.now();
        if (providerCertification.getExpiryDate().isBefore(currentDate))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Certificate has already expired");

        if (!certificationService.verifyCertificateAdmin(verifiedBy.verifier()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The verifier is not admin");

        providerCertification.setVerified(true);
        Map<String, Object> metadata = providerCertification.getMetadata();
        metadata.put("verifiedAt", currentDate);
        metadata.put("verifiedBy", verifiedBy.verifier());
        certificationService.updateCertification(certificationId, providerCertification);

        return provider;
    }
}
