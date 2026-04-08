package com.team28.booking.invoice.repository;

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
    
}