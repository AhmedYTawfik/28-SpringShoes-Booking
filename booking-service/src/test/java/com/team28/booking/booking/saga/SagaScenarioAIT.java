package com.team28.booking.booking.saga;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scenario A: happy-path saga
 *   booking.completed → PENDING invoice → POST /process → COMPLETED invoice + booking PAID
 */
class SagaScenarioAIT {

    private static final String BOOKING_URL  = System.getenv().getOrDefault("BOOKING_SERVICE_URL",  "http://localhost:8083");
    private static final String INVOICE_URL  = System.getenv().getOrDefault("INVOICE_SERVICE_URL",  "http://localhost:8085");
    private static final String USER_URL     = System.getenv().getOrDefault("USER_SERVICE_URL",     "http://localhost:8081");
    private static final String PROVIDER_URL = System.getenv().getOrDefault("PROVIDER_SERVICE_URL", "http://localhost:8082");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    void sagaScenarioA_bookingCompletedToInvoicePaid() throws Exception {
        // Step 1 — create user
        String userBody = "{\"name\":\"Saga A User\",\"email\":\"saga-a@test.com\",\"password\":\"pass\",\"phone\":\"01000000001\"}";
        HttpResponse<String> userResp = post(USER_URL + "/api/users/register", userBody);
        assertEquals(201, userResp.statusCode(), "user creation failed: " + userResp.body());
        long userId = extractId(userResp.body());

        // Step 2 — create provider
        String providerBody = "{\"name\":\"Saga A Provider\",\"specialty\":\"Shoes\",\"basePrice\":100,\"userId\":" + userId + "}";
        HttpResponse<String> provResp = post(PROVIDER_URL + "/api/providers", providerBody);
        assertEquals(201, provResp.statusCode(), "provider creation failed: " + provResp.body());
        long providerId = extractId(provResp.body());

        // Step 3 — create booking (REQUESTED)
        String bookingBody = String.format(
                "{\"userId\":%d,\"providerId\":%d,\"appointmentDate\":\"2026-06-01\",\"startTime\":\"10:00\",\"endTime\":\"11:00\"}",
                userId, providerId);
        HttpResponse<String> bookingResp = post(BOOKING_URL + "/api/bookings", bookingBody);
        assertEquals(201, bookingResp.statusCode(), "booking creation failed: " + bookingResp.body());
        long bookingId = extractId(bookingResp.body());

        // Step 4 — confirm booking (CONFIRMED)
        HttpResponse<String> confirmResp = put(BOOKING_URL + "/api/bookings/" + bookingId + "/confirm", "");
        assertEquals(200, confirmResp.statusCode(), "confirm failed: " + confirmResp.body());

        // Step 5 — complete booking → publishes booking.completed → invoice-service creates PENDING invoice
        HttpResponse<String> completeResp = put(BOOKING_URL + "/api/bookings/" + bookingId + "/complete", "");
        assertEquals(200, completeResp.statusCode(), "complete failed: " + completeResp.body());

        // Step 6 — poll until invoice is PENDING (RabbitMQ async, up to 5s)
        String invoiceStatus = pollInvoiceStatus(bookingId, "PENDING", 10);
        assertEquals("PENDING", invoiceStatus, "Invoice did not reach PENDING within timeout");

        // Step 7 — POST /process to pay (VISA card)
        String processBody = String.format(
                "{\"bookingId\":%d,\"userId\":%d,\"method\":\"VISA\",\"cardLastFour\":\"1234\"}", bookingId, userId);
        HttpResponse<String> processResp = post(INVOICE_URL + "/api/invoices/process", processBody);
        assertEquals(201, processResp.statusCode(), "process invoice failed: " + processResp.body());

        // Step 8 — assert invoice is COMPLETED
        String finalStatus = pollInvoiceStatus(bookingId, "COMPLETED", 10);
        assertEquals("COMPLETED", finalStatus, "Invoice did not reach COMPLETED after payment");
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
        // return whatever status is there for assertion message
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(INVOICE_URL + "/api/invoices/search?bookingId=" + bookingId))
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return extractField(resp.body(), "status");
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
