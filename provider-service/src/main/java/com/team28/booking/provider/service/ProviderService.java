package com.team28.booking.provider.service;

import com.team28.booking.provider.dto.ProviderCertAlertDTO;
import com.team28.booking.provider.model.Provider;
import com.team28.booking.provider.model.ProviderCertification;
import com.team28.booking.provider.repository.ProviderRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProviderService {
    private final ProviderRepository providerRepository;

    public ProviderService(ProviderRepository providerRepository) {
        this.providerRepository = providerRepository;
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
}
