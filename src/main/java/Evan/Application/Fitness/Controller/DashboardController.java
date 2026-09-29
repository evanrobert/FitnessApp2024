package Evan.Application.Fitness.Controller;

import Evan.Application.Fitness.Model.UserMacroInformation;
import Evan.Application.Fitness.Model.WorkoutInformation;
import Evan.Application.Fitness.Repositorys.CalorieInformationRepository;
import Evan.Application.Fitness.Repositorys.WorkoutInformationRepository;
import Evan.Application.Fitness.Security.AppUserPrincipal;
import Evan.Application.Fitness.Service.NutritionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class DashboardController {
    private final NutritionService nutrition;
    private final CalorieInformationRepository entries;
    private final WorkoutInformationRepository workouts;

    public DashboardController(NutritionService nutrition, CalorieInformationRepository entries,
                               WorkoutInformationRepository workouts) {
        this.nutrition = nutrition;
        this.entries = entries;
        this.workouts = workouts;
    }

    @GetMapping("/home")
    public String home(@AuthenticationPrincipal AppUserPrincipal me, Model model) {
        UserMacroInformation macroInfo = nutrition.targetsFor(me.getId()).orElse(null);
        double dailyGoal = macroInfo != null ? macroInfo.getDailyCalories() : 0;
        double consumed = nutrition.caloriesToday(me.getId());
        List<WorkoutInformation> history = workouts.findAllByUserIdOrderByDateDescIdDesc(me.getId());

        model.addAttribute("dailyGoal", dailyGoal);
        model.addAttribute("caloriesConsumed", consumed);
        model.addAttribute("remainingCalories", dailyGoal - consumed);
        model.addAttribute("calorieProgress", dailyGoal > 0 ? Math.min(consumed / dailyGoal * 100, 100) : 0);
        model.addAttribute("mealCount", entries.countByUserId(me.getId()));
        model.addAttribute("workoutCount", history.size());
        model.addAttribute("trainingVolume", history.stream()
                .mapToDouble(w -> w.getSets() * w.getReps() * w.getWeight()).sum());
        model.addAttribute("macroInfo", macroInfo);
        return "home";
    }
}
