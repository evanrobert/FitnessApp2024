package Evan.Application.Fitness.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Keeps bookmarks from the original app working after the routes were reorganised. */
@Controller
public class LegacyRedirectController {
    @GetMapping("/add/custom/meal")
    public String addMeal() {
        return "redirect:/fuel/new";
    }

    @GetMapping("/view/Nutrition")
    public String nutritionLog() {
        return "redirect:/fuel/ledger";
    }

    @GetMapping("/total")
    public String total() {
        return "redirect:/fuel";
    }

    @GetMapping({"/user/macro/information", "/ModifyDailyIntake"})
    public String targets() {
        return "redirect:/fuel/targets";
    }

    @GetMapping("/download/nutrition")
    public String download() {
        return "redirect:/export/nutrition.csv";
    }

    @GetMapping("/workouts")
    public String workouts() {
        return "redirect:/train";
    }
}
