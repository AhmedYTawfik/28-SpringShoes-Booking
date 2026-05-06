package com.team28.booking.provider.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VerifiedBy(@JsonProperty("verifiedBy") Long verifier) { }

