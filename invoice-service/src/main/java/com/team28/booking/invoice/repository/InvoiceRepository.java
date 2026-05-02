package com.team28.booking.invoice.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
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
        SELECT * FROM invoices i
        WHERE (CAST(:status AS text) IS NULL OR i.status = CAST(:status AS text))
        AND (CAST(:startDate AS timestamp) IS NULL OR i.created_at >= CAST(:startDate AS timestamp))
        AND (CAST(:endDate AS timestamp) IS NULL OR i.created_at <= CAST(:endDate AS timestamp))
        ORDER BY i.created_at DESC
        """, nativeQuery = true)
    List<Invoice> searchInvoices(
        @Param("status") String status,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

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

    // S5-F10: revenue grouped by booking service name, with cancellation fee from JSONB
    @Query(value = """
        SELECT
            bs.service_name                                                          AS service_type,
            COALESCE(SUM(bs.price), 0)                                               AS total_revenue,
            COUNT(DISTINCT i.id)                                                     AS invoice_count,
            COALESCE(SUM(
                COALESCE(CAST(i.transaction_details->>'cancellationFee' AS NUMERIC), 0)
            ), 0)                                                                    AS total_cancellation_fees
        FROM invoices i
        JOIN bookings b         ON b.id = i.booking_id
        JOIN booking_services bs ON bs.booking_id = b.id
        WHERE i.status IN ('COMPLETED', 'REFUNDED')
        GROUP BY bs.service_name
        ORDER BY total_revenue DESC
        """, nativeQuery = true)
    List<Object[]> getRevenueByServiceType();
}
