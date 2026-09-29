package Evan.Application.Fitness.Repositorys;

import Evan.Application.Fitness.Model.Goal;
import Evan.Application.Fitness.Model.GoalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GoalRepository extends JpaRepository<Goal, Long> {
    List<Goal> findAllByUserIdOrderByStatusAscTargetDateAscIdDesc(Long userId);

    List<Goal> findAllByUserIdAndStatus(Long userId, GoalStatus status);

    Optional<Goal> findByIdAndUserId(Long id, Long userId);
}
