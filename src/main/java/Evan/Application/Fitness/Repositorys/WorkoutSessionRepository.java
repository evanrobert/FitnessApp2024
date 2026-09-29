package Evan.Application.Fitness.Repositorys;

import Evan.Application.Fitness.Model.WorkoutSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, Long> {
    List<WorkoutSession> findAllByUserIdOrderBySessionDateDescIdDesc(Long userId);

    List<WorkoutSession> findAllByUserIdAndSessionDateBetweenOrderBySessionDateAscIdAsc(Long userId, LocalDate from, LocalDate to);

    Optional<WorkoutSession> findByIdAndUserId(Long id, Long userId);

    Optional<WorkoutSession> findFirstByUserIdOrderBySessionDateDescIdDesc(Long userId);

    long countByUserId(Long userId);

    @Query("SELECT s.sessionDate FROM WorkoutSession s WHERE s.user.id = :userId ORDER BY s.sessionDate")
    List<LocalDate> sessionDates(@Param("userId") Long userId);
}
