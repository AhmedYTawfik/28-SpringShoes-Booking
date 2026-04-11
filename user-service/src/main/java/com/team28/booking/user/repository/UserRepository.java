package com.team28.booking.user.repository;

import com.team28.booking.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Query(value = "SELECT * FROM users u WHERE " +
            "(:name IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
            "(:email IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%'))) AND " +
            "(:role IS NULL OR u.role = :role)",
            nativeQuery = true)
    List<User> searchUsers(
            @Param("name") String name,
            @Param("email") String email,
            @Param("role") String role
    );

    // S1-F4: Check if user has active bookings (cross-service native SQL)
    // Active bookings = status IN ('REQUESTED', 'CONFIRMED', 'IN_PROGRESS')
    @Query(value = "SELECT COUNT(*) FROM bookings b WHERE b.user_id = :userId AND " +
            "b.status IN ('REQUESTED', 'CONFIRMED', 'IN_PROGRESS')",
            nativeQuery = true)
    Long countActiveBookings(@Param("userId") Long userId);

    // S1-F6: Top Clients by Spending (native SQL with JOIN)
    // Returns: user_id, name, total_spent, booking_count
    @Query(value = "SELECT u.id as user_id, u.name, " +
            "COALESCE(SUM(COALESCE(b.total_price, 0)), 0) as total_spent, " +
            "COUNT(b.id) as booking_count " +
            "FROM users u " +
            "JOIN bookings b ON u.id = b.user_id " +
            "AND b.status = 'COMPLETED' " +
            "AND COALESCE(CAST(b.completed_at AS DATE), b.appointment_date, CAST(b.requested_at AS DATE)) >= CAST(:startDate AS DATE) " +
            "AND COALESCE(CAST(b.completed_at AS DATE), b.appointment_date, CAST(b.requested_at AS DATE)) <= CAST(:endDate AS DATE) " +
            "GROUP BY u.id, u.name " +
            "ORDER BY total_spent DESC, booking_count DESC, u.id ASC " +
            "LIMIT :limit",
            nativeQuery = true)
    List<Object[]> findTopClientsBySpending(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("limit") int limit
    );

    // S1-F3: User booking summary aggregated from the shared bookings table.
    @Query(value = "SELECT u.id AS user_id, u.name, " +
            "COUNT(b.id) AS total_bookings, " +
            "SUM(CASE WHEN b.status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_bookings, " +
            "SUM(CASE WHEN b.status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_bookings, " +
            "COALESCE(SUM(CASE WHEN b.status = 'COMPLETED' THEN b.total_price ELSE 0 END), 0) AS total_spent, " +
            "COALESCE(ROUND(AVG(CASE WHEN b.status = 'COMPLETED' THEN b.total_price END), 2), 0) AS average_booking_price " +
            "FROM users u " +
            "LEFT JOIN bookings b ON u.id = b.user_id " +
            "WHERE u.id = :userId " +
            "GROUP BY u.id, u.name",
            nativeQuery = true)
    List<Object[]> findUserBookingSummary(@Param("userId") Long userId);

    @Query(value = "SELECT * FROM users u WHERE u.preferences @> CAST(:filter AS jsonb)",
            nativeQuery = true)
    List<User> findByPreference(@Param("filter") String jsonFilter);

    // S1-F8: Load user together with saved addresses for profile DTO construction.
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.savedAddresses WHERE u.id = :userId")
    Optional<User> findByIdWithSavedAddresses(@Param("userId") Long userId);

    // S1-F9: Filter users by language preference and minimum completed bookings.
    @Query(value = "SELECT u.* " +
            "FROM users u " +
            "LEFT JOIN bookings b ON u.id = b.user_id AND b.status = 'COMPLETED' " +
            "WHERE LOWER(CAST(u.preferences ->> 'language' AS TEXT)) = LOWER(:language) " +
            "GROUP BY u.id " +
            "HAVING COUNT(b.id) >= :minBookings " +
            "ORDER BY u.id",
            nativeQuery = true)
    List<User> findUsersByLanguagePreferenceAndMinimumCompletedBookings(
            @Param("language") String language,
            @Param("minBookings") long minBookings
    );

}
