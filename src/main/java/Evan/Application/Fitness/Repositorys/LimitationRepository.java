package Evan.Application.Fitness.Repositorys;

import Evan.Application.Fitness.Model.Limitation;
import Evan.Application.Fitness.Model.LimitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LimitationRepository extends JpaRepository<Limitation, Long> {
    List<Limitation> findAllByUserIdOrderByStatusAscStartedOnDesc(Long userId);

    List<Limitation> findAllByUserIdAndStatusIn(Long userId, Collection<LimitationStatus> statuses);

    Optional<Limitation> findByIdAndUserId(Long id, Long userId);
}
