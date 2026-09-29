package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Model.Limitation;
import Evan.Application.Fitness.Model.LimitationStatus;
import Evan.Application.Fitness.Repositorys.LimitationRepository;
import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import Evan.Application.Fitness.Web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;

@Service
public class LimitationService {
    private final LimitationRepository limitations;
    private final UserLoginDetailsRepository users;
    private final TodayService todayService;

    public LimitationService(LimitationRepository limitations, UserLoginDetailsRepository users, TodayService todayService) {
        this.limitations = limitations;
        this.users = users;
        this.todayService = todayService;
    }

    public List<Limitation> all(Long userId) {
        return limitations.findAllByUserIdOrderByStatusAscStartedOnDesc(userId);
    }

    /** Active or being managed: shown while planning training. */
    public List<Limitation> current(Long userId) {
        return limitations.findAllByUserIdAndStatusIn(userId, EnumSet.of(LimitationStatus.ACTIVE, LimitationStatus.MANAGING));
    }

    public Limitation get(Long userId, Long id) {
        return limitations.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Limitation"));
    }

    @Transactional
    public void save(Long userId, Long id, Limitation form) {
        Limitation target = id == null ? new Limitation() : get(userId, id);
        if (id == null) {
            target.setUser(users.getReferenceById(userId));
        }
        target.setBodyArea(form.getBodyArea().trim());
        target.setTitle(form.getTitle().trim());
        target.setSeverity(form.getSeverity());
        target.setStatus(form.getStatus() == null ? LimitationStatus.ACTIVE : form.getStatus());
        target.setStartedOn(form.getStartedOn() == null ? todayService.today(userId) : form.getStartedOn());
        target.setResolvedOn(target.getStatus() == LimitationStatus.RESOLVED
                ? (form.getResolvedOn() == null ? todayService.today(userId) : form.getResolvedOn()) : null);
        target.setNotes(ProfileService.blankToNull(form.getNotes()));
        limitations.save(target);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        limitations.delete(get(userId, id));
    }
}
