package com.team28.booking.invoice.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.team28.booking.invoice.model.Invoice;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    @Query("""
        SELECT DISTINCT i
        FROM Invoice i
        LEFT JOIN FETCH i.invoiceDiscounts id
        LEFT JOIN FETCH id.discount d
        WHERE i.id = :invoiceId
        """)
    Optional<Invoice> findByIdWithDiscounts(@Param("invoiceId") Long invoiceId);

     // Search invoices by status and date range using native SQL
    // Returns invoices matching any non-null filter criteria
    @Query(value = """
        SELECT DISTINCT i.*
        FROM invoices i
        JOIN bookings b ON b.id = i.booking_id
        WHERE (CAST(:status AS text) IS NULL OR i.status = CAST(:status AS text))
        AND (CAST(:startDate AS timestamp) IS NULL OR b.requested_at >= CAST(:startDate AS timestamp))
        AND (CAST(:endDate AS timestamp) IS NULL OR b.requested_at <= CAST(:endDate AS timestamp))
        ORDER BY i.created_at DESC
        """, nativeQuery = true)
    List<Invoice> searchInvoices(
        @Param("status") String status,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    // S5-F4: get user_id from a booking
    @Query(value = "SELECT user_id FROM bookings WHERE id = :bookingId", nativeQuery = true)
    Long findUserIdByBookingId(@Param("bookingId") Long bookingId);

    // S5-F4: check if an invoice already exists for a given booking
    @Query(value = "SELECT EXISTS(SELECT 1 FROM invoices WHERE booking_id = :bookingId)", nativeQuery = true)
    boolean existsByBookingId(@Param("bookingId") Long bookingId);

    Optional<Invoice> findByBookingId(Long bookingId);

    // S5-F4: fetch booking status and totalPrice from the shared bookings table
    @Query(value = "SELECT status, total_price FROM bookings WHERE id = :bookingId", nativeQuery = true)
    List<Object[]> findBookingDetails(@Param("bookingId") Long bookingId);

    // S5-F6: aggregate COMPLETED and REFUNDED invoices within a booking date range
    @Query(value = """
            SELECT
                COALESCE(SUM(CASE WHEN i.status = 'COMPLETED' THEN i.amount ELSE 0 END), 0),
                COUNT(CASE WHEN i.status = 'COMPLETED' THEN 1 END),
                COALESCE(SUM(CASE WHEN i.status = 'REFUNDED' THEN i.amount ELSE 0 END), 0),
                COUNT(CASE WHEN i.status = 'REFUNDED' THEN 1 END),
                COALESCE(AVG(CASE WHEN i.status IN ('COMPLETED', 'REFUNDED') THEN i.amount END), 0)
            FROM invoices i
            JOIN bookings b ON b.id = i.booking_id
            WHERE b.requested_at >= :startDate AND b.requested_at <= :endDate
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

    // S5-F12: fetch booking status + appointmentDate for cancellation refund
    @Query(value = """
        SELECT b.status, b.appointment_date
        FROM bookings b
        WHERE b.id = :bookingId
        """, nativeQuery = true)
    List<Object[]> findBookingForCancellation(@Param("bookingId") Long bookingId);

    // S5-F12: set booking status to CANCELLED after successful cancellation refund
    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE bookings
        SET status = 'CANCELLED'
        WHERE id = :bookingId
        AND status IN ('REQUESTED', 'CONFIRMED')
        """, nativeQuery = true)
    int cancelBooking(@Param("bookingId") Long bookingId);

    // S5-F10: revenue by provider specialty, with cancellation fee breakdown (§10.5.1)
    @Query(value = """
        SELECT *
        FROM (
            SELECT
                p.specialty,
                COALESCE(SUM(
                    CASE WHEN i.status IN ('COMPLETED', 'REFUNDED')
                         THEN COALESCE(CAST(i.transaction_details->>'cancellationFee' AS NUMERIC), 0)
                         ELSE 0 END
                ), 0)                                                                   AS cancellation_fee_revenue,
                COALESCE(SUM(
                    CASE WHEN i.status = 'COMPLETED'
                              THEN COALESCE(i.amount, 0)
                         WHEN i.status = 'REFUNDED'
                              THEN COALESCE(i.amount, 0)
                                   - COALESCE(CAST(i.transaction_details->>'refundAmount' AS NUMERIC), 0)
                         ELSE 0 END
                ), 0)                                                                   AS net_booking_revenue,
                COUNT(DISTINCT CASE WHEN i.status IN ('COMPLETED', 'REFUNDED')
                                    THEN b.id END)                                      AS booking_count,
                COALESCE(SUM(CASE WHEN b.status = 'CANCELLED' THEN 1 ELSE 0 END), 0)  AS cancelled_count
            FROM bookings b
            JOIN providers p     ON p.id = b.provider_id
            LEFT JOIN invoices i ON i.booking_id = b.id
            WHERE b.requested_at >= :startDate
              AND b.requested_at <= :endDate
            GROUP BY p.specialty
        ) sub
        ORDER BY (cancellation_fee_revenue + net_booking_revenue) DESC
        """, nativeQuery = true)
    List<Object[]> getRevenueByServiceType(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}
