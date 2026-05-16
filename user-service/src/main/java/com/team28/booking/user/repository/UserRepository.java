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

    // S1-F6: Top Clients by Spending (native SQL with JOIN)
    // Returns: user_id, name, total_spent, booking_count
    @Query(value = "SELECT u.id as user_id, u.name, " +
            "COALESCE(SUM(b.total_price), 0) as total_spent, " +
            "COUNT(b.id) as booking_count " +
            "FROM users u " +
            "LEFT JOIN bookings b ON u.id = b.user_id " +
            "WHERE b.status = 'COMPLETED' " +
            "AND b.completed_at >= CAST(:startDate AS TIMESTAMP) " +
            "AND b.completed_at <= CAST(:endDate AS TIMESTAMP) " +
            "GROUP BY u.id, u.name " +
            "ORDER BY total_spent DESC " +
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
    Object[] findUserBookingSummary(@Param("userId") Long userId);

    @Query(value = "SELECT * FROM users u WHERE u.preferences @> CAST(:filter AS jsonb)",
            nativeQuery = true)
    List<User> findByPreference(@Param("filter") String jsonFilter);

    // S1-F8: Load user together with saved addresses for profile DTO construction.
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.savedAddresses WHERE u.id = :userId")
    Optional<User> findByIdWithSavedAddresses(@Param("userId") Long userId);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    Optional<User> findByEmail(String email);

    // S1-F9: Filter users by language preference (M3: completed bookings count via Feign)
    @Query(value = "SELECT * FROM users WHERE LOWER(CAST(preferences ->> 'language' AS TEXT)) = LOWER(:language) ORDER BY id", nativeQuery = true)
    List<User> findUsersByLanguagePreference(@Param("language") String language);

}
