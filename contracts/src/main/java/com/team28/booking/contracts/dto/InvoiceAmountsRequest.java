package com.team28.booking.contracts.dto;

import java.util.List;

public record InvoiceAmountsRequest(List<Long> bookingIds) {
}
