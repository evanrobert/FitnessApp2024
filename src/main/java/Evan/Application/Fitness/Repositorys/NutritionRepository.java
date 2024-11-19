package Evan.Application.Fitness.Repositorys;

import Evan.Application.Fitness.Model.Nutrition;
import Evan.Application.Fitness.Model.UserLoginDetails;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NutritionRepository extends JpaRepository<Nutrition, Long> {

}
