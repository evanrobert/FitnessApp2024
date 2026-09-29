package Evan.Application.Fitness.Controller;

import Evan.Application.Fitness.Model.CalorieInformation;
import Evan.Application.Fitness.Model.CalorieInformationForm;
import Evan.Application.Fitness.Model.UserMacroInformation;
import Evan.Application.Fitness.Security.AppUserPrincipal;
import Evan.Application.Fitness.Service.NutritionService;
import Evan.Application.Fitness.Service.ProfileService;
import Evan.Application.Fitness.Service.TodayService;
import Evan.Application.Fitness.Web.ChartJson;
import Evan.Application.Fitness.Web.Flash;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/fuel")
public class FuelController {
    /** Meal slots in day order; includes training-timing slots so intake can be lined up with sessions. */
    public static final Map<String, String> MEAL_TYPES = new LinkedHashMap<>();

    static {
        MEAL_TYPES.put("BREAKFAST", "Breakfast");
        MEAL_TYPES.put("LUNCH", "Lunch");
        MEAL_TYPES.put("PRE_WORKOUT", "Pre-workout");
        MEAL_TYPES.put("POST_WORKOUT", "Post-workout");
        MEAL_TYPES.put("DINNER", "Dinner");
        MEAL_TYPES.put("SNACK", "Snack");
    }

    private final NutritionService nutrition;
    private final ProfileService profiles;
    private final TodayService todayService;
    private final ChartJson charts;

    public FuelController(NutritionService nutrition, ProfileService profiles, TodayService todayService, ChartJson charts) {
        this.nutrition = nutrition;
        this.profiles = profiles;
        this.todayService = todayService;
        this.charts = charts;
    }

    @ModelAttribute("mealTypes")
    public Map<String, String> mealTypes() {
        return MEAL_TYPES;
    }

    @GetMapping
    public String day(@AuthenticationPrincipal AppUserPrincipal me,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                      Model model) {
        Long userId = me.getId();
        LocalDate today = todayService.today(userId);
        LocalDate day = date == null || date.isAfter(today) ? today : date;
        List<CalorieInformation> entries = nutrition.entriesOn(userId, day);
        UserMacroInformation targets = nutrition.targetsFor(userId).orElse(null);

        Map<String, List<CalorieInformation>> byMeal = new LinkedHashMap<>();
        MEAL_TYPES.keySet().forEach(k -> byMeal.put(k, new ArrayList<>()));
        entries.forEach(e -> byMeal.computeIfAbsent(e.getMealType() == null ? "SNACK" : e.getMealType(), k -> new ArrayList<>()).add(e));
        byMeal.values().removeIf(List::isEmpty);

        // 14-day calorie history ending on the viewed day.
        List<String> labels = new ArrayList<>();
        List<Double> calories = new ArrayList<>();
        Map<LocalDate, Double> perDay = new HashMap<>();
        nutrition.between(userId, day.minusDays(13), day).forEach(e -> perDay.merge(e.getDate(), e.getCalories(), Double::sum));
        for (int i = 13; i >= 0; i--) {
            LocalDate d = day.minusDays(i);
            labels.add(d.toString());
            calories.add(Math.round(perDay.getOrDefault(d, 0.0) * 10) / 10.0);
        }
        Map<String, Object> chart = ChartJson.spec("bar", "labels", labels, "values", calories, "unit", "kcal",
                "name", "Calories", "highlight", 13, "title", "Calories, last 14 days",
                "target", targets != null && targets.getDailyCalories() > 0 ? ChartJson.target(targets.getDailyCalories(), "Target") : null,
                "empty", "Log meals to see your daily intake trend.");

        CalorieInformation quick = new CalorieInformation();
        quick.setDate(day);
        quick.setMealType(defaultMealType(me.getId()));

        model.addAttribute("day", day);
        model.addAttribute("today", today);
        model.addAttribute("isToday", day.equals(today));
        model.addAttribute("totals", nutrition.totals(entries));
        model.addAttribute("targets", targets);
        model.addAttribute("byMeal", byMeal);
        model.addAttribute("recent", nutrition.recentFoods(userId, 8));
        model.addAttribute("calorieChart", charts.write(chart));
        model.addAttribute("calorieInformation", quick);
        return "fuel/day";
    }

