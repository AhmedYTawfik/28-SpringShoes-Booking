package com.testgen.booking;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TC379–TC425 — Design Pattern checks for Milestone 2 (Booking theme).
 *
 * <p>Seven GoF patterns: Strategy, Observer, CoR, Builder, Singleton, Factory, Adapter.
 *
 * <p><b>Architectural constraint</b>: This test-runner executes natively on the host
 * and talks to student services via HTTP. Student JARs are NOT on this JVM's classpath —
 * {@code Class.forName()} is impossible. All structural checks therefore use source-file
 * scanning via the helpers in {@link TestBase#allJavaFiles()} /
 * {@link TestBase#readClassSource(String)} / {@link TestBase#anySourceContains(String)}.
 *
 * <p><b>Booking-specific notes</b>:
 * <ul>
 *   <li>S5-F12 endpoint: {@code POST /api/invoices/{id}/refund-cancellation}</li>
 *   <li>Strategy selection uses booking.{@code status} + booking.{@code appointmentDate}
 *       (24h cutoff) — NOT a 30-day return window.</li>
 *   <li>Five MongoEvent subtypes: AuthEvent, ProviderEvent, BookingEvent,
 *       CalendarEvent, PaymentAuditEvent.</li>
 *   <li>EventType enum: AUTH, PROVIDER, BOOKING, CALENDAR, PAYMENT_AUDIT.</li>
 *   <li>Service-slot routing per per-theme replication recipe:
 *       userServiceUrl→user, catalogServiceUrl→provider, orderServiceUrl→booking,
 *       deliveryServiceUrl→calendar, checkoutServiceUrl→invoice.</li>
 * </ul>
 */
public class DP_PatternTests extends TestBase {

    // ═══════════════════════════════════════════════════════════════════════════
    // DP-1  STRATEGY  (TC379–TC385) — invoice-service / S5-F12 cancellation refund
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("TC379 — DP-1 Strategy: RefundStrategy interface exists")
    void tc379_refundStrategyInterfaceExists() throws Exception {
        BASE_URL = checkoutServiceUrl;
        String src = readClassSource("RefundStrategy");
        assertFalse(src.isEmpty(),
            "TC379: RefundStrategy.java not found in src/main/java. " +
            "Per spec §3.2, invoice-service must declare: interface RefundStrategy { … calculateRefund(…); }");
        assertTrue(src.contains("interface RefundStrategy"),
            "TC379: RefundStrategy must be declared as an interface, not a class. " +
            "Found head: " + src.substring(0, Math.min(300, src.length())));
        assertTrue(src.contains("calculateRefund"),
            "TC379: RefundStrategy interface must declare a method named 'calculateRefund'.");
    }

    @Test
    @DisplayName("TC380 — DP-1 Strategy: 3 concrete strategies implement RefundStrategy")
    void tc380_threeConcreteStrategies() throws Exception {
        BASE_URL = checkoutServiceUrl;
        String[] names = { s5StrategyFullRefund(), s5StrategyFoodOnly(), s5StrategyNoRefund() };
        for (String name : names) {
            String src = readClassSource(name);
            assertFalse(src.isEmpty(),
                "TC380: " + name + ".java not found in src/main/java. " +
                "All three named strategy classes must exist per spec §3.2.");
            assertTrue(src.contains("implements RefundStrategy") ||
                       src.contains("implements RefundStrategy,") ||
                       src.contains("implements RefundStrategy "),
                "TC380: " + name + " must implement RefundStrategy. " +
                "Found class head: " + src.substring(0, Math.min(300, src.length())));
        }
    }

    @Test
    @DisplayName("TC381 — DP-1 Strategy: RefundStrategySelector exists")
    void tc381_refundStrategySelectorExists() throws Exception {
        BASE_URL = checkoutServiceUrl;
        boolean selectorFound = anySourceContains("RefundStrategySelector") ||
                                anySourceContains("RefundStrategyFactory");
        assertTrue(selectorFound,
            "TC381: No RefundStrategySelector or RefundStrategyFactory found in src/main/java. " +
            "Per spec §3.2, dispatch logic must live in a separate selector/factory class.");
        boolean returnsStrategy =
            anySourceContains("RefundStrategy select(") ||
            anySourceContains("RefundStrategy getStrategy(") ||
            anySourceContains("RefundStrategy choose(") ||
            anySourceContains("RefundStrategy resolve(") ||
            anySourceContains(": RefundStrategy") ||
            anySourceContains("RefundStrategy selectStrategy");
        assertTrue(returnsStrategy,
            "TC381: RefundStrategySelector must expose a method that returns RefundStrategy. " +
            "Per spec, the service calls selector.select(…).calculateRefund(…) polymorphically.");
    }

    @Test
    @DisplayName("TC382 — DP-1 Strategy: FullRefundStrategy audit trail (appointment >24h)")
    void tc382_fullRefundAuditTrail() throws Exception {
        BASE_URL = checkoutServiceUrl;
        // CONFIRMED booking, appointmentDate = +3 days (>24h) → FullRefundStrategy
        String farFuture = java.time.LocalDate.now().plusDays(3).toString();
        long providerId = _BkM2S5.provSpec(this, "Dentist");
        Long bid = _BkM2.bk(this, 1L, providerId, "CONFIRMED", farFuture);
        Long iid = _BkM2S5.invWithDetails(this, bid, 1L, 200.0, "COMPLETED", null);
        String tok = adminToken();
        String body = "{\"reason\":\"schedule_conflict\"}";
        HttpResponse<String> rsp = httpPostAuth(
                "/api/invoices/" + iid + "/refund-cancellation", body, tok);
        assert2xx(rsp, "TC382 FullRefund POST");
        // Verify strategy recorded in PG transactionDetails JSONB
        String iTable = tableName("Invoice");
        String details = jdbc.queryForObject(
            "SELECT transaction_details::text FROM \"" + iTable + "\" WHERE id = ?",
            String.class, iid);
        assertNotNull(details,
            "TC382: transactionDetails must be populated after FullRefundStrategy; invoiceId=" + iid);
        assertTrue(details.contains(s5StrategyFullRefund()),
            "TC382: transactionDetails.strategyName must be " + s5StrategyFullRefund() +
            "; got " + details);
        // Also check MongoDB audit if available
        if (mongo != null) {
            com.mongodb.client.MongoCollection<org.bson.Document> col =
                    mongo.getCollection(s5AuditCollection());
            org.bson.Document latest = col
                    .find(new org.bson.Document("invoiceId", iid))
                    .sort(new org.bson.Document("_id", -1)).first();
            if (latest == null)
                latest = col.find().sort(new org.bson.Document("_id", -1)).first();
            if (latest != null) {
                String strat = latest.getString("strategyName");
                if (strat == null) {
                    org.bson.Document det = latest.get("details", org.bson.Document.class);
                    if (det != null) strat = det.getString("strategyName");
                }
                if (strat != null)
                    assertEquals(s5StrategyFullRefund(), strat,
                        "TC382: audit strategyName must be " + s5StrategyFullRefund() +
                        "; got=" + strat);
            }
        }
    }

    @Test
    @DisplayName("TC383 — DP-1 Strategy: PartialRefundStrategy audit trail (appointment ≤24h)")
    void tc383_partialRefundAuditTrail() throws Exception {
        BASE_URL = checkoutServiceUrl;
        // CONFIRMED booking, appointmentDate = today (≤24h) → PartialRefundStrategy
        String today = java.time.LocalDate.now().toString();
        long providerId = _BkM2S5.provSpec(this, "Tutor");
        Long bid = _BkM2.bk(this, 1L, providerId, "CONFIRMED", today);
        Long iid = _BkM2S5.invWithDetails(this, bid, 1L, 300.0, "COMPLETED", null);
        String tok = adminToken();
        String body = "{\"reason\":\"schedule_conflict\"}";
        HttpResponse<String> rsp = httpPostAuth(
                "/api/invoices/" + iid + "/refund-cancellation", body, tok);
        assert2xx(rsp, "TC383 PartialRefund POST");
        String iTable = tableName("Invoice");
        String details = jdbc.queryForObject(
            "SELECT transaction_details::text FROM \"" + iTable + "\" WHERE id = ?",
            String.class, iid);
        assertNotNull(details,
            "TC383: transactionDetails must be populated after PartialRefundStrategy; invoiceId=" + iid);
        assertTrue(details.contains(s5StrategyFoodOnly()),
            "TC383: transactionDetails.strategyName must be " + s5StrategyFoodOnly() +
            "; got " + details);
        if (mongo != null) {
            com.mongodb.client.MongoCollection<org.bson.Document> col =
                    mongo.getCollection(s5AuditCollection());
            org.bson.Document latest = col
                    .find(new org.bson.Document("invoiceId", iid))
                    .sort(new org.bson.Document("_id", -1)).first();
            if (latest == null)
                latest = col.find().sort(new org.bson.Document("_id", -1)).first();
            if (latest != null) {
                String strat = latest.getString("strategyName");
                if (strat == null) {
                    org.bson.Document det = latest.get("details", org.bson.Document.class);
                    if (det != null) strat = det.getString("strategyName");
                }
                if (strat != null)
                    assertEquals(s5StrategyFoodOnly(), strat,
                        "TC383: audit strategyName must be " + s5StrategyFoodOnly() +
                        "; got=" + strat);
            }
        }
    }

    @Test
    @DisplayName("TC384 — DP-1 Strategy: NoRefundStrategy 400 + REFUND_DENIED audit (IN_PROGRESS)")
    void tc384_noRefundAuditOnDenial() throws Exception {
        BASE_URL = checkoutServiceUrl;
        if (mongo == null) throw new AssertionError(
            "TC384: MongoDB required — REFUND_DENIED audit check needs " + s5AuditCollection() + " collection.");
        // IN_PROGRESS booking → NoRefundStrategy → 400
        long providerId = _BkM2S5.provSpec(this, "Barber");
        Long bid = _BkM2.bk(this, 1L, providerId, "IN_PROGRESS", "2026-04-10");
        Long iid = _BkM2S5.invWithDetails(this, bid, 1L, 100.0, "COMPLETED", null);
        String tok = adminToken();
        String body = "{\"reason\":\"too late\"}";
        HttpResponse<String> rsp = httpPostAuth(
                "/api/invoices/" + iid + "/refund-cancellation", body, tok);
        assertEquals(400, rsp.statusCode(),
            "TC384: IN_PROGRESS booking must return 400; got=" + rsp.statusCode() +
            " body=" + rsp.body());
        // NOTE: do not assert on response body — Spring Boot 4 strips
        // ResponseStatusException reason from the default error body unless
        // server.error.include-message=always is set. The denial reason is
        // verified via the Mongo audit event below instead.
        com.mongodb.client.MongoCollection<org.bson.Document> col =
                mongo.getCollection(s5AuditCollection());
        boolean foundDenied = false;
        for (org.bson.Document d : col.find()) {
            if (bsonContainsString(d, "REFUND_DENIED") && bsonContainsLong(d, iid)) {
                foundDenied = true;
                break;
            }
        }
        assertTrue(foundDenied,
            "TC384: REFUND_DENIED event referencing invoiceId=" + iid +
            " must be logged to '" + s5AuditCollection() +
            "' BEFORE the 400 is thrown (per spec §3.2 step f / §10 S5-F12 step c).");
    }

    @Test
    @DisplayName("TC385 — DP-1 Strategy: refund service has no if-else on booking status")
    void tc385_noIfElseOnBookingStatusInService() throws Exception {
        BASE_URL = checkoutServiceUrl;
        for (java.nio.file.Path p : allJavaFiles()) {
            if (p.toString().contains("/src/test/")) continue;
            String fname = p.getFileName().toString();
            // Only inspect service-layer files
            if (!fname.endsWith("Service.java") && !fname.endsWith("ServiceImpl.java"))
                continue;
            // Skip the strategy/selector/factory files
            if (fname.contains("Strategy") || fname.contains("Selector") ||
                fname.contains("Factory") || fname.contains("Refund"))
                continue;
            String content;
            try { content = java.nio.file.Files.readString(p); }
            catch (java.io.IOException e) { continue; }
            // Only look at files that actually deal with cancellation refund
            if (!content.contains("refund-cancellation") &&
                !content.contains("cancellationRefund") &&
                !content.contains("CancellationRefund"))
                continue;
            // Catch direct branching on booking status / appointmentDate inside the service
            assertFalse(content.contains("status == ") && content.contains("IN_PROGRESS"),
                "TC385: " + fname + " branches on booking status (IN_PROGRESS) directly. " +
                "Per spec §3.2 step g, this decision belongs in RefundStrategySelector, not the service.");
            assertFalse(content.contains("appointmentDate.minusHours(24)") ||
                        content.contains("appointmentDate.plusHours(24)") ||
                        content.contains("Duration.between(") && content.contains("appointmentDate"),
                "TC385: " + fname + " branches on appointmentDate directly. " +
                "Per spec §3.2, appointmentDate-vs-now arithmetic belongs in RefundStrategySelector.");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DP-2  OBSERVER  (TC386–TC392)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("TC386 — DP-2 Observer: EntityObserver interface")
    void tc386_entityObserverInterface() throws Exception {
        BASE_URL = userServiceUrl;
        String src = readClassSource("EntityObserver");
        assertFalse(src.isEmpty(),
            "TC386: EntityObserver.java not found in src/main/java. " +
            "Required: interface EntityObserver { void onEvent(String eventType, Object payload); }");
        assertTrue(src.contains("interface EntityObserver"),
            "TC386: EntityObserver must be declared as an interface; found head: " +
            src.substring(0, Math.min(300, src.length())));
        assertTrue(src.contains("onEvent"),
            "TC386: EntityObserver must declare onEvent(…) method.");
    }

    @Test
    @DisplayName("TC387 — DP-2 Observer: MongoEventLogger implements EntityObserver")
    void tc387_mongoEventLoggerImplementsObserver() throws Exception {
        BASE_URL = userServiceUrl;
        String src = readClassSource("MongoEventLogger");
        assertFalse(src.isEmpty(),
            "TC387: MongoEventLogger.java not found in src/main/java.");
        assertTrue(src.contains("implements EntityObserver") ||
                   src.contains("implements EntityObserver,") ||
                   src.contains("implements EntityObserver "),
            "TC387: MongoEventLogger must implement EntityObserver; found head: " +
            src.substring(0, Math.min(300, src.length())));
    }

    @Test
    @DisplayName("TC388 — DP-2 Observer: no @EventListener writes to MongoDB (Spring vs GoF)")
    void tc388_noSpringEventListenerOnMongoPath() throws Exception {
        BASE_URL = userServiceUrl;
        for (java.nio.file.Path p : allJavaFiles()) {
            if (p.toString().contains("/src/test/")) continue;
            String content;
            try { content = java.nio.file.Files.readString(p); }
            catch (java.io.IOException e) { continue; }
            if (!content.contains("@EventListener")) continue;
            boolean writesToMongo =
                content.contains("mongoTemplate") ||
                content.contains("MongoTemplate")  ||
                content.contains("mongoRepository") ||
                content.contains("MongoRepository") ||
                (content.contains(".save(") && (content.contains("mongo") || content.contains("Mongo"))) ||
                (content.contains(".insert(") && (content.contains("mongo") || content.contains("Mongo")));
            assertFalse(writesToMongo,
                "TC388: " + p.getFileName() + " has @EventListener AND MongoDB writes. " +
                "Per spec §3.3, event logging must go through the GoF Observer chain — " +
                "not Spring's @EventListener/@ApplicationEventPublisher.");
        }
    }

    @Test
    @DisplayName("TC389 — DP-2 Observer: register triggers REGISTERED in auth_events")
    void tc389_registerTriggersAuthEvent() throws Exception {
        BASE_URL = userServiceUrl;
        if (mongo == null) throw new AssertionError("TC389: MongoDB required.");
        com.mongodb.client.MongoCollection<org.bson.Document> col =
                mongo.getCollection("auth_events");
        long before = col.countDocuments();
        Map<String, Object> u = seedAndLoginUser("tc389reg");
        long uid = (long)(Number) u.get("id");
        long after = col.countDocuments();
        assertTrue(after > before,
            "TC389: auth_events must grow after registration. " +
            "before=" + before + " after=" + after + ". Observer chain not wired to register controller.");
        // Filter by REGISTERED — seedAndLoginUser fires both REGISTERED + LOGGED_IN
        org.bson.Document doc = col
                .find(new org.bson.Document("userId", uid).append("action", "REGISTERED"))
                .first();
        if (doc == null)
            doc = col.find(new org.bson.Document("action", "REGISTERED"))
                    .sort(new org.bson.Document("_id", -1)).first();
        assertNotNull(doc, "TC389: No REGISTERED auth_events doc for userId=" + uid);
        String action = doc.getString("action");
        if (action == null) action = doc.getString("eventType");
        if (action != null)
            assertEquals("REGISTERED", action,
                "TC389: event action must be REGISTERED; got=" + action);
    }

    @Test
    @DisplayName("TC390 — DP-2 Observer: login triggers LOGGED_IN")
    void tc390_loginTriggersLoggedInEvent() throws Exception {
        BASE_URL = userServiceUrl;
        if (mongo == null) throw new AssertionError("TC390: MongoDB required.");
        Map<String, Object> u = seedAndLoginUser("tc390login");
        long uid  = (long)(Number) u.get("id");
        String email = (String) u.get("email");
        com.mongodb.client.MongoCollection<org.bson.Document> col =
                mongo.getCollection("auth_events");
        long before = col.countDocuments(new org.bson.Document("userId", uid));
        String loginBody = String.format("{\"email\":\"%s\",\"password\":\"UserPwd!2026\"}", email);
        HttpResponse<String> loginResp = http.send(
                java.net.http.HttpRequest.newBuilder(java.net.URI.create(userServiceUrl + "/api/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(loginBody)).build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        assert2xx(loginResp, "TC390 second login");
        long after = col.countDocuments(new org.bson.Document("userId", uid));
        assertTrue(after > before,
            "TC390: auth_events must grow on login for userId=" + uid +
            "; before=" + before + " after=" + after);
        org.bson.Document latest = col
                .find(new org.bson.Document("userId", uid))
                .sort(new org.bson.Document("_id", -1)).first();
        if (latest != null) {
            String action = latest.getString("action");
            if (action == null) action = latest.getString("eventType");
            if (action != null)
                assertEquals("LOGGED_IN", action,
                    "TC390: latest event for userId=" + uid + " must be LOGGED_IN; got=" + action);
        }
    }

    @Test
    @DisplayName("TC391 — DP-2 Observer: M1 retrofit (S1-F2) emits event")
    void tc391_m1RetrofitEmitsEvent() throws Exception {
        BASE_URL = userServiceUrl;
        if (mongo == null) throw new AssertionError("TC391: MongoDB required.");
        Map<String, Object> u = seedAndLoginUser("tc391pref");
        long uid = (long)(Number) u.get("id");
        String tok = (String) u.get("token");
        com.mongodb.client.MongoCollection<org.bson.Document> col =
                mongo.getCollection("auth_events");
        long before = col.countDocuments(new org.bson.Document("userId", uid));
        HttpResponse<String> r = httpPutAuth(
                "/api/users/" + uid + "/preferences", "{\"language\":\"fr\"}", tok);
        assert2xx(r, "TC391 PUT /api/users/{id}/preferences");
        long after = col.countDocuments(new org.bson.Document("userId", uid));
        assertTrue(after > before,
            "TC391: M1 S1-F2 (PUT preferences) must emit an observer event. " +
            "before=" + before + " after=" + after + " for userId=" + uid +
            ". The M2 Observer retrofit must cover M1 endpoints too.");
    }

    @Test
    @DisplayName("TC392 — DP-2 Observer: unregister method exists (chain load-bearing check)")
    void tc392_unregisterMethodExists() throws Exception {
        BASE_URL = userServiceUrl;
        boolean hasUnregister =
            anySourceContains("removeObserver(") ||
            anySourceContains("unregisterObserver(") ||
            anySourceContains("detachObserver(")   ||
            anySourceContains("unregister(EntityObserver") ||
            anySourceContains("detach(EntityObserver");
        assertTrue(hasUnregister,
            "TC392: No removeObserver / unregisterObserver / detachObserver method found. " +
            "The Observer subject must support observer removal to prove the chain is load-bearing. " +
            "Without it, there is no way to verify that MongoDB writes go through observers rather than " +
            "being direct calls hidden alongside notifyObservers(…).");
        boolean hasNotify =
            anySourceContains("notifyObservers(") ||
            anySourceContains("notifyObserver(");
        assertTrue(hasNotify,
            "TC392: No notifyObservers(…) call found in any service class. " +
            "The subject must call notifyObservers(…) to dispatch events through the chain.");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DP-3  CHAIN OF RESPONSIBILITY  (TC393–TC400)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("TC393 — DP-3 CoR: AuthHandler base + setNext/handle")
    void tc393_authHandlerBaseExists() throws Exception {
        BASE_URL = userServiceUrl;
        String src = readClassSource("AuthHandler");
        assertFalse(src.isEmpty(),
            "TC393: AuthHandler.java not found in src/main/java. " +
            "Required: abstract class (or interface) with setNext(AuthHandler) and handle(…) methods.");
        assertTrue(src.contains("setNext"),
            "TC393: AuthHandler must declare setNext(AuthHandler) — the chain-linkage primitive.");
        assertTrue(src.contains("handle"),
            "TC393: AuthHandler must declare handle(…) — the chain dispatch method.");
    }

    @Test
    @DisplayName("TC394 — DP-3 CoR: ≥3 concrete AuthHandler subclasses")
    void tc394_atLeastThreeConcreteHandlers() throws Exception {
        BASE_URL = userServiceUrl;
        long count = allJavaFiles().stream()
                .filter(p -> !p.toString().contains("/src/test/"))
                .filter(p -> !p.getFileName().toString().equals("AuthHandler.java"))
                .filter(p -> {
                    try {
                        String c = java.nio.file.Files.readString(p);
                        return c.contains("extends AuthHandler") ||
                               c.contains("implements AuthHandler");
                    } catch (java.io.IOException e) { return false; }
                })
                .count();
        assertTrue(count >= 3,
            "TC394: Expected ≥3 concrete AuthHandler subclasses; found=" + count + ". " +
            "Per spec §3.4: TokenExtractionHandler, SignatureValidationHandler, UserLoaderHandler " +
            "(+ optional RoleAuthorizationHandler).");
    }

    @Test
    @DisplayName("TC395 — DP-3 CoR: missing Authorization → 401")
    void tc395_missingAuthHeader401() throws Exception {
        BASE_URL = userServiceUrl;
        HttpResponse<String> r = httpGet("/api/users/1");
        assertEquals(401, r.statusCode(),
            "TC395: GET /api/users/1 without Authorization header must return 401 " +
            "(TokenExtractionHandler short-circuits); got=" + r.statusCode());
    }

    @Test
    @DisplayName("TC396 — DP-3 CoR: invalid signature → 401")
    void tc396_invalidSignature401() throws Exception {
        BASE_URL = userServiceUrl;
        HttpResponse<String> r = httpGetWithRawAuth("/api/users/1", "Bearer xxx.yyy.zzz");
        assertEquals(401, r.statusCode(),
            "TC396: GET /api/users/1 with malformed/invalid token must return 401 " +
            "(SignatureValidationHandler); got=" + r.statusCode());
    }

    @Test
    @DisplayName("TC397 — DP-3 CoR: deleted user with valid token → 401")
    void tc397_deletedUserToken401() throws Exception {
        BASE_URL = userServiceUrl;
        Map<String, Object> u = seedAndLoginUser("tc397del");
        long uid = (long)(Number) u.get("id");
        String tok = (String) u.get("token");
        String userTable = tableName("User");
        // Fresh user has no bookings/invoices — DELETE should succeed; fall back to DEACTIVATE
        try {
            jdbc.update("DELETE FROM \"" + userTable + "\" WHERE id = ?", uid);
        } catch (org.springframework.dao.DataAccessException ex) {
            String statusCol = columnByField("User", "status");
            jdbc.update("UPDATE \"" + userTable + "\" SET \"" + statusCol +
                        "\" = 'DEACTIVATED' WHERE id = ?", uid);
        }
        HttpResponse<String> r = httpGetAuth("/api/users/" + uid, tok);
        assertEquals(401, r.statusCode(),
            "TC397: Valid token for a deleted/deactivated user must return 401 " +
            "(UserLoaderHandler must re-check PG on every request); got=" + r.statusCode());
    }

    @Test
    @DisplayName("TC398 — DP-3 CoR: ADMIN-only endpoint with CLIENT token → 403")
    void tc398_clientTokenOnAdminEndpoint403() throws Exception {
        BASE_URL = userServiceUrl;
        Map<String, Object> u = seedAndLoginUser("tc398cli");
        String tok = (String) u.get("token");
        long uid = (long)(Number) u.get("id");
        HttpResponse<String> r = httpPutAuth("/api/users/" + uid + "/role",
                "{\"role\":\"ADMIN\"}", tok);
        assertEquals(403, r.statusCode(),
            "TC398: CLIENT PUT /api/users/{id}/role must return 403 " +
            "(RoleAuthorizationHandler — authenticated but forbidden); got=" + r.statusCode());
    }

    @Test
    @DisplayName("TC399 — DP-3 CoR: ADMIN-only with ADMIN token → 2xx")
    void tc399_adminTokenOnAdminEndpoint200() throws Exception {
        BASE_URL = userServiceUrl;
        Map<String, Object> u = seedAndLoginUser("tc399target");
        long targetId = (long)(Number) u.get("id");
        HttpResponse<String> r = httpPutAuth("/api/users/" + targetId + "/role",
                "{\"role\":\"ADMIN\"}", adminToken());
        assertTrue(r.statusCode() >= 200 && r.statusCode() < 300,
            "TC399: ADMIN PUT /api/users/{id}/role must succeed (2xx — chain passes through); " +
            "got=" + r.statusCode() + " body=" + r.body());
    }

    @Test
    @DisplayName("TC400 — DP-3 CoR: filter delegates to chain head (source scan)")
    void tc400_filterDelegatesToChainHead() throws Exception {
        BASE_URL = userServiceUrl;
        String src = readClassSource("JwtAuthenticationFilter");
        assertFalse(src.isEmpty(),
            "TC400: JwtAuthenticationFilter.java not found in src/main/java.");
        boolean invokesChain =
            src.contains("head.handle(")       ||
            src.contains("authHandler.handle(") ||
            src.contains("chainHead.handle(")   ||
            src.contains("handler.handle(")     ||
            src.contains("first.handle(")       ||
            src.contains("chain.handle(")       ||
            src.contains(".handle(ctx")         ||
            src.contains(".handle(context")     ||
            src.contains(".handle(request")     ||
            src.contains(".handle(auth");
        assertTrue(invokesChain,
            "TC400: JwtAuthenticationFilter.doFilterInternal must delegate to the chain head " +
            "(e.g., head.handle(ctx)). The chain is dead code if the filter does validation inline.");
        boolean hasInlineJwts =
            src.contains("Jwts.parser()") || src.contains("Jwts.parserBuilder()");
        assertFalse(hasInlineJwts,
            "TC400: JwtAuthenticationFilter calls Jwts.parser/parserBuilder directly. " +
            "Token signature validation must be in SignatureValidationHandler, not the filter.");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DP-4  BUILDER  (TC401–TC405)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("TC401 — DP-4 Builder: M2 dashboard DTOs have builder()")
    void tc401_m2DashboardDtosHaveBuilder() throws Exception {
        BASE_URL = userServiceUrl;
        // Per spec §3.5: ProviderDashboardDTO (S2-F12), BookingAnalyticsDashboardDTO (S3-F10),
        // CalendarAnalyticsDTO (S4-F10), ServiceTypeRevenueDTO (S5-F10).
        String[] dtos = {
            "ProviderDashboardDTO",
            "BookingAnalyticsDashboardDTO",
            "CalendarAnalyticsDTO",
            "ServiceTypeRevenueDTO"
        };
        List<String> noBuilder = new ArrayList<>();
        for (String dto : dtos) {
            String src = readClassSource(dto);
            if (src.isEmpty()) continue; // DTO may not exist yet — structural absence caught by TC402
            boolean hasBuilder =
                src.contains("builder()") ||
                src.contains("class Builder") ||
                src.contains("Builder<")  ||
                src.contains("new Builder(");
            if (!hasBuilder) noBuilder.add(dto);
        }
        assertTrue(noBuilder.isEmpty(),
            "TC401: M2 dashboard DTOs missing Builder pattern: " + noBuilder +
            ". Per spec §3.5, all 5-plus-field analytics DTOs must expose static builder() + fluent build().");
    }

    @Test
    @DisplayName("TC402 — DP-4 Builder: M1 in-scope DTOs have Builder (16 DTOs)")
    void tc402_m1DtosHaveBuilder() throws Exception {
        BASE_URL = userServiceUrl;
        // 16 DTO-returning M1 features (excludes S1-F9 and S4-F6 which return entities, not DTOs;
        // also excludes S2-F8 and S3-F8 explicitly excluded by M2 spec §3.5).
        String[] dtos = {
            "UserBookingSummaryDTO",     // S1-F3
            "TopClientDTO",              // S1-F6
            "UserProfileDTO",            // S1-F8
            "ProviderEarningsDTO",       // S2-F3
            "TopProviderDTO",            // S2-F6
            "ProviderCertAlertDTO",      // S2-F9
            "BookingEstimateDTO",        // S3-F3
            "BookingAnalyticsDTO",       // S3-F6 (M1 — distinct from M2's BookingAnalyticsDashboardDTO)
            "BookingDetailsDTO",         // S3-F9
            "AvailableProviderDTO",      // S4-F3
            "ProviderUtilizationDTO",    // S4-F8
            "IdleProviderDTO",           // S4-F9
            "UserInvoiceSummaryDTO",     // S5-F3
            "RevenueReportDTO",          // S5-F6
            "InvoiceDetailsDTO",         // S5-F8
            "DiscountUsageDTO"           // S5-F9
        };
        List<String> noBuilder = new ArrayList<>();
        for (String dto : dtos) {
            String src = readClassSource(dto);
            if (src.isEmpty()) { noBuilder.add(dto + "(file not found)"); continue; }
            boolean hasBuilder =
                src.contains("builder()") ||
                src.contains("class Builder") ||
                src.contains("Builder<");
            if (!hasBuilder) noBuilder.add(dto);
        }
        assertTrue(noBuilder.isEmpty(),
            "TC402: These M1 DTOs are missing Builder: " + noBuilder +
            ". Per spec §3.5, all 16 DTO-returning M1 features must be retrofitted with Builder. " +
            "Excluded (entity-returners): S1-F9, S2-F8, S3-F8, S4-F6.");
    }

    @Test
    @DisplayName("TC403 — DP-4 Builder: M2 provider dashboard still works after Builder retrofit")
    void tc403_m2DashboardRegressionAfterBuilderRetrofit() throws Exception {
        BASE_URL = catalogServiceUrl;  // provider-service
        String adminTok = adminToken();
        // Spec §10 S2-F12: GET /api/providers/{id}/dashboard returns ProviderDashboardDTO.
        // Use the existing _BkM2 helpers to seed a provider, then call the dashboard.
        long providerId = _BkM2S5.provSpec(this, "Dentist");
        // Seed a few COMPLETED bookings for total revenue
        for (double amt : new double[]{ 100.0, 200.0, 300.0 }) {
            Long bid = _BkM2.bk(this, 1L, providerId, "COMPLETED", "2026-04-15");
            _BkM2.inv(this, bid, 1L, amt, "COMPLETED", "2026-04-15");
        }
        HttpResponse<String> dash = httpGetAuth(
                "/api/providers/" + providerId + "/dashboard", adminTok);
        assert2xx(dash, "TC403 GET provider dashboard");
        JsonNode j = parseNode(dash.body());
        assertTrue(j.has("totalBookings") || j.has("total_bookings"),
            "TC403: provider dashboard missing totalBookings after Builder retrofit; body=" + dash.body());
        assertTrue(j.has("totalRevenue") || j.has("total_revenue"),
            "TC403: provider dashboard missing totalRevenue after Builder retrofit; body=" + dash.body());
    }

    @Test
    @DisplayName("TC404 — DP-4 Builder: M1 retrofit doesn't break behavior (S1-F3 booking-summary)")
    void tc404_m1RetrofitBehaviorUnchanged() throws Exception {
        BASE_URL = userServiceUrl;
        Map<String, Object> u = seedAndLoginUser("tc404s1f3");
        long uid = (long)(Number) u.get("id");
        String tok = (String) u.get("token");
        HttpResponse<String> r = httpGetAuth("/api/users/" + uid + "/booking-summary", tok);
        assert2xx(r, "TC404 S1-F3 booking-summary GET");
        JsonNode j = parseNode(r.body());
        assertTrue(j.has("totalBookings") || j.has("total_bookings") || j.has("bookingCount"),
            "TC404: booking-summary missing totalBookings after Builder retrofit; body=" + r.body());
        assertTrue(j.has("totalSpent") || j.has("total_spent") || j.has("totalAmount"),
            "TC404: booking-summary missing totalSpent after Builder retrofit; body=" + r.body());
    }

    @Test
    @DisplayName("TC405 — DP-4 Builder: Provider and Booking entities do NOT use Builder")
    void tc405_entityClassesExemptFromBuilder() throws Exception {
        BASE_URL = userServiceUrl;
        // S2-F8 (Verify Provider Certification) → returns entity, not DTO → no Builder needed.
        // S3-F8 (Add Services to Existing Booking) → returns entity, not DTO → no Builder needed.
        // Check that entity classes don't declare builder() — if they do, students over-applied
        // the Builder pattern to non-DTOs.
        for (String entityName : new String[]{"Provider", "Booking"}) {
            String src = readClassSource(entityName);
            if (src.isEmpty()) continue;
            assertFalse(src.contains("static class Builder") ||
                        src.contains("class Builder<"),
                "TC405: " + entityName + " entity must NOT have a Builder inner class. " +
                "S2-F8 (verify certification) and S3-F8 (add services) return entities directly — Builder is " +
                "only required for DTOs (per spec §3.5).");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DP-5  SINGLETON  (TC406–TC411)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("TC406 — DP-5 Singleton: JwtConfigurationManager has private constructor")
    void tc406_jwtConfigMgrPrivateConstructor() throws Exception {
        BASE_URL = userServiceUrl;
        String src = readClassSource("JwtConfigurationManager");
        assertFalse(src.isEmpty(),
            "TC406: JwtConfigurationManager.java not found in src/main/java.");
        assertTrue(src.contains("private JwtConfigurationManager("),
            "TC406: JwtConfigurationManager must have a private no-arg constructor. " +
            "Public constructors allow callers to bypass the singleton. " +
            "Found head: " + src.substring(0, Math.min(500, src.length())));
    }

    @Test
    @DisplayName("TC407 — DP-5 Singleton: getInstance() is public static")
    void tc407_getInstanceIsPublicStatic() throws Exception {
        BASE_URL = userServiceUrl;
        String src = readClassSource("JwtConfigurationManager");
        assertFalse(src.isEmpty(), "TC407: JwtConfigurationManager.java not found.");
        assertTrue(src.contains("getInstance()"),
            "TC407: JwtConfigurationManager must expose a public getInstance() method.");
        boolean isPublicStatic =
            src.contains("public static JwtConfigurationManager getInstance()") ||
            src.contains("public static synchronized JwtConfigurationManager getInstance()") ||
            (src.contains("public static") && src.contains("getInstance()"));
        assertTrue(isPublicStatic,
            "TC407: getInstance() must be declared public static. " +
            "Found head: " + src.substring(0, Math.min(500, src.length())));
    }

    @Test
    @DisplayName("TC408 — DP-5 Singleton: same reference — static instance field present")
    void tc408_staticInstanceFieldPresent() throws Exception {
        BASE_URL = userServiceUrl;
        String src = readClassSource("JwtConfigurationManager");
        assertFalse(src.isEmpty(), "TC408: JwtConfigurationManager.java not found.");
        boolean hasStaticInstance =
            src.contains("static JwtConfigurationManager instance")  ||
            src.contains("static JwtConfigurationManager INSTANCE")  ||
            src.contains("static final JwtConfigurationManager")     ||
            src.contains("private static JwtConfigurationManager")   ||
            src.contains("static volatile JwtConfigurationManager");
        assertTrue(hasStaticInstance,
            "TC408: JwtConfigurationManager must store the instance in a static field " +
            "(e.g., private static JwtConfigurationManager instance). " +
            "Without it, each getInstance() call allocates a new object — not a singleton.");
    }

    @Test
    @DisplayName("TC409 — DP-5 Singleton: thread-safe initialization")
    void tc409_threadSafeInitialization() throws Exception {
        BASE_URL = userServiceUrl;
        String src = readClassSource("JwtConfigurationManager");
        assertFalse(src.isEmpty(), "TC409: JwtConfigurationManager.java not found.");
        boolean threadSafe =
            src.contains("static final JwtConfigurationManager")  ||   // eager — always safe
            src.contains("volatile JwtConfigurationManager")        ||   // double-checked locking
            src.contains("synchronized JwtConfigurationManager getInstance") ||
            (src.contains("synchronized") && src.contains("getInstance"));
        assertTrue(threadSafe,
            "TC409: JwtConfigurationManager.getInstance() is not thread-safe. " +
            "Use eager init (static final), volatile + double-checked locking, or synchronized. " +
            "Lazy init without synchronisation lets concurrent first-callers each see instance==null " +
            "and each call new(), producing multiple instances.");
    }

    @Test
    @DisplayName("TC410 — DP-5 Singleton: NOT a Spring bean")
    void tc410_notASpringBean() throws Exception {
        BASE_URL = userServiceUrl;
        String src = readClassSource("JwtConfigurationManager");
        assertFalse(src.isEmpty(), "TC410: JwtConfigurationManager.java not found.");
        for (String ann : new String[]{"@Component", "@Service", "@Configuration",
                                       "@Bean", "@Repository", "@ManagedBean"}) {
            assertFalse(src.contains(ann),
                "TC410: JwtConfigurationManager must NOT carry " + ann + ". " +
                "It is a classical GoF Singleton — Spring must not manage its lifecycle. " +
                "Mixing Spring bean lifecycle with a manual getInstance() can produce two instances.");
        }
    }

    @Test
    @DisplayName("TC411 — DP-5 Singleton: JwtService reads via getInstance() — integration")
    void tc411_jwtServiceUsesGetInstance() throws Exception {
        BASE_URL = userServiceUrl;
        String jwtSvc = readClassSource("JwtService");
        if (!jwtSvc.isEmpty()) {
            assertTrue(jwtSvc.contains("JwtConfigurationManager.getInstance()") ||
                       jwtSvc.contains("getInstance()"),
                "TC411: JwtService must read config via JwtConfigurationManager.getInstance(). " +
                "Using @Autowired or @Value bypasses the singleton — secret may differ from the one " +
                "used at token-issue time. Found head: " + jwtSvc.substring(0, Math.min(400, jwtSvc.length())));
        }
        // Integration round-trip: token issued by service must pass validation on protected endpoint
        Map<String, Object> u = seedAndLoginUser("tc411jwt");
        long uid = (long)(Number) u.get("id");
        String tok = (String) u.get("token");
        HttpResponse<String> r = httpGetAuth("/api/users/" + uid, tok);
        assert2xx(r, "TC411 protected endpoint validates token with singleton-served secret");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DP-6  FACTORY  (TC412–TC419)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("TC412 — DP-6 Factory: MongoEvent interface")
    void tc412_mongoEventInterface() throws Exception {
        BASE_URL = userServiceUrl;
        String src = readClassSource("MongoEvent");
        assertFalse(src.isEmpty(),
            "TC412: MongoEvent.java not found in src/main/java. " +
            "Required: interface MongoEvent { String getId(); LocalDateTime getTimestamp(); " +
            "String getAction(); Map<String,Object> getDetails(); }");
        assertTrue(src.contains("interface MongoEvent"),
            "TC412: MongoEvent must be an interface. Found head: " +
            src.substring(0, Math.min(200, src.length())));
        for (String m : new String[]{"getId", "getTimestamp", "getAction", "getDetails"}) {
            assertTrue(src.contains(m),
                "TC412: MongoEvent must declare " + m + "() per spec §3.7 / §7.1.1.");
        }
    }

    @Test
    @DisplayName("TC413 — DP-6 Factory: 5 event classes implement MongoEvent")
    void tc413_fiveEventClassesImplementMongoEvent() throws Exception {
        BASE_URL = userServiceUrl;
        String[] events = {
            "AuthEvent", "ProviderEvent", "BookingEvent", "CalendarEvent", "PaymentAuditEvent"
        };
        List<String> missing = new ArrayList<>();
        for (String ev : events) {
            String src = readClassSource(ev);
            if (src.isEmpty()) { missing.add(ev + "(file not found)"); continue; }
            if (!src.contains("implements MongoEvent") &&
                !src.contains("implements MongoEvent,") &&
                !src.contains("implements MongoEvent ")) {
                missing.add(ev);
            }
        }
        assertTrue(missing.isEmpty(),
            "TC413: Event classes not implementing MongoEvent: " + missing);
    }

    @Test
    @DisplayName("TC414 — DP-6 Factory: createEvent(EventType, Map) signature")
    void tc414_createEventSignature() throws Exception {
        BASE_URL = userServiceUrl;
        String src = readClassSource("EventFactory");
        assertFalse(src.isEmpty(),
            "TC414: EventFactory.java not found in src/main/java.");
        assertTrue(src.contains("createEvent"),
            "TC414: EventFactory must declare a createEvent(…) method.");
        assertTrue(src.contains("EventType") && src.contains("Map"),
            "TC414: createEvent must accept (EventType, Map<String,Object>) parameters. " +
            "Found head: " + src.substring(0, Math.min(500, src.length())));
    }

    @Test
    @DisplayName("TC415 — DP-6 Factory: createEvent(AUTH, …) dispatches to AuthEvent")
    void tc415_factoryDispatchesAuthBranch() throws Exception {
        BASE_URL = userServiceUrl;
        // Use readAllSourcesNamed to handle federated per-microservice EventFactory split.
        String src = readAllSourcesNamed("EventFactory");
        assertFalse(src.isEmpty(), "TC415: EventFactory.java not found.");
        assertTrue(src.contains("AUTH") && src.contains("AuthEvent"),
            "TC415: EventFactory must handle EventType.AUTH and create AuthEvent. " +
            "Source must contain both 'AUTH' and 'AuthEvent' in the dispatch block.");
    }

    @Test
    @DisplayName("TC416 — DP-6 Factory: all 5 EventTypes dispatch correctly")
    void tc416_allFiveEventTypesDispatched() throws Exception {
        BASE_URL = userServiceUrl;
        // Booking EventType enum: AUTH, PROVIDER, BOOKING, CALENDAR, PAYMENT_AUDIT.
        // Use union across all federated EventFactory files.
        String src = readAllSourcesNamed("EventFactory");
        assertFalse(src.isEmpty(), "TC416: EventFactory.java not found.");
        List<String> missing = new ArrayList<>();
        for (String t : new String[]{"AUTH", "PROVIDER", "BOOKING", "CALENDAR", "PAYMENT_AUDIT"}) {
            if (!src.contains(t)) missing.add(t);
        }
        assertTrue(missing.isEmpty(),
            "TC416: EventFactory missing dispatch branches for: " + missing +
            ". All 5 EventType values must produce the matching concrete event.");
    }

    @Test
    @DisplayName("TC417 — DP-6 Factory: PaymentAuditEvent exposes method + amount")
    void tc417_paymentAuditEventHasMethodAndAmount() throws Exception {
        BASE_URL = userServiceUrl;
        // Inspect ONE specific class — narrow check, not "is dispatch covered anywhere".
        String src = readClassSource("PaymentAuditEvent");
        assertFalse(src.isEmpty(), "TC417: PaymentAuditEvent.java not found.");
        assertTrue(src.contains("method") || src.contains("paymentMethod"),
            "TC417: PaymentAuditEvent must have a 'method'/'paymentMethod' field (per spec §7.1.5). " +
            "Found head: " + src.substring(0, Math.min(400, src.length())));
        assertTrue(src.contains("amount") || src.contains("refundAmount"),
            "TC417: PaymentAuditEvent must have an 'amount' field. " +
            "Found head: " + src.substring(0, Math.min(400, src.length())));
    }

    @Test
    @DisplayName("TC418 — DP-6 Factory: register integration matches factory output")
    void tc418_registerEventMatchesFactoryShape() throws Exception {
        BASE_URL = userServiceUrl;
        if (mongo == null) throw new AssertionError("TC418: MongoDB required.");
        com.mongodb.client.MongoCollection<org.bson.Document> col =
                mongo.getCollection("auth_events");
        Map<String, Object> u = seedAndLoginUser("tc418fact");
        long uid = (long)(Number) u.get("id");
        org.bson.Document doc = col
                .find(new org.bson.Document("userId", uid))
                .sort(new org.bson.Document("_id", -1)).first();
        if (doc == null)
            doc = col.find().sort(new org.bson.Document("_id", -1)).first();
        assertNotNull(doc,
            "TC418: No auth_events doc found after registration. " +
            "EventFactory must be invoked — auth_events must contain an AuthEvent document.");
        String action = doc.getString("action");
        if (action == null) action = doc.getString("eventType");
        assertNotNull(action,
            "TC418: auth_events doc must have action/eventType (AuthEvent shape); doc=" + doc.toJson());
        assertTrue(doc.containsKey("userId") || doc.containsKey("user_id"),
            "TC418: auth_events doc missing userId field (AuthEvent shape per spec §7.1.1); doc=" + doc.toJson());
    }

    @Test
    @DisplayName("TC419 — DP-6 Factory: no `new XEvent(…)` in service classes")
    void tc419_noDirectEventConstructorsInServices() throws Exception {
        BASE_URL = userServiceUrl;
        String[] eventClasses = {
            "AuthEvent", "ProviderEvent", "BookingEvent", "CalendarEvent", "PaymentAuditEvent"
        };
        for (java.nio.file.Path p : allJavaFiles()) {
            String pathStr = p.toString();
            if (pathStr.contains("/src/test/")) continue;
            // Narrow walker scope to /service/ paths so legitimate adapter constructors
            // (DP-7 source→domain translation) aren't incorrectly flagged.
            if (!pathStr.contains("/service/")) continue;
            String fname = p.getFileName().toString();
            if (fname.equals("EventFactory.java")) continue; // factory itself may use new
            boolean isEventClass = false;
            for (String ev : eventClasses)
                if (fname.equals(ev + ".java")) { isEventClass = true; break; }
            if (isEventClass) continue;
            String content;
            try { content = java.nio.file.Files.readString(p); }
            catch (java.io.IOException e) { continue; }
            for (String ev : eventClasses) {
                assertFalse(content.contains("new " + ev + "("),
                    "TC419: " + fname + " directly constructs " + ev + " with 'new'. " +
                    "All event creation must go through EventFactory.createEvent(…). " +
                    "Direct construction bypasses centralized factory logic (tracing, defaults, etc.).");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DP-7  ADAPTER  (TC420–TC425)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("TC420 — DP-7 Adapter: per-service NoSQL adapter classes present")
    void tc420_adapterClassesPresent() throws Exception {
        BASE_URL = userServiceUrl;
        // Per spec §3.8: MongoDocumentAdapter (all 5 services), ElasticsearchHitAdapter
        // (provider-service), Neo4jRecordAdapter (booking-service), CassandraRowAdapter
        // (calendar-service).
        List<String> missing = new ArrayList<>();
        if (!anySourceContains("class MongoDocumentAdapter"))
            missing.add("MongoDocumentAdapter (all services)");
        if (!anySourceContains("class ElasticsearchHitAdapter"))
            missing.add("ElasticsearchHitAdapter (provider-service)");
        if (!anySourceContains("class Neo4jRecordAdapter"))
            missing.add("Neo4jRecordAdapter (booking-service)");
        if (!anySourceContains("class CassandraRowAdapter"))
            missing.add("CassandraRowAdapter (calendar-service)");
        assertTrue(missing.isEmpty(),
            "TC420: Missing Adapter classes: " + missing +
            ". Per spec §3.8, each service must have Adapter(s) for its NoSQL data sources.");
    }

    @Test
    @DisplayName("TC421 — DP-7 Adapter: each adapter has adapt() returning service DTO")
    void tc421_adaptersHaveAdaptMethod() throws Exception {
        BASE_URL = userServiceUrl;
        String[] adapters = {
            "MongoDocumentAdapter", "ElasticsearchHitAdapter",
            "Neo4jRecordAdapter",   "CassandraRowAdapter"
        };
        List<String> noAdapt = new ArrayList<>();
        for (String name : adapters) {
            String src = readClassSource(name);
            if (src.isEmpty()) continue; // absence already caught by TC420
            if (!src.contains("adapt(")) noAdapt.add(name);
        }
        assertTrue(noAdapt.isEmpty(),
            "TC421: Adapters missing adapt() method: " + noAdapt +
            ". Per spec §3.8, adapt(source) → targetDto is the required method name.");
    }

    @Test
    @DisplayName("TC422 — DP-7 Adapter: MongoDocumentAdapter.adapt(Document) → DTO")
    void tc422_mongoDocumentAdapterSignature() throws Exception {
        BASE_URL = userServiceUrl;
        String src = readClassSource("MongoDocumentAdapter");
        assertFalse(src.isEmpty(), "TC422: MongoDocumentAdapter.java not found.");
        assertTrue(src.contains("adapt(") &&
                   (src.contains("Document") || src.contains("document")),
            "TC422: MongoDocumentAdapter.adapt() must accept a MongoDB Document parameter. " +
            "Found: " + src.substring(0, Math.min(400, src.length())));
    }

    @Test
    @DisplayName("TC423 — DP-7 Adapter: ElasticsearchHitAdapter (provider-service)")
    void tc423_elasticsearchHitAdapterSignature() throws Exception {
        BASE_URL = catalogServiceUrl;  // provider-service
        String src = readClassSource("ElasticsearchHitAdapter");
        assertFalse(src.isEmpty(), "TC423: ElasticsearchHitAdapter.java not found.");
        assertTrue(src.contains("adapt("),
            "TC423: ElasticsearchHitAdapter must have an adapt() method.");
        assertTrue(src.contains("SearchHit") || src.contains("Hit") ||
                   src.contains("Map<") || src.contains("sourceAsMap"),
            "TC423: ElasticsearchHitAdapter.adapt() must accept a SearchHit or equivalent ES source. " +
            "Found: " + src.substring(0, Math.min(400, src.length())));
    }

    @Test
    @DisplayName("TC424 — DP-7 Adapter: ObjectArrayDtoAdapter for S1-F3")
    void tc424_objectArrayAdapterForS1F3() throws Exception {
        BASE_URL = userServiceUrl;
        boolean hasAdapter =
            anySourceContains("ObjectArrayDtoAdapter") ||
            anySourceContains("class ObjectArray")     ||
            anySourceContains("adapt(Object[]")        ||
            anySourceContains("adapt(Object[] ");
        assertTrue(hasAdapter,
            "TC424: No ObjectArrayDtoAdapter (or Object[]-to-DTO adapter) found in user-service. " +
            "Per spec §3.8, S1-F3 uses native SQL returning Object[] — an Adapter is required.");
        // Integration: S1-F3 must still return a correct DTO after the adapter retrofit
        Map<String, Object> u = seedAndLoginUser("tc424s1f3");
        long uid = (long)(Number) u.get("id");
        String tok = (String) u.get("token");
        HttpResponse<String> r = httpGetAuth("/api/users/" + uid + "/booking-summary", tok);
        assert2xx(r, "TC424 S1-F3 booking-summary after Adapter retrofit");
        JsonNode j = parseNode(r.body());
        assertTrue(
            j.has("totalBookings") || j.has("total_bookings") || j.has("bookingCount") ||
            j.has("totalSpent")    || j.has("total_spent")    || j.has("totalAmount"),
            "TC424: booking-summary DTO must have structural fields (totalBookings, totalSpent, …); " +
            "body=" + r.body());
    }

    @Test
    @DisplayName("TC425 — DP-7 Adapter: M1 JPQL/DTO projection features are exempt")
    void tc425_jpqlFeaturesExemptFromAdapter() throws Exception {
        BASE_URL = userServiceUrl;
        // This TC documents the adapter exemption: features implemented via JPQL constructor
        // expressions or @Query DTO projection do NOT require an Adapter class.
        // Verification: S1-F6 (TopClient) — typically JPQL — must not throw 500 errors.
        String adminTok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/top-clients?limit=5", adminTok);
        assertNotEquals(500, r.statusCode(),
            "TC425: JPQL-based M1 feature (S1-F6 top-clients) must not return 500. " +
            "Adapter is required only for Object[]-returning native SQL features; JPQL is exempt.");
    }
}
