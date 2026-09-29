package Evan.Application.Fitness.Controller;

import Evan.Application.Fitness.Model.UserMacroInformation;
import Evan.Application.Fitness.Security.AppUserPrincipal;
import Evan.Application.Fitness.Service.NutritionService;
import Evan.Application.Fitness.Web.Flash;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class UserMacroInformationController {
    private final NutritionService nutrition;

    public UserMacroInformationController(NutritionService nutrition) {
        this.nutrition = nutrition;
    }

    @GetMapping({"/user/macro/information", "/ModifyDailyIntake"})
    public String targets(@AuthenticationPrincipal AppUserPrincipal me, Model model) {
        model.addAttribute("userMacroInformation",
                nutrition.targetsFor(me.getId()).orElseGet(UserMacroInformation::new));
        return "ModifyDailyIntake";
    }

    @PostMapping({"/edit/macro/information", "/log/custom/macro/information"})
    public String saveTargets(@AuthenticationPrincipal AppUserPrincipal me,
                              @Valid @ModelAttribute UserMacroInformation userMacroInformation, BindingResult result,
                              RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return "ModifyDailyIntake";
        }
        nutrition.saveTargets(me.getId(), userMacroInformation);
        Flash.success(redirect, "Daily targets saved");
        return "redirect:/home";
    }

    @GetMapping("/total")
    public String summary(@AuthenticationPrincipal AppUserPrincipal me, Model model) {
        double consumed = nutrition.caloriesToday(me.getId());
        double goal = nutrition.targetsFor(me.getId()).map(UserMacroInformation::getDailyCalories).orElse(0.0);
        model.addAttribute("dailyGoal", goal);
        model.addAttribute("caloriesConsumed", consumed);
        model.addAttribute("remainingCalories", goal - consumed);
        return "CalorieSummary";
    }
}
