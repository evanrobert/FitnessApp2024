package Evan.Application.Fitness.Repositorys;

import Evan.Application.Fitness.Model.CalorieInformation;
import Evan.Application.Fitness.Model.UserLoginDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CalorieInformationRepository extends JpaRepository<CalorieInformation, Long> {
    List<CalorieInformation> findByUserLoginDetails(UserLoginDetails userLoginDetails);
   // List<CalorieInformation> findAllByMealType(String mealtype);
}
