package com.team28.booking.user.adapter;

import com.team28.booking.user.dto.TopClientDTO;
import com.team28.booking.user.dto.UserBookingSummaryDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ObjectArrayDtoAdapter {

    public UserBookingSummaryDTO toUserBookingSummaryDTO(Object[] row) {
        return new UserBookingSummaryDTO(
                ((Number) row[0]).longValue(),
                (String) row[1],
                ((Number) row[2]).longValue(),
                ((Number) row[3]).longValue(),
                ((Number) row[4]).longValue(),
                toBigDecimal(row[5]),
                toBigDecimal(row[6])
        );
    }

    public TopClientDTO toTopClientDTO(Object[] row) {
        TopClientDTO dto = new TopClientDTO();
        dto.setUserId(((Number) row[0]).longValue());
        dto.setName((String) row[1]);
        dto.setTotalSpent(((Number) row[2]).doubleValue());
        dto.setBookingCount(((Number) row[3]).longValue());
        return dto;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(value.toString());
    }
}
