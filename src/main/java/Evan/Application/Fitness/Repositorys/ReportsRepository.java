package Evan.Application.Fitness.Repositorys;

import Evan.Application.Fitness.Model.Reports;
import Evan.Application.Fitness.Model.UserLoginDetails;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportsRepository extends JpaRepository<Reports,Long> {
    Reports findByUserLoginDetails(UserLoginDetails userLoginDetails);
}
