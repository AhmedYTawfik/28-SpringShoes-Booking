package com.team28.booking.invoice.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentAuditEventRepository extends MongoRepository<PaymentAuditEvent, String> {

    List<PaymentAuditEvent> findByInvoiceId(Long invoiceId);

    List<PaymentAuditEvent> findByAction(String action);
}
