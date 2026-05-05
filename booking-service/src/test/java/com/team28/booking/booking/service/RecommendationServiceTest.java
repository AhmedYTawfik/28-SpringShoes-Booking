package com.team28.booking.booking.service;

import com.team28.booking.booking.cache.CacheInvalidator;
import com.team28.booking.booking.dto.ProviderRecommendationDTO;
import com.team28.booking.booking.neo4j.UserNodeRepository;
import com.team28.booking.booking.observer.MongoEventLogger;
import com.team28.booking.booking.repository.BookingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * S3-F12 unit tests covering all spec scenarios (§10.3.3 test scenario a–f).
 *
 * The ownership check reads the SecurityContext, so tests stub it manually.
 * Neo4j and PG interactions are fully mocked — no running databases required.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private MongoEventLogger mongoEventLogger;
    @Mock private CacheInvalidator cacheInvalidator;
    @Mock private UserNodeRepository userNodeRepository;

    @InjectMocks
    private BookingService bookingService;

    // ── SecurityContext helpers ───────────────────────────────────────────────

    private void setUserContext(long uid) {
        Map<String, Object> principal = Map.of("id", uid, "role", "USER");
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void setAdminContext(long uid) {
        Map<String, Object> principal = Map.of("id", uid, "role", "ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** Helper: build a typed List<Object[]> for mockito stubs without ambiguity. */
    private List<Object[]> providerRows(Object[]... rows) {
        List<Object[]> list = new ArrayList<>();
        for (Object[] row : rows) list.add(row);
        return list;
    }

    // ── Scenario (a): happy-path recommendations ──────────────────────────────

    /**
     * A booked P1 and P2. B booked P1+P3. C booked P2+P4.
     * Recommendations for A → should include P3 (score=1 from B) and P4 (score=1 from C).
     */
    @Test
    void getRecommendations_happyPath_returnsScoredProviders() {
        setUserContext(1L);

        when(bookingRepository.existsUserById(1L)).thenReturn(true);

        List<Map<String, Object>> neo4jResult = List.of(
                Map.of("providerId", 3L, "score", 1L),
                Map.of("providerId", 4L, "score", 1L)
        );
        when(userNodeRepository.findRecommendations(1L, 5)).thenReturn(neo4jResult);
        when(bookingRepository.findProvidersByIds(List.of(3L, 4L)))
                .thenReturn(providerRows(
                        new Object[]{3L, "Provider P3", "Haircut"},
                        new Object[]{4L, "Provider P4", "Massage"}));

        List<ProviderRecommendationDTO> result = bookingService.getRecommendations(1L, 5);

        assertEquals(2, result.size());

        assertEquals(3L, result.get(0).providerId());
        assertEquals("Provider P3", result.get(0).name());
        assertEquals("Haircut", result.get(0).specialty());
        assertEquals(1L, result.get(0).score());

        assertEquals(4L, result.get(1).providerId());
        assertEquals("Provider P4", result.get(1).name());
        assertEquals("Massage", result.get(1).specialty());
        assertEquals(1L, result.get(1).score());

        // P1 and P2 must NOT appear (excluded by Cypher WHERE NOT)
        result.forEach(dto -> assertNotEquals(1L, dto.providerId()));
        result.forEach(dto -> assertNotEquals(2L, dto.providerId()));
    }

    // ── Scenario (b): wrong user token → 403 ─────────────────────────────────

    @Test
    void getRecommendations_wrongUserToken_throws403() {
        setUserContext(2L); // B's token requesting for A (userId=1)

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.getRecommendations(1L, 5));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verifyNoInteractions(userNodeRepository);
        verify(bookingRepository, never()).existsUserById(anyLong());
    }

    // ── Scenario (c): ADMIN token → 200 bypass ───────────────────────────────

    @Test
    void getRecommendations_adminToken_bypassesOwnershipCheck() {
        setAdminContext(99L); // admin requesting for any user

        when(bookingRepository.existsUserById(1L)).thenReturn(true);
        when(userNodeRepository.findRecommendations(1L, 5)).thenReturn(List.of(
                Map.of("providerId", 3L, "score", 1L)
        ));
        when(bookingRepository.findProvidersByIds(List.of(3L)))
                .thenReturn(providerRows(new Object[]{3L, "Provider P3", "Haircut"}));

        List<ProviderRecommendationDTO> result = bookingService.getRecommendations(1L, 5);

        assertEquals(1, result.size());
        assertEquals(3L, result.get(0).providerId());
    }

    // ── Scenario (d): user with no recorded interactions → empty list ─────────

    @Test
    void getRecommendations_noInteractions_returnsEmptyList() {
        setUserContext(1L);

        when(bookingRepository.existsUserById(1L)).thenReturn(true);
        when(userNodeRepository.findRecommendations(1L, 5)).thenReturn(List.of());

        List<ProviderRecommendationDTO> result = bookingService.getRecommendations(1L, 5);

        assertTrue(result.isEmpty());
        verify(bookingRepository, never()).findProvidersByIds(anyList());
    }

    // ── Scenario (e): userId=999 with ADMIN token → 404 ─────────────────────

    @Test
    void getRecommendations_adminUnknownUser_throws404() {
        setAdminContext(99L);

        when(bookingRepository.existsUserById(999L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.getRecommendations(999L, 5));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(userNodeRepository);
    }

    // ── Scenario (f): no JWT token → ownership check skips ADMIN, sees no auth ─

    @Test
    void getRecommendations_userExistsInJwtButNotPg_throws404() {
        setUserContext(1L);

        when(bookingRepository.existsUserById(1L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.getRecommendations(1L, 5));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(userNodeRepository);
    }

    // ── limit is passed through to Neo4j ─────────────────────────────────────

    @Test
    void getRecommendations_respectsLimit() {
        setAdminContext(99L);

        when(bookingRepository.existsUserById(1L)).thenReturn(true);
        when(userNodeRepository.findRecommendations(1L, 2)).thenReturn(List.of(
                Map.of("providerId", 3L, "score", 2L),
                Map.of("providerId", 4L, "score", 1L)
        ));
        when(bookingRepository.findProvidersByIds(any()))
                .thenReturn(providerRows(
                        new Object[]{3L, "P3", "Specialty"},
                        new Object[]{4L, "P4", "Specialty2"}));

        List<ProviderRecommendationDTO> result = bookingService.getRecommendations(1L, 2);

        assertEquals(2, result.size());
        verify(userNodeRepository).findRecommendations(1L, 2);
    }

    // ── Neo4j ranking order is preserved in the response ─────────────────────

    @Test
    void getRecommendations_preservesNeo4jRankingOrder() {
        setAdminContext(99L);

        when(bookingRepository.existsUserById(1L)).thenReturn(true);
        when(userNodeRepository.findRecommendations(1L, 5)).thenReturn(List.of(
                Map.of("providerId", 3L, "score", 3L),
                Map.of("providerId", 4L, "score", 1L)
        ));
        when(bookingRepository.findProvidersByIds(any()))
                .thenReturn(providerRows(
                        new Object[]{3L, "P3", "Specialty"},
                        new Object[]{4L, "P4", "Specialty2"}));

        List<ProviderRecommendationDTO> result = bookingService.getRecommendations(1L, 5);

        assertEquals(3L, result.get(0).score());
        assertEquals(1L, result.get(1).score());
    }
}
