package com.team28.booking.booking.dto;

import java.io.Serializable;

/**
 * S3-F12: Provider recommendation result.
 * Fields: providerId, name, specialty, score (count of similar users who booked this provider).
 */
public record ProviderRecommendationDTO(
        Long providerId,
        String name,
        String specialty,
        long score
) implements Serializable {

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Long providerId;
        private String name;
        private String specialty;
        private long score;

        public Builder providerId(Long providerId) { this.providerId = providerId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder specialty(String specialty) { this.specialty = specialty; return this; }
        public Builder score(long score) { this.score = score; return this; }
        public ProviderRecommendationDTO build() {
            return new ProviderRecommendationDTO(providerId, name, specialty, score);
        }
    }
}
