package com.team28.booking.user.dto;

public class TopClientDTOBuilder {
    private Long userId;
    private String name;
    private Double totalSpent;
    private Long bookingCount;

    public TopClientDTOBuilder userId(Long userId) { this.userId = userId; return this; }
    public TopClientDTOBuilder name(String name) { this.name = name; return this; }
    public TopClientDTOBuilder totalSpent(Double totalSpent) { this.totalSpent = totalSpent; return this; }
    public TopClientDTOBuilder bookingCount(Long bookingCount) { this.bookingCount = bookingCount; return this; }

    public TopClientDTO build() {
        return new TopClientDTO(userId, name, totalSpent, bookingCount);
    }
}
