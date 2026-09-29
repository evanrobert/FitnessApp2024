package Evan.Application.Fitness.Repositorys;

import Evan.Application.Fitness.Model.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Members see the shared library plus their own exercises, never another member's. */
public interface ExerciseRepository extends JpaRepository<Exercise, Long> {
    @Query("SELECT e FROM Exercise e WHERE e.owner IS NULL OR e.owner.id = :userId ORDER BY e.name")
    List<Exercise> findVisibleTo(@Param("userId") Long userId);

    @Query("SELECT e FROM Exercise e WHERE e.id = :id AND (e.owner IS NULL OR e.owner.id = :userId)")
    Optional<Exercise> findVisible(@Param("id") Long id, @Param("userId") Long userId);

    @Query("SELECT COUNT(e) > 0 FROM Exercise e WHERE LOWER(e.name) = LOWER(:name) AND (e.owner IS NULL OR e.owner.id = :userId)")
    boolean nameInUse(@Param("name") String name, @Param("userId") Long userId);

    Optional<Exercise> findByIdAndOwnerId(Long id, Long ownerId);

    /** Library first, then the member's own, matched case-insensitively. */
    @Query("SELECT e FROM Exercise e WHERE LOWER(e.name) = LOWER(:name) AND (e.owner IS NULL OR e.owner.id = :userId) " +
            "ORDER BY CASE WHEN e.owner IS NULL THEN 0 ELSE 1 END, e.id")
    List<Exercise> findVisibleByName(@Param("name") String name, @Param("userId") Long userId);
}
