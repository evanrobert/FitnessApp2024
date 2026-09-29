package Evan.Application.Fitness.Repositorys;

import Evan.Application.Fitness.Model.UserLoginDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserLoginDetailsRepository extends JpaRepository<UserLoginDetails, Long> {
    Optional<UserLoginDetails> findByUsername(String username);

    boolean existsByUsernameIgnoreCase(String username);
}
