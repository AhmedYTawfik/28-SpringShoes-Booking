package com.team28.booking.invoice.dto;

public class ProcessInvoiceRequest {
    private Long bookingId;
    private Long userId;
    private String method;

    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
}
