package com.team28.booking.invoice.mongo;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Repository
public class PaymentAuditEventRepositoryImpl implements PaymentAuditEventRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    public PaymentAuditEventRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<PaymentMethodBreakdown> findMethodBreakdown(LocalDateTime start, LocalDateTime end, List<String> actions) {
        Document matchDoc = new Document();
        Document timeRange = new Document("$gte", start).append("$lte", end);
        matchDoc.put("timestamp", timeRange);
        matchDoc.put("action", new Document("$in", actions));

        AggregationOperation match = context -> new Document("$match", matchDoc);

        Document condSuccess = new Document("$cond", Arrays.asList(new Document("$eq", Arrays.asList("$action", "COMPLETED")), 1, 0));
        Document condFailure = new Document("$cond", Arrays.asList(new Document("$eq", Arrays.asList("$action", "FAILED")), 1, 0));
        Document condAmount = new Document("$cond", Arrays.asList(new Document("$eq", Arrays.asList("$action", "COMPLETED")), "$amount", 0));

        Document groupFields = new Document("_id", "$method")
                .append("successCount", new Document("$sum", condSuccess))
                .append("failureCount", new Document("$sum", condFailure))
                .append("totalAmount", new Document("$sum", condAmount));

        AggregationOperation group = context -> new Document("$group", groupFields);

        Document projectFields = new Document("method", "$_id")
                .append("successCount", 1)
                .append("failureCount", 1)
                .append("totalAmount", 1)
                .append("_id", 0);

        AggregationOperation project = context -> new Document("$project", projectFields);

        Aggregation agg = Aggregation.newAggregation(match, group, project);
        AggregationResults<PaymentMethodBreakdown> results = mongoTemplate.aggregate(agg, "payment_audit_trail", PaymentMethodBreakdown.class);
        return results.getMappedResults();
    }
}
