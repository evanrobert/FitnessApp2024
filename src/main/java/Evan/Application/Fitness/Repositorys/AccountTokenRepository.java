package Evan.Application.Fitness.Repositorys;

import Evan.Application.Fitness.Model.AccountToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {
    Optional<AccountToken> findByTokenHash(String tokenHash);

    /** Invalidates every outstanding link of one kind, e.g. after a successful reset. */
    @Modifying
    @Query("UPDATE AccountToken t SET t.usedAt = :now WHERE t.user.id = :userId AND t.purpose = :purpose AND t.usedAt IS NULL")
    int invalidate(@Param("userId") Long userId, @Param("purpose") AccountToken.Purpose purpose, @Param("now") LocalDateTime now);

    @Query("SELECT COUNT(t) FROM AccountToken t WHERE t.user.id = :userId AND t.purpose = :purpose AND t.createdAt > :since")
    long countIssuedSince(@Param("userId") Long userId, @Param("purpose") AccountToken.Purpose purpose, @Param("since") LocalDateTime since);

    @Modifying
    @Query("DELETE FROM AccountToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
