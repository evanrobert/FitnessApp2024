package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Model.DailyCheckIn;
import Evan.Application.Fitness.Repositorys.DailyCheckInRepository;
import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Daily check-ins are upserted by date: one record per member per day. */
@Service
public class CheckInService {
    private final DailyCheckInRepository checkIns;
    private final UserLoginDetailsRepository users;
    private final TodayService todayService;

    public CheckInService(DailyCheckInRepository checkIns, UserLoginDetailsRepository users, TodayService todayService) {
        this.checkIns = checkIns;
        this.users = users;
        this.todayService = todayService;
    }

    public Optional<DailyCheckIn> forDate(Long userId, LocalDate date) {
        return checkIns.findByUserIdAndCheckInDate(userId, date);
    }

    public List<DailyCheckIn> between(Long userId, LocalDate from, LocalDate to) {
        return checkIns.findAllByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(userId, from, to);
    }

    public List<DailyCheckIn> history(Long userId) {
        return checkIns.findAllByUserIdOrderByCheckInDateDesc(userId);
    }

    /** A form pre-filled with the day's existing check-in, or an empty one for that date. */
    public DailyCheckIn formFor(Long userId, LocalDate date) {
        return forDate(userId, date).orElseGet(() -> {
            DailyCheckIn blank = new DailyCheckIn();
            blank.setCheckInDate(date);
            return blank;
        });
    }

    @Transactional
    public DailyCheckIn save(Long userId, DailyCheckIn form) {
        LocalDate date = form.getCheckInDate() == null ? todayService.today(userId) : form.getCheckInDate();
        DailyCheckIn target = existingOrNew(userId, date);
        target.setSleepHours(form.getSleepHours());
        target.setSleepQuality(form.getSleepQuality());
        target.setEnergy(form.getEnergy());
        target.setMood(form.getMood());
        target.setStress(form.getStress());
        target.setSoreness(form.getSoreness());
        target.setWaterOz(form.getWaterOz());
        target.setSteps(form.getSteps());
        target.setRestingHr(form.getRestingHr());
        target.setNotes(ProfileService.blankToNull(form.getNotes()));
        return checkIns.save(target);
    }

    /** One-tap hydration logging: adds to today's total without touching other fields. */
    @Transactional
    public double addWater(Long userId, double ounces) {
        DailyCheckIn target = existingOrNew(userId, todayService.today(userId));
        double total = Math.max(0, Math.min(400, (target.getWaterOz() == null ? 0 : target.getWaterOz()) + ounces));
        target.setWaterOz(total);
        checkIns.save(target);
        return total;
    }

    private DailyCheckIn existingOrNew(Long userId, LocalDate date) {
        return checkIns.findByUserIdAndCheckInDate(userId, date).orElseGet(() -> {
            DailyCheckIn fresh = new DailyCheckIn();
            fresh.setUser(users.getReferenceById(userId));
            fresh.setCheckInDate(date);
            return fresh;
        });
    }
}
