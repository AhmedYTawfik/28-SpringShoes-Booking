package com.team28.booking.invoice.mongo;

import com.team28.booking.invoice.factory.MongoEvent;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "payment_audit_trail")
public class PaymentAuditEvent implements MongoEvent {

    @Id
    private String id;

    @Indexed
    private Long invoiceId;

    private String action;
    private LocalDateTime timestamp;
    private Map<String, Object> details;

    /**
     * method and amount are required (not null) for payment-shaped actions:
     * CREATED, COMPLETED, FAILED, REFUNDED, REFUND_DENIED, DISCOUNT_APPLIED, RETRY_ATTEMPTED.
     * Null is permitted only for non-payment actions (e.g. ANALYTICS_VIEWED).
     */
    private String method;
    private Double amount;

    public PaymentAuditEvent() {}

    public PaymentAuditEvent(String action, LocalDateTime timestamp, Map<String, Object> params) {
        this.action = action;
        this.timestamp = timestamp;
        this.details = new HashMap<>(params);
        this.invoiceId = params.get("invoiceId") != null
                ? ((Number) params.get("invoiceId")).longValue() : null;
        this.method = params.get("method") != null
                ? params.get("method").toString() : null;
        this.amount = params.get("amount") != null
                ? ((Number) params.get("amount")).doubleValue() : null;
    }

    @Override public String getId()                  { return id; }
    @Override public LocalDateTime getTimestamp()    { return timestamp; }
    @Override public String getAction()              { return action; }
    @Override public Map<String, Object> getDetails(){ return details; }

    public Long getInvoiceId()   { return invoiceId; }
    public String getMethod()    { return method; }
    public Double getAmount()    { return amount; }

    public void setId(String id)                            { this.id = id; }
    public void setInvoiceId(Long invoiceId)                { this.invoiceId = invoiceId; }
    public void setAction(String action)                    { this.action = action; }
    public void setTimestamp(LocalDateTime timestamp)       { this.timestamp = timestamp; }
    public void setDetails(Map<String, Object> details)     { this.details = details; }
    public void setMethod(String method)                    { this.method = method; }
    public void setAmount(Double amount)                    { this.amount = amount; }
}
