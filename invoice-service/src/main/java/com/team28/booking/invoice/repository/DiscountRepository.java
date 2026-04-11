package com.team28.booking.invoice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.team28.booking.invoice.model.Discount;

@Repository
public interface DiscountRepository extends JpaRepository<Discount, Long> {
    @Query(value = """
        SELECT
            d.id AS discount_id,
            d.code AS code,
            d.discount_type AS discount_type,
            d.discount_value AS discount_value,
            COALESCE(d.current_uses, 0) AS times_used,
            COALESCE(SUM(id.discount_applied), 0) AS total_discount_given,
            d.active AS active,
            d.expiry_date AS expiry_date
        FROM discounts d
        LEFT JOIN invoice_discounts id ON id.discount_id = d.id
        GROUP BY
            d.id, d.code, d.discount_type, d.discount_value,
            d.current_uses, d.active, d.expiry_date
        ORDER BY COALESCE(d.current_uses, 0) DESC, d.id ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<DiscountUsageProjection> findTopUsedDiscounts(@Param("limit") int limit);
}