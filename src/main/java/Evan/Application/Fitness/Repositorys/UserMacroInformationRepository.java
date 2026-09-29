package Evan.Application.Fitness.Repositorys;

import Evan.Application.Fitness.Model.UserMacroInformation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserMacroInformationRepository extends JpaRepository<UserMacroInformation, Long> {
    Optional<UserMacroInformation> findByUserId(Long userId);
}