    @GetMapping("/new")
    public String newMeal(@AuthenticationPrincipal AppUserPrincipal me,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                          @RequestParam(required = false) String type, Model model) {
        CalorieInformation meal = new CalorieInformation();
        meal.setDate(date == null ? todayService.today(me.getId()) : date);
        meal.setMealType(type != null && MEAL_TYPES.containsKey(type) ? type : defaultMealType(me.getId()));
        model.addAttribute("calorieInformation", meal);
        return "fuel/form";
    }

    @PostMapping
    public String create(@AuthenticationPrincipal AppUserPrincipal me,
                         @Valid @ModelAttribute CalorieInformation calorieInformation, BindingResult result,
                         @RequestParam(defaultValue = "false") boolean another, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return "fuel/form";
        }
        CalorieInformation saved = nutrition.logMeal(me.getId(), calorieInformation);
        Flash.success(redirect, saved.getItemName() + " logged · " + Math.round(saved.getCalories()) + " kcal");
        return another ? "redirect:/fuel/new?date=" + saved.getDate() + "&type=" + saved.getMealType()
                : "redirect:/fuel?date=" + saved.getDate();
    }

    @GetMapping("/{id}/edit")
    public String edit(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, Model model) {
        model.addAttribute("calorieInformation", nutrition.entry(me.getId(), id));
        model.addAttribute("entryId", id);
        return "fuel/form";
    }

    @PostMapping("/{id}")
    public String update(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id,
                         @Valid @ModelAttribute CalorieInformation calorieInformation, BindingResult result, Model model,
                         RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("entryId", id);
            return "fuel/form";
        }
        nutrition.updateEntry(me.getId(), id, calorieInformation);
        Flash.success(redirect, "Entry updated");
        return "redirect:/fuel?date=" + nutrition.entry(me.getId(), id).getDate();
    }

    @PostMapping("/{id}/relog")
    public String relog(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                        RedirectAttributes redirect) {
        CalorieInformation copy = nutrition.relog(me.getId(), id, date);
        Flash.success(redirect, copy.getItemName() + " logged again · " + Math.round(copy.getCalories()) + " kcal");
        return "redirect:/fuel?date=" + copy.getDate();
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id,
                         @RequestParam(required = false) String returnTo, RedirectAttributes redirect) {
        LocalDate date = nutrition.entry(me.getId(), id).getDate();
        nutrition.deleteEntry(me.getId(), id);
        Flash.success(redirect, "Entry deleted");
        return "redirect:" + Evan.Application.Fitness.Web.ViewAdvice.safeReturn(returnTo, "/fuel?date=" + date);
    }

    @GetMapping("/ledger")
    public String ledger(@AuthenticationPrincipal AppUserPrincipal me, Model model) {
        CalorieInformationForm form = new CalorieInformationForm();
        form.setCalorieInformationList(nutrition.entriesFor(me.getId()));
        model.addAttribute("calorieInformationForm", form);
        return "fuel/ledger";
    }

    @PostMapping("/ledger")
    public String saveLedger(@AuthenticationPrincipal AppUserPrincipal me,
                             @ModelAttribute CalorieInformationForm calorieInformationForm, RedirectAttributes redirect) {
        int updated = nutrition.updateEntries(me.getId(), calorieInformationForm.getCalorieInformationList());
        Flash.success(redirect, updated + (updated == 1 ? " entry" : " entries") + " saved");
        return "redirect:/fuel/ledger";
    }

    @GetMapping("/targets")
    public String targets(@AuthenticationPrincipal AppUserPrincipal me, Model model) {
        if (!model.containsAttribute("userMacroInformation")) {
            model.addAttribute("userMacroInformation", nutrition.targetsFor(me.getId()).orElseGet(UserMacroInformation::new));
        }
        model.addAttribute("estimate", profiles.estimate(me.getId()));
        return "fuel/targets";
    }

    @PostMapping("/targets")
    public String saveTargets(@AuthenticationPrincipal AppUserPrincipal me,
                              @Valid @ModelAttribute UserMacroInformation userMacroInformation, BindingResult result,
                              Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("estimate", profiles.estimate(me.getId()));
            return "fuel/targets";
        }
        nutrition.saveTargets(me.getId(), userMacroInformation);
        Flash.success(redirect, "Daily targets saved");
        return "redirect:/fuel";
    }

    /** Suggest the meal slot by local time of day, so the common case needs no change. */
    private String defaultMealType(Long userId) {
        int hour = java.time.LocalTime.now(todayService.zoneFor(userId)).getHour();
        return hour < 11 ? "BREAKFAST" : hour < 15 ? "LUNCH" : hour < 21 ? "DINNER" : "SNACK";
    }
}
