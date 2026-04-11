package com.team28.booking.invoice.repository;

import com.team28.booking.invoice.model.InvoiceDiscount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceDiscountRepository extends JpaRepository<InvoiceDiscount, Long> {
	@Query("""
		SELECT COUNT(id) > 0
		FROM InvoiceDiscount id
		WHERE id.invoice.id = :invoiceId AND id.discount.id = :discountId
		""")
	boolean existsByInvoiceIdAndDiscountId(@Param("invoiceId") Long invoiceId, @Param("discountId") Long discountId);
}