package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Model.CalorieInformation;
import Evan.Application.Fitness.Model.UserMacroInformation;
import Evan.Application.Fitness.Model.FavoriteFood;
import Evan.Application.Fitness.Repositorys.CalorieInformationRepository;
import Evan.Application.Fitness.Repositorys.FavoriteFoodRepository;
import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import Evan.Application.Fitness.Repositorys.UserMacroInformationRepository;
import Evan.Application.Fitness.Web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NutritionService {
    private final CalorieInformationRepository entries;
    private final UserMacroInformationRepository targets;
    private final UserLoginDetailsRepository users;
    private final TodayService todayService;
    private final FavoriteFoodRepository favorites;

    public NutritionService(CalorieInformationRepository entries, UserMacroInformationRepository targets,
                            UserLoginDetailsRepository users, TodayService todayService, FavoriteFoodRepository favorites) {
        this.favorites = favorites;
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
        if (entry.getCalories() == 0 && entry.macroCalories() > 0) {
            entry.setCalories((double) Math.round(entry.macroCalories())); // macros given, calories left blank
        }
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

    public CalorieInformation entry(Long userId, Long entryId) {
        return entries.findByIdAndUserId(entryId, userId).orElseThrow(() -> new NotFoundException("Meal"));
    }

    @Transactional
    public void updateEntry(Long userId, Long entryId, CalorieInformation values) {
        copyEditableFields(values, entry(userId, entryId));
    }

    /** Copy a past entry onto today (the most common way people log repeat meals). */
    @Transactional
    public CalorieInformation relog(Long userId, Long entryId, LocalDate date) {
        return relog(userId, entryId, date, null);
    }

    /** Logs a past entry again; {@code mealType} (e.g. lunch, by time of day) replaces the original slot when given. */
    @Transactional
    public CalorieInformation relog(Long userId, Long entryId, LocalDate date, String mealType) {
        CalorieInformation copy = new CalorieInformation();
        copyEditableFields(entry(userId, entryId), copy);
        if (mealType != null) {
            copy.setMealType(mealType);
        }
        copy.setDate(date == null ? todayService.today(userId) : date);
        copy.setUser(users.getReferenceById(userId));
        return entries.save(copy);
    }

    public List<CalorieInformation> entriesOn(Long userId, LocalDate day) {
        return entries.findAllByUserIdAndDateOrderByIdAsc(userId, day);
    }

    public List<CalorieInformation> between(Long userId, LocalDate from, LocalDate to) {
        return entries.findAllByUserIdAndDateBetweenOrderByDateAscIdAsc(userId, from, to);
    }

    /** Most recently logged distinct foods, newest first: one-click re-log candidates. */
    public List<CalorieInformation> recentFoods(Long userId, int limit) {
        Map<String, CalorieInformation> distinct = new LinkedHashMap<>();
        for (CalorieInformation c : entries.findAllByUserIdOrderByDateDescIdDesc(userId)) {
            if (c.getItemName() != null) {
                distinct.putIfAbsent(c.getItemName().trim().toLowerCase(Locale.ROOT), c);
            }
            if (distinct.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(distinct.values());
    }

    // ---- One-tap logging -------------------------------------------------------------

    /** A food the member can log again with one tap. {@code entryId} is their latest entry of it. */
    public record QuickFood(Long entryId, String name, double calories, double protein, String icon, boolean favorite) {
    }

    static String key(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * Starred foods first, then what the member eats most often (last 60 days), most recent breaking ties.
     * Each food appears once, with the calories and macros of its latest entry.
     */
    public List<QuickFood> quickFoods(Long userId, int limit) {
        LocalDate since = todayService.today(userId).minusDays(60);
        Set<String> starred = favoriteKeys(userId);
        Map<String, CalorieInformation> latest = new LinkedHashMap<>();
        Map<String, Integer> times = new HashMap<>();
        for (CalorieInformation c : entries.findAllByUserIdOrderByDateDescIdDesc(userId)) {
            String k = key(c.getItemName());
            if (k.isEmpty()) {
                continue;
            }
            latest.putIfAbsent(k, c);
            if (c.getDate() != null && !c.getDate().isBefore(since)) {
                times.merge(k, 1, Integer::sum);
            }
        }
        List<String> order = new ArrayList<>(latest.keySet()); // newest first
        Map<String, Integer> recency = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            recency.put(order.get(i), i);
        }
        order.sort(Comparator.<String>comparingInt(k -> starred.contains(k) ? 0 : 1)
                .thenComparing(k -> -times.getOrDefault(k, 0))
                .thenComparing(recency::get));
        return order.stream().limit(limit).map(k -> {
            CalorieInformation c = latest.get(k);
            return new QuickFood(c.getId(), c.getItemName().trim(), c.getCalories(), c.getProteins(), c.foodIcon(), starred.contains(k));
        }).toList();
    }

    public Set<String> favoriteKeys(Long userId) {
        Set<String> keys = new HashSet<>();
        favorites.findAllByUserId(userId).forEach(f -> keys.add(f.getNameKey()));
        return keys;
    }

    /** Stars or un-stars the food of one of the member's entries. Returns true when it's now starred. */
    @Transactional
    public boolean toggleFavorite(Long userId, Long entryId) {
        String k = key(entry(userId, entryId).getItemName());
        Optional<FavoriteFood> existing = favorites.findByUserIdAndNameKey(userId, k);
        if (existing.isPresent()) {
            favorites.delete(existing.get());
            return false;
        }
        FavoriteFood f = new FavoriteFood();
        f.setUser(users.getReferenceById(userId));
        f.setNameKey(k.length() > 255 ? k.substring(0, 255) : k);
        favorites.save(f);
        return true;
    }

    /** What was eaten for a meal on the day before {@code day}, when that meal is still empty on {@code day}. */
    public List<CalorieInformation> sameAsYesterday(Long userId, LocalDate day, String mealType) {
        if (mealType == null) {
            return List.of();
        }
        boolean already = entriesOn(userId, day).stream().anyMatch(e -> mealType.equals(e.getMealType()));
        return already ? List.of() : entriesOn(userId, day.minusDays(1)).stream()
                .filter(e -> mealType.equals(e.getMealType())).toList();
    }

    /** Copies every entry of one meal from one day to another. Returns how many were added. */
    @Transactional
    public int copyMeal(Long userId, LocalDate from, String mealType, LocalDate to) {
        int n = 0;
        for (CalorieInformation e : entriesOn(userId, from)) {
            if (mealType.equals(e.getMealType())) {
                relog(userId, e.getId(), to, mealType);
                n++;
            }
        }
        return n;
    }

    public DayTotals totals(List<CalorieInformation> dayEntries) {
        return new DayTotals(
                dayEntries.stream().mapToDouble(CalorieInformation::getCalories).sum(),
                dayEntries.stream().mapToDouble(CalorieInformation::getProteins).sum(),
                dayEntries.stream().mapToDouble(CalorieInformation::getCarbohydrates).sum(),
                dayEntries.stream().mapToDouble(CalorieInformation::getFats).sum(),
                dayEntries.stream().mapToDouble(CalorieInformation::getFiber).sum(),
                dayEntries.stream().mapToDouble(CalorieInformation::getSodium).sum(),
                dayEntries.stream().mapToDouble(CalorieInformation::getSugars).sum(),
                dayEntries.size());
    }

    public record DayTotals(double calories, double protein, double carbs, double fat, double fiber, double sodium,
                            double sugars, int entries) {
        /** Share of energy from each macro (protein, carbs, fat), 0-100. */
        public double[] macroSplit() {
            double p = protein * 4, c = carbs * 4, f = fat * 9, total = p + c + f;
            return total == 0 ? new double[]{0, 0, 0} : new double[]{p / total * 100, c / total * 100, f / total * 100};
        }
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
        current.setDailyFiber(values.getDailyFiber());
        current.setDailyWaterOz(values.getDailyWaterOz());
        targets.save(current);
    }

    @Transactional(readOnly = true)
    public double caloriesToday(Long userId) {
        return entries.sumCaloriesForDay(userId, todayService.today(userId));
    }

    private static double nz(Double v) {
        return v == null ? 0 : v;
    }

    private static void copyEditableFields(CalorieInformation from, CalorieInformation to) {
        to.setItemName(from.getItemName() == null ? null : from.getItemName().trim());
        to.setDate(from.getDate() != null ? from.getDate() : to.getDate());
        to.setMealType(from.getMealType());
        to.setCalories(nz(from.getCalories()));
        to.setProteins(nz(from.getProteins()));
        to.setFats(nz(from.getFats()));
        to.setCarbohydrates(nz(from.getCarbohydrates()));
        to.setFiber(nz(from.getFiber()));
        to.setSugars(nz(from.getSugars()));
        to.setSodium(nz(from.getSodium()));
        to.setCholesterol(nz(from.getCholesterol()));
    }
}
