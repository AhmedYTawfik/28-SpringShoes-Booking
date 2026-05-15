package com.team28.booking.booking.saga;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scenario B: payment failure + compensation saga
 *   booking.completed → PENDING invoice → POST /process?simulateFailure=true → FAILED
 *   → compensation via refund-cancellation → REFUNDED invoice
 */
class SagaScenarioBIT {

    private static final String BOOKING_URL  = System.getenv().getOrDefault("BOOKING_SERVICE_URL",  "http://localhost:8083");
    private static final String INVOICE_URL  = System.getenv().getOrDefault("INVOICE_SERVICE_URL",  "http://localhost:8085");
    private static final String USER_URL     = System.getenv().getOrDefault("USER_SERVICE_URL",     "http://localhost:8081");
    private static final String PROVIDER_URL = System.getenv().getOrDefault("PROVIDER_SERVICE_URL", "http://localhost:8082");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    void sagaScenarioB_paymentFailureThenRefund() throws Exception {
        // Step 1 — create user
        String userBody = "{\"name\":\"Saga B User\",\"email\":\"saga-b@test.com\",\"password\":\"pass\",\"phone\":\"01000000002\"}";
        HttpResponse<String> userResp = post(USER_URL + "/api/users/register", userBody);
        assertEquals(201, userResp.statusCode(), "user creation failed: " + userResp.body());
        long userId = extractId(userResp.body());

        // Step 2 — create provider
        String providerBody = "{\"name\":\"Saga B Provider\",\"specialty\":\"Boots\",\"basePrice\":150,\"userId\":" + userId + "}";
        HttpResponse<String> provResp = post(PROVIDER_URL + "/api/providers", providerBody);
        assertEquals(201, provResp.statusCode(), "provider creation failed: " + provResp.body());
        long providerId = extractId(provResp.body());

        // Step 3 — create + confirm + complete booking
        String bookingBody = String.format(
                "{\"userId\":%d,\"providerId\":%d,\"appointmentDate\":\"2026-06-02\",\"startTime\":\"12:00\",\"endTime\":\"13:00\"}",
                userId, providerId);
        HttpResponse<String> bookingResp = post(BOOKING_URL + "/api/bookings", bookingBody);
        assertEquals(201, bookingResp.statusCode(), "booking creation failed: " + bookingResp.body());
        long bookingId = extractId(bookingResp.body());

        put(BOOKING_URL + "/api/bookings/" + bookingId + "/confirm", "");
        put(BOOKING_URL + "/api/bookings/" + bookingId + "/complete", "");

        // Step 4 — wait for PENDING invoice from saga
        String pendingStatus = pollInvoiceStatus(bookingId, "PENDING", 10);
        assertEquals("PENDING", pendingStatus, "Invoice did not reach PENDING");

        // Step 5 — simulate payment failure
        String processBody = String.format(
                "{\"bookingId\":%d,\"userId\":%d,\"method\":\"VISA\",\"cardLastFour\":\"9999\"}", bookingId, userId);
        HttpResponse<String> failResp = post(INVOICE_URL + "/api/invoices/process?simulateFailure=true", processBody);
        assertEquals(200, failResp.statusCode(), "simulate failure call failed: " + failResp.body());

        // Step 6 — assert invoice is FAILED
        String failedStatus = pollInvoiceStatus(bookingId, "FAILED", 10);
        assertEquals("FAILED", failedStatus, "Invoice did not reach FAILED after simulated failure");

        // Step 7 — fetch invoice id for refund-cancellation
        long invoiceId = fetchInvoiceId(bookingId);
        assertTrue(invoiceId > 0, "Could not find invoice for booking " + bookingId);

        // Step 8 — trigger cancellation refund (compensation)
        String refundBody = "{\"reason\":\"Scenario B compensation test\",\"appointmentDate\":\"2026-06-02\"}";
        HttpResponse<String> refundResp = post(INVOICE_URL + "/api/invoices/" + invoiceId + "/refund-cancellation", refundBody);
        // 200 or 201 both acceptable
        assertTrue(refundResp.statusCode() < 300,
                "refund-cancellation failed: " + refundResp.statusCode() + " " + refundResp.body());

        // Step 9 — assert invoice is REFUNDED
        String refundedStatus = pollInvoiceStatus(bookingId, "REFUNDED", 10);
        assertEquals("REFUNDED", refundedStatus, "Invoice did not reach REFUNDED after compensation");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private HttpResponse<String> post(String url, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String url, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private String pollInvoiceStatus(long bookingId, String expected, int maxAttempts) throws Exception {
        for (int i = 0; i < maxAttempts; i++) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(INVOICE_URL + "/api/invoices/search?bookingId=" + bookingId))
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body().contains("\"status\":\"" + expected + "\"")) {
                return expected;
            }
            Thread.sleep(500);
        }
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(INVOICE_URL + "/api/invoices/search?bookingId=" + bookingId))
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return extractField(resp.body(), "status");
    }

    private long fetchInvoiceId(long bookingId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(INVOICE_URL + "/api/invoices/search?bookingId=" + bookingId))
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 200) {
            try { return extractId(resp.body()); } catch (Exception e) { return -1; }
        }
        return -1;
    }

    private long extractId(String json) {
        String marker = "\"id\":";
        int idx = json.indexOf(marker);
        if (idx < 0) throw new RuntimeException("No id in: " + json);
        int start = idx + marker.length();
        int end = json.indexOf(',', start);
        if (end < 0) end = json.indexOf('}', start);
        return Long.parseLong(json.substring(start, end).trim());
    }

    private String extractField(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int idx = json.indexOf(marker);
        if (idx < 0) return "UNKNOWN";
        int start = idx + marker.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}
