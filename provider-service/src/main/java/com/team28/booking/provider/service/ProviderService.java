package com.team28.booking.provider.service;

import com.team28.booking.provider.dto.ProviderEarningsDTO;
import com.team28.booking.provider.dto.VerifiedBy;
import com.team28.booking.provider.model.Provider;
import com.team28.booking.provider.model.ProviderCertification;
import com.team28.booking.provider.observer.MongoEventLogger;
import com.team28.booking.provider.observer.Observable;
import com.team28.booking.provider.repository.ProviderRepository;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

@Service
public class ProviderService extends Observable {
    private final ProviderRepository providerRepository;
    private final ProviderCertificationService certificationService;
    private final MongoEventLogger mongoEventLogger;
    private final CacheInvalidationService cacheInvalidationService;
    private final IndexingService indexingService;

    public ProviderService(
            ProviderRepository providerRepository,
            ProviderCertificationService certificationService,
            MongoEventLogger mongoEventLogger,
            CacheInvalidationService cacheInvalidationService,
            IndexingService indexingService
    ) {
        this.providerRepository = providerRepository;
        this.certificationService = certificationService;
        this.mongoEventLogger = mongoEventLogger;
        this.cacheInvalidationService = cacheInvalidationService;
        this.indexingService = indexingService;
    }

    @PostConstruct
    void registerObservers() {
        register(mongoEventLogger);
    }

    //create
    public Provider createProvider(Provider provider) {
        ensureServiceDetailsDescription(provider);
        Provider saved = providerRepository.save(provider);
        emitAfterCommit("PROVIDER_CREATED", providerPayload(saved));
        indexingService.indexProvider(saved, "auto_crud_create");
        return saved;
    }

    //get all
    public List<Provider> getAllProviders() {
        return providerRepository.findAll();
    }

    //get by id
    public Provider getProviderById(Long id) {
        return providerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found with id: " + id));
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
        boolean ratingChanged = !Objects.equals(existingProvider.getRating(), updatedProvider.getRating())
                || !Objects.equals(existingProvider.getTotalRatings(), updatedProvider.getTotalRatings());
        existingProvider.setRating(updatedProvider.getRating());
        existingProvider.setTotalRatings(updatedProvider.getTotalRatings());
        existingProvider.setServiceDetails(updatedProvider.getServiceDetails());
        ensureServiceDetailsDescription(existingProvider);

        Provider saved = providerRepository.save(existingProvider);
        if (ratingChanged) {
            cacheInvalidationService.invalidateProviderRating(saved.getId());
            emitAfterCommit("RATING_RECORDED", providerPayload(saved));
        }
        indexingService.indexProvider(saved, "auto_crud_update");
        return saved;
    }

    //delete
    public void deleteProvider(Long id) {
        Provider provider = getProviderById(id);
        providerRepository.delete(provider);
        indexingService.deleteProvider(provider);
    }

    @Transactional
    public void updateAvailability(Long providerId, Provider.ProviderStatus newStatus) {
        Provider provider = getProviderById(providerId);

        if (newStatus == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        }
        if (newStatus == Provider.ProviderStatus.OFFLINE) {
            Long activeBookings = providerRepository.countActiveBookings(providerId);
            if (activeBookings != null && activeBookings > 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Cannot set provider to OFFLINE while having active bookings"
                );
            }
        }

        provider.setStatus(newStatus);
        Provider saved = providerRepository.save(provider);
        emitAfterCommit("AVAILABILITY_TOGGLED", providerPayload(saved));
    }
  
    public ProviderEarningsDTO getProviderEarningsSummary(Long providerId, LocalDate startDate, LocalDate endDate) {
        Provider provider = getProviderById(providerId);
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate cannot be after endDate");
        }

        List<Object[]> results = providerRepository.getProviderEarningsSummary(providerId, startDate, endDate);

        Long totalBookings = 0L;
        Double totalEarnings = 0.0;
        Double averageBookingPrice = 0.0;

        if (!results.isEmpty()) {
            Object[] row = results.get(0);

            if (row[0] != null) {
                totalBookings = ((Number) row[0]).longValue();
            }
            if (row[1] != null) {
                totalEarnings = ((Number) row[1]).doubleValue();
            }
            if (row[2] != null) {
                averageBookingPrice = ((Number) row[2]).doubleValue();
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
  
    public Provider updateServiceDetails(Long id, Map<String, Object> updates) {
        Provider provider = getProviderById(id);
        Map<String, Object> existingDetails = provider.getServiceDetails();

        if (existingDetails == null) {
            existingDetails = new HashMap<>();
        }
        if (updates != null) {
            existingDetails.putAll(updates);
        }

        provider.setServiceDetails(existingDetails);
        ensureServiceDetailsDescription(provider);
        Provider saved = providerRepository.save(provider);
        Map<String, Object> payload = providerPayload(saved);
        payload.put("serviceDetails", saved.getServiceDetails());
        emitAfterCommit("SERVICE_DETAILS_UPDATED", payload);
        return saved;
    }

    public static String descriptionOrEmpty(Map<String,Object> serviceDetails) {
        return Optional.ofNullable(serviceDetails)
                .map(m -> m.get("description")).map(Object::toString).orElse("");
    }

    private static void ensureServiceDetailsDescription(Provider provider) {
        Map<String, Object> serviceDetails = provider.getServiceDetails();
        if (serviceDetails == null) {
            serviceDetails = new HashMap<>();
        } else {
            serviceDetails = new HashMap<>(serviceDetails);
        }
        serviceDetails.put("description", descriptionOrEmpty(serviceDetails));
        provider.setServiceDetails(serviceDetails);
    }
  
    public List<Provider> searchProviders(Provider.ProviderStatus status, Double minRating, Double maxRating) {
        if (minRating != null && maxRating != null && minRating > maxRating) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minRating cannot be greater than maxRating");
        }

        return providerRepository.searchProviders(status, minRating, maxRating);
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

        Map<String, Object> payload = providerPayload(provider);
        payload.put("certificationId", certificationId);
        payload.put("verifiedBy", verifiedBy.verifier());
        emitAfterCommit("CERTIFICATION_VERIFIED", payload);
        return provider;
    }

    private void emitAfterCommit(String action, Map<String, Object> payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notifyObservers(action, payload);
                }
            });
        } else {
            notifyObservers(action, payload);
        }
    }

    private Map<String, Object> providerPayload(Provider provider) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("providerId", provider.getId());
        payload.put("name", provider.getName());
        payload.put("specialty", provider.getSpecialty());
        payload.put("status", provider.getStatus() != null ? provider.getStatus().name() : null);
        payload.put("rating", provider.getRating());
        payload.put("totalRatings", provider.getTotalRatings());
        return payload;
    }
}
