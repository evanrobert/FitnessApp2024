package Evan.Application.Fitness.Repositorys;

import Evan.Application.Fitness.Model.ExerciseSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExerciseSetRepository extends JpaRepository<ExerciseSet, Long> {
    /** Complete lifting history in chronological order (drives PR detection and exercise trends). */
    @Query("SELECT st FROM ExerciseSet st JOIN FETCH st.session s JOIN FETCH st.exercise e " +
            "WHERE s.user.id = :userId ORDER BY s.sessionDate, s.id, st.sortOrder, st.setNumber")
    List<ExerciseSet> historyFor(@Param("userId") Long userId);

    @Query("SELECT st FROM ExerciseSet st JOIN FETCH st.session s " +
            "WHERE s.user.id = :userId AND st.exercise.id = :exerciseId ORDER BY s.sessionDate, s.id, st.setNumber")
    List<ExerciseSet> historyFor(@Param("userId") Long userId, @Param("exerciseId") Long exerciseId);

    @Query("SELECT COUNT(st) > 0 FROM ExerciseSet st WHERE st.exercise.id = :exerciseId")
    boolean exerciseInUse(@Param("exerciseId") Long exerciseId);
}
