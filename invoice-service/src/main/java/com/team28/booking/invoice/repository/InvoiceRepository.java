package com.team28.booking.invoice.repository;

import com.team28.booking.invoice.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

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