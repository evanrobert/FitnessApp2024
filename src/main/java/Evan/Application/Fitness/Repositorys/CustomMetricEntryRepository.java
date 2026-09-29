package Evan.Application.Fitness.Repositorys;

import Evan.Application.Fitness.Model.CustomMetricEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomMetricEntryRepository extends JpaRepository<CustomMetricEntry, Long> {
    List<CustomMetricEntry> findAllByMetricIdOrderByRecordedOnAscIdAsc(Long metricId);

    Optional<CustomMetricEntry> findFirstByMetricIdOrderByRecordedOnDescIdDesc(Long metricId);

    @Query("SELECT e FROM CustomMetricEntry e JOIN FETCH e.metric m WHERE e.id = :id AND m.user.id = :userId")
    Optional<CustomMetricEntry> findOwned(@Param("id") Long id, @Param("userId") Long userId);

    @Query("SELECT e FROM CustomMetricEntry e JOIN FETCH e.metric m WHERE m.user.id = :userId ORDER BY e.recordedOn DESC, e.id DESC")
    List<CustomMetricEntry> allFor(@Param("userId") Long userId);
}
