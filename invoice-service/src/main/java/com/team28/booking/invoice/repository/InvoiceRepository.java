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
}