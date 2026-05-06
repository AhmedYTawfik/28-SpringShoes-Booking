package com.team28.booking.provider.service;

import com.team28.booking.provider.adapter.ObjectArrayDtoAdapter;
import com.team28.booking.provider.cache.CacheInvalidator;
import com.team28.booking.provider.dto.*;
import com.team28.booking.provider.model.Provider;
import com.team28.booking.provider.model.ProviderCertification;
import com.team28.booking.provider.observer.MongoEventLogger;
import com.team28.booking.provider.observer.Observable;
import com.team28.booking.provider.repository.ProviderRepository;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;

@Service
public class ProviderService extends Observable {
    private final ProviderRepository providerRepository;
    private final ProviderCertificationService certificationService;
    private final MongoEventLogger mongoEventLogger;
    private final CacheInvalidationService cacheInvalidationService;
    private final IndexingService indexingService;
    private final ObjectArrayDtoAdapter objectArrayDtoAdapter;
    private final CacheInvalidator cacheInvalidator;
    private final ProviderDashboardService dashboardService;

    public ProviderService(
            ProviderRepository providerRepository,
            ProviderCertificationService certificationService,
            MongoEventLogger mongoEventLogger,
            CacheInvalidationService cacheInvalidationService,
            IndexingService indexingService,
            ObjectArrayDtoAdapter objectArrayDtoAdapter,
            CacheInvalidator cacheInvalidator,
            ProviderDashboardService dashboardService
    ) {
        this.providerRepository = providerRepository;
        this.certificationService = certificationService;
        this.mongoEventLogger = mongoEventLogger;
        this.cacheInvalidationService = cacheInvalidationService;
        this.indexingService = indexingService;
        this.objectArrayDtoAdapter = objectArrayDtoAdapter;
        this.cacheInvalidator = cacheInvalidator;
        this.dashboardService = dashboardService;
    }

    @PostConstruct
    void registerObservers() {
        register(mongoEventLogger);
    }

    // ── writes ───────────────────────────────────────────────────────────────

    @Transactional
    public Provider createProvider(Provider provider) {
        ensureServiceDetailsDescription(provider);
        Provider saved = providerRepository.save(provider);
        invalidateProviderCaches(null);
        emitAfterCommit("PROVIDER_CREATED", providerPayload(saved));
        indexingService.indexProvider(saved, "auto_crud_create");
        return saved;
    }

    @Transactional
    public Provider updateProvider(Long id, Provider updatedProvider) {
        Provider existingProvider = findById(id);

        existingProvider.setName(updatedProvider.getName());
        existingProvider.setEmail(updatedProvider.getEmail());
        existingProvider.setPhone(updatedProvider.getPhone());
        existingProvider.setSpecialty(updatedProvider.getSpecialty());
        existingProvider.setStatus(updatedProvider.getStatus());
        boolean ratingChanged = !Objects.equals(existingProvider.getRating(), updatedProvider.getRating())
                || !Objects.equals(existingProvider.getTotalRatings(), updatedProvider.getTotalRatings());
        existingProvider.setRating(updatedProvider.getRating());
        existingProvider.setTotalRatings(updatedProvider.getTotalRatings());
        existingProvider.setServiceDetails(updatedProvider.getServiceDetails());
        ensureServiceDetailsDescription(existingProvider);

        Provider saved = providerRepository.save(existingProvider);
        invalidateProviderCaches(id);
        if (ratingChanged) {
            cacheInvalidationService.invalidateProviderRating(saved.getId());
            emitAfterCommit("RATING_RECORDED", providerPayload(saved));
        }
        indexingService.indexProvider(saved, "auto_crud_update");
        return saved;
    }

    @Transactional
    public void deleteProvider(Long id) {
        Provider provider = findById(id);
        providerRepository.delete(provider);
        invalidateProviderCaches(id);
        indexingService.deleteProvider(provider);
    }

    @Transactional
    public void updateAvailability(Long providerId, Provider.ProviderStatus newStatus) {
        Provider provider = findById(providerId);

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
        invalidateProviderCaches(providerId);
        emitAfterCommit("AVAILABILITY_TOGGLED", providerPayload(saved));
        indexingService.indexProvider(saved, "auto_crud_update");
    }

    @Transactional
    public Provider updateServiceDetails(Long id, Map<String, Object> updates) {
        Provider provider = findById(id);
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
        invalidateProviderCaches(id);
        Map<String, Object> payload = providerPayload(saved);
        payload.put("serviceDetails", saved.getServiceDetails());
        emitAfterCommit("SERVICE_DETAILS_UPDATED", payload);
        indexingService.indexProvider(saved, "auto_crud_update");
        return saved;
    }

