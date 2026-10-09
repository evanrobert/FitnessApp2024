package Evan.Application.Fitness.Repositorys;

import Evan.Application.Fitness.Model.UsageEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface UsageEventRepository extends JpaRepository<UsageEvent, Long> {
    List<UsageEvent> findAllByOccurredOnBetween(LocalDate from, LocalDate to);

    @Modifying
    @Query("DELETE FROM UsageEvent e WHERE e.occurredOn < :before")
    int deleteOlderThan(@Param("before") LocalDate before);
}
