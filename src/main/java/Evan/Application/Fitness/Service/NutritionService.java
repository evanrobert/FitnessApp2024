package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Model.CalorieInformation;
import Evan.Application.Fitness.Model.UserMacroInformation;
import Evan.Application.Fitness.Repositorys.CalorieInformationRepository;
import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import Evan.Application.Fitness.Repositorys.UserMacroInformationRepository;
import Evan.Application.Fitness.Web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NutritionService {
    private final CalorieInformationRepository entries;
    private final UserMacroInformationRepository targets;
    private final UserLoginDetailsRepository users;
    private final TodayService todayService;

    public NutritionService(CalorieInformationRepository entries, UserMacroInformationRepository targets,
                            UserLoginDetailsRepository users, TodayService todayService) {
        this.entries = entries;
        this.targets = targets;
        this.users = users;
        this.todayService = todayService;
    }

    @Transactional(readOnly = true)
    public List<CalorieInformation> entriesFor(Long userId) {
        return entries.findAllByUserIdOrderByDateDescIdDesc(userId);
    }

    @Transactional
    public CalorieInformation logMeal(Long userId, CalorieInformation values) {
        CalorieInformation entry = new CalorieInformation();
        copyEditableFields(values, entry);
        if (entry.getDate() == null) {
            entry.setDate(todayService.today(userId));
        }
        entry.setUser(users.getReferenceById(userId));
        return entries.save(entry);
    }

    /**
     * Bulk edit from the nutrition log. Only rows the member owns are updated;
     * unknown or foreign ids are ignored rather than created or overwritten.
     */
    @Transactional
    public int updateEntries(Long userId, List<CalorieInformation> submitted) {
        if (submitted == null || submitted.isEmpty()) {
            return 0;
        }
        Map<Long, CalorieInformation> owned = entries.findAllByUserIdOrderByDateDescIdDesc(userId).stream()
                .collect(Collectors.toMap(CalorieInformation::getId, Function.identity()));
        int updated = 0;
        for (CalorieInformation row : submitted) {
            CalorieInformation existing = row.getId() == null ? null : owned.get(row.getId());
            if (existing != null) {
                copyEditableFields(row, existing);
                updated++;
            }
        }
        return updated;
    }

    @Transactional
    public void deleteEntry(Long userId, Long entryId) {
        entries.delete(entries.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new NotFoundException("Meal")));
    }

    @Transactional(readOnly = true)
    public Optional<UserMacroInformation> targetsFor(Long userId) {
        return targets.findByUserId(userId);
    }

    /** Create-or-update: a member has exactly one set of daily targets. */
    @Transactional
    public void saveTargets(Long userId, UserMacroInformation values) {
        UserMacroInformation current = targets.findByUserId(userId).orElseGet(() -> {
            UserMacroInformation fresh = new UserMacroInformation();
            fresh.setUser(users.getReferenceById(userId));
            return fresh;
        });
        current.setDailyCalories(values.getDailyCalories());
        current.setDailyProtein(values.getDailyProtein());
        current.setDailyFat(values.getDailyFat());
        current.setDailyCarbohydrates(values.getDailyCarbohydrates());
        targets.save(current);
    }

    @Transactional(readOnly = true)
    public double caloriesToday(Long userId) {
        return entries.sumCaloriesForDay(userId, todayService.today(userId));
    }

    private static void copyEditableFields(CalorieInformation from, CalorieInformation to) {
        to.setItemName(from.getItemName() == null ? null : from.getItemName().trim());
        to.setDate(from.getDate() != null ? from.getDate() : to.getDate());
        to.setMealType(from.getMealType());
        to.setCalories(from.getCalories());
        to.setProteins(from.getProteins());
        to.setFats(from.getFats());
        to.setCarbohydrates(from.getCarbohydrates());
        to.setFiber(from.getFiber());
        to.setSugars(from.getSugars());
        to.setSodium(from.getSodium());
        to.setCholesterol(from.getCholesterol());
    }
}
