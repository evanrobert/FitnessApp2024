package Evan.Application.Fitness.Repositorys;

import Evan.Application.Fitness.Model.CustomMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomMetricRepository extends JpaRepository<CustomMetric, Long> {
    List<CustomMetric> findAllByUserIdOrderByArchivedAscNameAsc(Long userId);

    Optional<CustomMetric> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);
}
