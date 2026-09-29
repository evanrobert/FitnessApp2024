package Evan.Application.Fitness.Repositorys;

import Evan.Application.Fitness.Model.DailyCheckIn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyCheckInRepository extends JpaRepository<DailyCheckIn, Long> {
    Optional<DailyCheckIn> findByUserIdAndCheckInDate(Long userId, LocalDate date);

    List<DailyCheckIn> findAllByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(Long userId, LocalDate from, LocalDate to);

    List<DailyCheckIn> findAllByUserIdOrderByCheckInDateDesc(Long userId);

    Optional<DailyCheckIn> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}
