package Evan.Application.Fitness.Repositorys;

import Evan.Application.Fitness.Model.Roles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends JpaRepository<Roles,Long> {
    Roles findByName(String name);

}
