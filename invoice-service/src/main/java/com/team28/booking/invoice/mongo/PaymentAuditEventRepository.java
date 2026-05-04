package com.team28.booking.invoice.mongo;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentAuditEventRepository extends MongoRepository<PaymentAuditEvent, String>, PaymentAuditEventRepositoryCustom {

    List<PaymentAuditEvent> findByInvoiceId(Long invoiceId);

    List<PaymentAuditEvent> findByAction(String action);
}
