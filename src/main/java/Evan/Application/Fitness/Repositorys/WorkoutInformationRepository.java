package Evan.Application.Fitness.Repositorys;

import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Model.WorkoutInformation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkoutInformationRepository extends JpaRepository<WorkoutInformation, Long> {
    List<WorkoutInformation> findAllByUserLoginDetails(UserLoginDetails userLoginDetails);
}
