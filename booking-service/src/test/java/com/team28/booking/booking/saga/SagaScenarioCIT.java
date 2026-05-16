package com.team28.booking.booking.saga;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Saga E2E Integration Test — Scenario C: pre-check failure (no calendar slot).
 *
 * Setup:  User (ACTIVE), Provider (BUSY), Booking (IN_PROGRESS)
 *         No covering time slot exists in calendar-postgres.
 * Action: PUT /api/bookings/{id}/complete
 * Assert: 400; Booking stays IN_PROGRESS; Provider stays BUSY;
 *         no booking.completed event published (evidenced by no PAYMENT_PENDING transition).
 *
 * URLs are read from system properties / env vars with sensible local defaults.
 */
@Tag("saga-e2e")
class SagaScenarioCIT {

    private static final ObjectMapper OM = new ObjectMapper().findAndRegisterModules();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static final String USER_URL     = prop("USER_SERVICE_URL",     "http://localhost:8081");
    private static final String PROVIDER_URL = prop("PROVIDER_SERVICE_URL", "http://localhost:8082");
    private static final String BOOKING_URL  = prop("BOOKING_SERVICE_URL",  "http://localhost:8083");

    private static String prop(String key, String def) {
        String v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v : System.getProperty(key, def);
    }

    @Test
    @DisplayName("Scenario C — PUT /complete with no calendar slot returns 400; booking stays IN_PROGRESS; provider stays BUSY")
    void scenarioC_noCalendarSlot_returns400_and_no_transition() throws Exception {
        // ── Step 1: register ACTIVE user ────────────────────────────────────
        String nonce = String.valueOf(System.nanoTime()).substring(5);
        String email = "saga_c_" + nonce + "@test.io";
        String regBody = String.format(
                "{\"name\":\"SagaC User\",\"email\":\"%s\",\"password\":\"Test!2026\",\"phone\":\"+2011%s\"}",
                email, nonce.substring(0, 8));

        HttpResponse<String> regResp = post(USER_URL, "/api/auth/register", regBody, null);
        assertTrue(regResp.statusCode() >= 200 && regResp.statusCode() < 300,
                "Scenario C: register user — expected 2xx, got " + regResp.statusCode());
        String userToken = OM.readTree(regResp.body()).path("token").asText();
        assertFalse(userToken.isBlank(), "Scenario C: user token must not be blank");

        // ── Step 2: register ADMIN and create a Provider ─────────────────────
        String adminNonce = String.valueOf(System.nanoTime()).substring(5);
        String adminEmail = "saga_c_adm_" + adminNonce + "@test.io";
        String adminRegBody = String.format(
                "{\"name\":\"SagaC Admin\",\"email\":\"%s\",\"password\":\"Admin!2026\",\"phone\":\"+2012%s\"}",
                adminEmail, adminNonce.substring(0, 8));
        HttpResponse<String> adminReg = post(USER_URL, "/api/auth/register", adminRegBody, null);
        assertTrue(adminReg.statusCode() >= 200 && adminReg.statusCode() < 300,
                "Scenario C: register admin — expected 2xx, got " + adminReg.statusCode());
        String adminToken = OM.readTree(adminReg.body()).path("token").asText();

        String provBody = String.format(
                "{\"name\":\"SagaC Provider\",\"specialty\":\"Dentist\"," +
                "\"email\":\"sagac_prov_%s@test.io\",\"phone\":\"+2013%s\"}",
                adminNonce, adminNonce.substring(0, 8));
        HttpResponse<String> provResp = post(PROVIDER_URL, "/api/providers", provBody, adminToken);
        assertTrue(provResp.statusCode() >= 200 && provResp.statusCode() < 300,
                "Scenario C: create provider — expected 2xx, got " + provResp.statusCode());
        long providerId = OM.readTree(provResp.body()).path("id").asLong();
        assertTrue(providerId > 0, "Scenario C: providerId must be > 0");

        // ── Step 3: create Booking (IN_PROGRESS) — no time slot created ──────
        String apptDate = LocalDate.now().plusDays(5).toString();
        String bookBody = String.format(
                "{\"providerId\":%d,\"appointmentDate\":\"%s\",\"startTime\":\"10:00\",\"endTime\":\"11:00\"}",
                providerId, apptDate);
        HttpResponse<String> bookResp = post(BOOKING_URL, "/api/bookings", bookBody, userToken);
        assertTrue(bookResp.statusCode() >= 200 && bookResp.statusCode() < 300,
                "Scenario C: create booking — expected 2xx, got " + bookResp.statusCode());
        long bookingId = OM.readTree(bookResp.body()).path("id").asLong();
        assertTrue(bookingId > 0, "Scenario C: bookingId must be > 0");

        // ── Step 4: attempt complete (no calendar slot exists) ────────────────
        HttpResponse<String> completeResp = put(BOOKING_URL,
                "/api/bookings/" + bookingId + "/complete", "{}", userToken);

        assertEquals(400, completeResp.statusCode(),
                "Scenario C: PUT /complete with no calendar slot must return 400; body=" + completeResp.body());

        // ── Step 5: verify Booking stays IN_PROGRESS ─────────────────────────
        HttpResponse<String> bookGet = get(BOOKING_URL, "/api/bookings/" + bookingId, userToken);
        assertTrue(bookGet.statusCode() >= 200 && bookGet.statusCode() < 300,
                "Scenario C: GET booking expected 2xx, got " + bookGet.statusCode());
        JsonNode bookNode = OM.readTree(bookGet.body());
        String bookStatus = bookNode.path("status").asText();
        assertEquals("IN_PROGRESS", bookStatus,
                "Scenario C: booking must remain IN_PROGRESS; got=" + bookStatus);

        // ── Step 6: verify Provider stays BUSY ───────────────────────────────
        HttpResponse<String> provGet = get(PROVIDER_URL, "/api/providers/" + providerId, adminToken);
        assertTrue(provGet.statusCode() >= 200 && provGet.statusCode() < 300,
                "Scenario C: GET provider expected 2xx, got " + provGet.statusCode());
        JsonNode provNode = OM.readTree(provGet.body());
        String provStatus = provNode.path("status").asText();
        assertEquals("BUSY", provStatus,
                "Scenario C: provider must remain BUSY; got=" + provStatus);

        // ── Step 7: assert no booking.completed was published ─────────────────
        // If booking.completed were emitted, the saga would have created a PENDING invoice
        // and transitioned booking to PAYMENT_PENDING. IN_PROGRESS proves the event was suppressed.
        assertNotEquals("PAYMENT_PENDING", bookStatus,
                "Scenario C: booking.completed must NOT have been published");
        assertNotEquals("COMPLETING", bookStatus,
                "Scenario C: saga did not start — booking must not be COMPLETING");
    }

    // ── HTTP helpers ─────────────────────────────────────────────────────────

    private HttpResponse<String> get(String base, String path, String token) throws Exception {
        var b = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json");
        if (token != null) b.header("Authorization", "Bearer " + token);
        return HTTP.send(b.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String base, String path, String body, String token) throws Exception {
        var b = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json");
        if (token != null) b.header("Authorization", "Bearer " + token);
        b.POST(HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String base, String path, String body, String token) throws Exception {
        var b = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json");
        if (token != null) b.header("Authorization", "Bearer " + token);
        b.PUT(HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
}
