package com.team28.booking.provider.config;

import com.team28.booking.provider.model.Provider;
import com.team28.booking.provider.repository.ProviderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String[][] BASELINE = {
        {"Preseed Provider 1", "provider1@booking.io", "+201000000001", "Dentist"},
        {"Preseed Provider 2", "provider2@booking.io", "+201000000002", "Barber"},
        {"Preseed Provider 3", "provider3@booking.io", "+201000000003", "Tutor"},
        {"Preseed Provider 4", "provider4@booking.io", "+201000000004", "Dentist"},
    };

    private final ProviderRepository providerRepository;

    public DataInitializer(ProviderRepository providerRepository) {
        this.providerRepository = providerRepository;
    }

    @Bean
    CommandLineRunner seedProviders() {
        return args -> seedBaselineProviders();
    }

    @Scheduled(fixedRate = 2000)
    public void seedBaselineProviders() {
        for (int i = 0; i < BASELINE.length; i++) {
            long expectedId = i + 1;
            try {
                if (!providerRepository.existsById(expectedId)) {
                    Provider p = new Provider();
                    p.setName(BASELINE[i][0]);
                    p.setEmail(BASELINE[i][1]);
                    p.setPhone(BASELINE[i][2]);
                    p.setSpecialty(BASELINE[i][3]);
                    p = providerRepository.save(p);
                    log.info("Re-seeded provider id={}", p.getId());
                }
            } catch (Exception e) {
                log.error("Failed to seed provider id={}", expectedId, e);
            }
        }
    }
}
