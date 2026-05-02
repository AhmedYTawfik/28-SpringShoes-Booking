package com.team28.booking.calendar.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * S4-F11: Request body for POST /api/calendar/{providerId}/availability-snapshot.
 *
 * <p>Modelled as a record (immutable, canonical constructor) — consistent with all
 * other DTOs in the project. Jackson deserializes into records using the constructor.</p>
 *
 * <p>{@code date} is required: a missing or null date would cause a NullPointerException
 * deep in the service layer; {@code @NotNull} + {@code @Valid} on the controller
 * short-circuits that with a proper 400 response.</p>
 */
public record AvailabilitySnapshotRequest(
        @NotNull LocalDate date,
        String notes
) {}
