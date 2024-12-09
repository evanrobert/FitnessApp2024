package Evan.Application.Fitness.Repositorys;

import Evan.Application.Fitness.Model.CalorieInformation;
import Evan.Application.Fitness.Model.UserLoginDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface CalorieInformationRepository extends JpaRepository<CalorieInformation, Long> {
    List<CalorieInformation> findAllByUserLoginDetails(UserLoginDetails userLoginDetails);
    List<CalorieInformation> findByMealType(String mealType);
   //CalorieInformation findByUserLoginDetails(UserLoginDetails userLoginDetails);
    List<CalorieInformation> findByUserLoginDetails(UserLoginDetails userLoginDetails);


    @Query("SELECT c FROM CalorieInformation c WHERE c.date = CURRENT_DATE")
    List<CalorieInformation> findAllByToday();

}

