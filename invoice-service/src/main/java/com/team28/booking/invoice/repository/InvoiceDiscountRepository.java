package com.team28.booking.invoice.repository;

import com.team28.booking.invoice.model.InvoiceDiscount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceDiscountRepository extends JpaRepository<InvoiceDiscount, Long> {
}