    @Transactional
    public Provider verifyCertificate(Long providerId, Long certificationId, VerifiedBy verifiedBy) {
        Provider provider;
        ProviderCertification providerCertification;
        try {
            provider = findById(providerId);
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
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The verifier is not admin");

        providerCertification.setVerified(true);
        Map<String, Object> metadata = providerCertification.getMetadata();
        if (metadata == null) {
            metadata = new HashMap<>();
            providerCertification.setMetadata(metadata);
        }
        metadata.put("verifiedAt", currentDate);
        metadata.put("verifiedBy", verifiedBy.verifier());
        certificationService.updateCertification(certificationId, providerCertification);

        Map<String, Object> payload = providerPayload(provider);
        payload.put("certificationId", certificationId);
        payload.put("verifiedBy", verifiedBy.verifier());
        emitAfterCommit("CERTIFICATION_VERIFIED", payload);
        return provider;
    }

    public ProviderDashboardDTO logAndGetProviderDashboard(Long id) {
        mongoEventLogger.onEvent("DASHBOARD_VIEWED", Map.of("id", id));
        return dashboardService.getProviderDashboard(id);
    }

    // ── reads (cached) ───────────────────────────────────────────────────────

    public List<Provider> getAllProviders() {
        return providerRepository.findAll();
    }

    /** CRUD GET-by-ID — 15 min TTL (§4.4.2). */
    @Cacheable(cacheNames = "provider-service::provider", key = "#id")
    public Provider getProviderById(Long id) {
        return findById(id);
    }

    public void indexProviderExplicitly(Long id) {
        Provider provider = findById(id);
        indexingService.indexProvider(provider, "explicit");
    }

    /** S2-F5: filter by pricing tier — 5 min TTL (§4.4.1). */
    @Cacheable(cacheNames = "provider-service::S2-F5",
               key = "T(java.util.Objects).hash(#tier, #status)")
    public List<Provider> filterByPricingTier(String tier, Provider.ProviderStatus status) {
        if (status == null) return providerRepository.findByTier(tier);
        else return providerRepository.findByTierAndStatus(tier, status.name());
    }

    /** S2-F1: search providers — 5 min TTL (§4.4.1). */
    @Cacheable(cacheNames = "provider-service::S2-F1",
               key = "T(java.util.Objects).hash(#status, #minRating, #maxRating)")
    public List<Provider> searchProviders(Provider.ProviderStatus status, Double minRating, Double maxRating) {
        if (minRating != null && maxRating != null && minRating > maxRating) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minRating cannot be greater than maxRating");
        }
        return providerRepository.searchProviders(status, minRating, maxRating);
    }

    /** S2-F3: provider earnings summary — 10 min TTL (§4.4.1). */
    @Cacheable(cacheNames = "provider-service::S2-F3",
               key = "T(java.util.Objects).hash(#providerId, #startDate, #endDate)")
    public ProviderEarningsDTO getProviderEarningsSummary(Long providerId, LocalDate startDate, LocalDate endDate) {
        Provider provider = findById(providerId);
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate cannot be after endDate");
        }
        List<Object[]> results = providerRepository.getProviderEarningsSummary(providerId, startDate, endDate);
        Object[] row = results.isEmpty() ? new Object[]{null, null, null} : results.get(0);
        return objectArrayDtoAdapter.toProviderEarningsDTO(provider.getId(), provider.getName(), row);
    }

    // ── internal helpers ─────────────────────────────────────────────────────

    /** Non-cached DB fetch used by all write paths (§4.4.4). */
    Provider findById(Long id) {
        return providerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found with id: " + id));
    }

    /** Invalidate entity detail + all feature caches on any provider write (§4.4.4). */
    private void invalidateProviderCaches(Long id) {
        if (id != null) {
            cacheInvalidator.deleteKey("provider-service::provider::" + id);
        }
        cacheInvalidator.wildcardDelete("provider-service::S2-F1::*");
        cacheInvalidator.wildcardDelete("provider-service::S2-F3::*");
        cacheInvalidator.wildcardDelete("provider-service::S2-F5::*");
        cacheInvalidator.wildcardDelete("provider-service::S2-F6::*");
        cacheInvalidator.wildcardDelete("provider-service::S2-F9::*");
        cacheInvalidator.wildcardDelete("provider-service::S2-F10::*");
    }

    public static String descriptionOrEmpty(Map<String, Object> serviceDetails) {
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

    public List<ProviderCertAlertDTO> getProvidersWithExpCert() {
        List<Provider> providers =
                providerRepository.findProvidersWithExpiredCerts(LocalDate.now());

        List<ProviderCertAlertDTO> certificationAlerts = new ArrayList<>();
        for (Provider provider : providers) {
            List<ProviderCertification> expiredCerts = provider.getProviderCertifications()
                    .stream().filter(
                            cert -> cert.getExpiryDate().isBefore(LocalDate.now())
                    ).toList();

            certificationAlerts.add(new ProviderCertAlertDTO(
                    provider.getId(), provider.getName(),
                    provider.getStatus(), expiredCerts, expiredCerts.size()
            ));
        }

        return certificationAlerts;
    }


    @Transactional
    public void rateProvider(Long providerId, RateProviderDTO rateProvider) {
        Provider provider;
        try {
            provider = getProviderById(providerId);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }

        BookingSummary bookingSummary =
                providerRepository.getBookingSummary(rateProvider.bookingId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        if (!bookingSummary.getProviderId().equals(providerId))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking is not associated with given provider");

        if (!bookingSummary.getStatus().equals("COMPLETED"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking was not completed");

        if (rateProvider.rating() < 1.0 || rateProvider.rating() > 5.0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rating must be between 1 and 5");

        // TC340: Duplicate rating check — atomically mark booking as rated
        int updated = providerRepository.markBookingAsRated(rateProvider.bookingId());
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This booking has already been rated");
        }

        int previousRating = provider.getTotalRatings();
        int newTotalRatings = previousRating + 1;
        double newRating = (provider.getRating() * previousRating + rateProvider.rating()) / newTotalRatings;

        provider.setRating(newRating);
        provider.setTotalRatings(newTotalRatings);
        updateProvider(providerId, provider);
    }

    public List<TopProviderDTO> getTopRatedProviders(int limit) {
        if (limit == 0) limit = 50;
        PageRequest paging = PageRequest.of(0, limit);
        List<ProviderSummary> topProviders = providerRepository.findTopProvidersWithBookingCount(paging);


        List<TopProviderDTO> topProviderDTOS = new ArrayList<>();
        topProviders.forEach(provider -> topProviderDTOS.add(
                // The total bookings are left as 0 for now
                new TopProviderDTO(
                        provider.getId(), provider.getName(),
                        provider.getRating(), provider.getBookingCount().intValue()
                )
        ));

        return topProviderDTOS;
    }
}
