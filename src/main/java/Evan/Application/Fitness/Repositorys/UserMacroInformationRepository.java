package Evan.Application.Fitness.Repositorys;

import Evan.Application.Fitness.Model.UserMacroInformation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserMacroInformationRepository extends JpaRepository<UserMacroInformation, Long> {
}
