package Evan.Application.Fitness.Repositorys;

import Evan.Application.Fitness.Model.CalorieInformation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CalorieInformationRepository extends JpaRepository<CalorieInformation, Long> {
}
