package com.team28.booking.calendar.dto;

public interface IdleProviderProjection {

    Long getProviderId();

    String getProviderName();

    String getSpecialty();

    Double getRating();

    Long getBookedSlotsCount();

    Long getTotalSlotsCount();
}
