package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Model.BodyMeasurement;
import Evan.Application.Fitness.Repositorys.BodyMeasurementRepository;
import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import Evan.Application.Fitness.Web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class BodyService {
    private final BodyMeasurementRepository measurements;
    private final UserLoginDetailsRepository users;

    public BodyService(BodyMeasurementRepository measurements, UserLoginDetailsRepository users) {
        this.measurements = measurements;
        this.users = users;
    }

    public List<BodyMeasurement> history(Long userId) {
        return measurements.findAllByUserIdOrderByMeasuredOnDescIdDesc(userId);
    }

    public List<BodyMeasurement> chronological(Long userId) {
        return measurements.findAllByUserIdOrderByMeasuredOnAscIdAsc(userId);
    }

    public BodyMeasurement get(Long userId, Long id) {
        return measurements.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Measurement"));
    }

    public Optional<BodyMeasurement> latestWeight(Long userId) {
        return measurements.findFirstByUserIdAndWeightLbNotNullOrderByMeasuredOnDescIdDesc(userId);
    }

    /** True when the form carries at least one measurement worth saving. */
    public static boolean hasAnyValue(BodyMeasurement m) {
        return m.getWeightLb() != null || m.getBodyFatPct() != null || m.hasTapeMeasurements();
    }

    @Transactional
    public BodyMeasurement save(Long userId, Long id, BodyMeasurement form) {
        BodyMeasurement target = id == null ? new BodyMeasurement() : get(userId, id);
        if (id == null) {
            target.setUser(users.getReferenceById(userId));
        }
        target.setMeasuredOn(form.getMeasuredOn());
        target.setWeightLb(form.getWeightLb());
        target.setBodyFatPct(form.getBodyFatPct());
        target.setWaistIn(form.getWaistIn());
        target.setHipsIn(form.getHipsIn());
        target.setChestIn(form.getChestIn());
        target.setArmIn(form.getArmIn());
        target.setThighIn(form.getThighIn());
        target.setNeckIn(form.getNeckIn());
        target.setNotes(ProfileService.blankToNull(form.getNotes()));
        return measurements.save(target);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        measurements.delete(get(userId, id));
    }
}
