package Evan.Application.Fitness.Repositorys;

import Evan.Application.Fitness.Model.Nutrition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NutritionRepository extends JpaRepository<Nutrition, Long> {
}
