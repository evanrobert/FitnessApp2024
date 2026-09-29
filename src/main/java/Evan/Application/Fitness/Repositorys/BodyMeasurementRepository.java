package Evan.Application.Fitness.Repositorys;

import Evan.Application.Fitness.Model.BodyMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BodyMeasurementRepository extends JpaRepository<BodyMeasurement, Long> {
    List<BodyMeasurement> findAllByUserIdOrderByMeasuredOnDescIdDesc(Long userId);

    List<BodyMeasurement> findAllByUserIdOrderByMeasuredOnAscIdAsc(Long userId);

    List<BodyMeasurement> findAllByUserIdAndMeasuredOnGreaterThanEqualOrderByMeasuredOnAscIdAsc(Long userId, LocalDate from);

    Optional<BodyMeasurement> findByIdAndUserId(Long id, Long userId);

    Optional<BodyMeasurement> findFirstByUserIdAndWeightLbNotNullOrderByMeasuredOnDescIdDesc(Long userId);

    Optional<BodyMeasurement> findFirstByUserIdAndBodyFatPctNotNullOrderByMeasuredOnDescIdDesc(Long userId);

    Optional<BodyMeasurement> findFirstByUserIdAndWaistInNotNullOrderByMeasuredOnDescIdDesc(Long userId);

    long countByUserId(Long userId);
}
