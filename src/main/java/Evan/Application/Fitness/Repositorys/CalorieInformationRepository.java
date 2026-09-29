package Evan.Application.Fitness.Repositorys;

import Evan.Application.Fitness.Model.CalorieInformation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Every query is scoped to a member id: entries are never read across accounts. */
public interface CalorieInformationRepository extends JpaRepository<CalorieInformation, Long> {

    List<CalorieInformation> findAllByUserIdOrderByDateDescIdDesc(Long userId);

    Optional<CalorieInformation> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    @Query("SELECT COALESCE(SUM(c.calories), 0) FROM CalorieInformation c " +
            "WHERE c.user.id = :userId AND c.date = :day")
    double sumCaloriesForDay(@Param("userId") Long userId, @Param("day") LocalDate day);
}
