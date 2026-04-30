//package com.team28.booking.user.dto;
//
//import java.math.BigDecimal;
//
//public record TopClientDTO(
//        Long userId,
//        String name,
//        BigDecimal totalSpent,
//        Long bookingCount
//) {
//}

package com.team28.booking.user.dto;

public class TopClientDTO {

    private Long userId;
    private String name;
    private Double totalSpent;
    private Long bookingCount;

    // Constructors
    public TopClientDTO() {
    }

    public TopClientDTO(Long userId, String name, Double totalSpent, Long bookingCount) {
        this.userId = userId;
        this.name = name;
        this.totalSpent = totalSpent;
        this.bookingCount = bookingCount;
    }

    // Getters and Setters
    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getTotalSpent() {
        return totalSpent;
    }

    public void setTotalSpent(Double totalSpent) {
        this.totalSpent = totalSpent;
    }

    public Long getBookingCount() {
        return bookingCount;
    }

    public void setBookingCount(Long bookingCount) {
        this.bookingCount = bookingCount;
    }

    public static TopClientDTOBuilder builder() { return new TopClientDTOBuilder(); }
}
