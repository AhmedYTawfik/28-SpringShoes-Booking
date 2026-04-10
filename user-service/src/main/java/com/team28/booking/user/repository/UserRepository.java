package com.team28.booking.user.repository;

import com.team28.booking.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

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

}