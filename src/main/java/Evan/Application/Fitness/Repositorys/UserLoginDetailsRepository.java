package Evan.Application.Fitness.Repositorys;

import Evan.Application.Fitness.Model.UserLoginDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserLoginDetailsRepository extends JpaRepository<UserLoginDetails, Long> {
    Optional<UserLoginDetails> findByUsername(String username);

    Optional<UserLoginDetails> findByEmail(String email);

    Optional<UserLoginDetails> findByUnsubscribeToken(String unsubscribeToken);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByEmail(String email);

    @Query("SELECT u.id FROM UserLoginDetails u WHERE u.weeklyEmail = TRUE AND u.email IS NOT NULL AND u.emailVerifiedAt IS NOT NULL")
    List<Long> weeklyEmailRecipients();

    /**
     * Claims this week's summary for one member. Returns 0 when it was already
     * claimed (by an earlier run or another instance), so each week sends at most once.
     */
    @Modifying
    @Query("UPDATE UserLoginDetails u SET u.weeklyEmailLastSent = :day WHERE u.id = :id "
            + "AND (u.weeklyEmailLastSent IS NULL OR u.weeklyEmailLastSent < :day)")
    int claimWeeklyEmail(@Param("id") Long id, @Param("day") LocalDate day);

    @Modifying
    @Query("UPDATE UserLoginDetails u SET u.failedLoginCount = u.failedLoginCount + 1 WHERE u.username = :username")
    int incrementFailedLogins(@Param("username") String username);

    @Modifying
    @Query("UPDATE UserLoginDetails u SET u.lockedUntil = :until, u.failedLoginCount = 0 "
            + "WHERE u.username = :username AND u.failedLoginCount >= :threshold")
    int lockIfOverThreshold(@Param("username") String username, @Param("threshold") int threshold,
                            @Param("until") LocalDateTime until);

    @Modifying
    @Query("UPDATE UserLoginDetails u SET u.failedLoginCount = 0, u.lockedUntil = NULL WHERE u.username = :username")
    int clearFailedLogins(@Param("username") String username);
}
