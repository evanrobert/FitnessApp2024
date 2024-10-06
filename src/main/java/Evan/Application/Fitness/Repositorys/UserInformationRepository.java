package Evan.Application.Fitness.Repositorys;

import Evan.Application.Fitness.Model.UserInformation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserInformationRepository extends JpaRepository<UserInformation,Long> {
}
