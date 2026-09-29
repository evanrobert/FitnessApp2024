package Evan.Application.Fitness.Repositorys;

import Evan.Application.Fitness.Model.UserInformation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserInformationRepository extends JpaRepository<UserInformation, Long> {
    Optional<UserInformation> findByUserId(Long userId);
}
