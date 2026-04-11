package com.team28.booking.invoice.repository;

import com.team28.booking.invoice.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    // S5-F4: check if an invoice already exists for a given booking
    @Query(value = "SELECT EXISTS(SELECT 1 FROM invoices WHERE booking_id = :bookingId)", nativeQuery = true)
    boolean existsByBookingId(@Param("bookingId") Long bookingId);

    // S5-F4: fetch booking status and totalPrice from the shared bookings table
    @Query(value = "SELECT status, total_price FROM bookings WHERE id = :bookingId", nativeQuery = true)
    List<Object[]> findBookingDetails(@Param("bookingId") Long bookingId);

    // S5-F6: aggregate COMPLETED and REFUNDED invoices within a date range
    @Query(value = """
            SELECT
                COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN amount ELSE 0 END), 0),
                COUNT(CASE WHEN status IN ('COMPLETED', 'REFUNDED') THEN 1 END),
                COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END),
                COALESCE(SUM(CASE WHEN status = 'REFUNDED' THEN amount ELSE 0 END), 0),
                COALESCE(AVG(CASE WHEN status IN ('COMPLETED', 'REFUNDED') THEN amount END), 0)
            FROM invoices
            WHERE created_at >= :startDate AND created_at <= :endDate
            """, nativeQuery = true)
    Object[] getRevenueStats(@Param("startDate") LocalDateTime startDate,
                             @Param("endDate") LocalDateTime endDate);


    // Get invoice summary grouped by method for a user
    @Query(value = """
        SELECT method, COUNT(*) as invoice_count, SUM(amount) as total_amount
        FROM invoices
        WHERE user_id = :userId AND status = 'COMPLETED'
        GROUP BY method
        """, nativeQuery = true)
    List<Object[]> getInvoiceSummaryByUserId(@Param("userId") Long userId);

    // Verify user exists (cross-service query)
    @Query(value = "SELECT id FROM users WHERE id = :userId", nativeQuery = true)
    Long findUserById(@Param("userId") Long userId);
}
