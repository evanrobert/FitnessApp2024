package Evan.Application.Fitness.Repositorys;

import Evan.Application.Fitness.Model.FavoriteFood;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteFoodRepository extends JpaRepository<FavoriteFood, Long> {
    List<FavoriteFood> findAllByUserId(Long userId);

    Optional<FavoriteFood> findByUserIdAndNameKey(Long userId, String nameKey);
}
