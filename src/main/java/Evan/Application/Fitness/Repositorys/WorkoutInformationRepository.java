package Evan.Application.Fitness.Repositorys;

import Evan.Application.Fitness.Model.WorkoutInformation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkoutInformationRepository extends JpaRepository<WorkoutInformation, Long> {
    List<WorkoutInformation> findAllByUserIdOrderByDateDescIdDesc(Long userId);
}
