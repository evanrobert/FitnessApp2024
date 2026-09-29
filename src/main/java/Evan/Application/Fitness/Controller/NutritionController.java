package Evan.Application.Fitness.Controller;

import Evan.Application.Fitness.Model.CalorieInformation;
import Evan.Application.Fitness.Model.CalorieInformationForm;
import Evan.Application.Fitness.Security.AppUserPrincipal;
import Evan.Application.Fitness.Service.CsvExporter;
import Evan.Application.Fitness.Service.NutritionService;
import Evan.Application.Fitness.Web.Flash;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

@Controller
public class NutritionController {
    private final NutritionService nutrition;

    public NutritionController(NutritionService nutrition) {
        this.nutrition = nutrition;
    }

    @GetMapping("/add/custom/meal")
    public String mealForm(Model model) {
        model.addAttribute("calorieInformation", new CalorieInformation());
        return "CustomMeal";
    }

    @PostMapping("/Post/Custom/Meal")
    public String logMeal(@AuthenticationPrincipal AppUserPrincipal me,
                          @Valid @ModelAttribute CalorieInformation calorieInformation, BindingResult result,
                          RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return "CustomMeal";
        }
        nutrition.logMeal(me.getId(), calorieInformation);
        Flash.success(redirect, "Meal logged");
        return "redirect:/home";
    }

    @GetMapping("/view/Nutrition")
    public String log(@AuthenticationPrincipal AppUserPrincipal me, Model model) {
        CalorieInformationForm form = new CalorieInformationForm();
        form.setCalorieInformationList(nutrition.entriesFor(me.getId()));
        model.addAttribute("calorieInformationForm", form);
        return "NutritionLog";
    }

    @PostMapping("/edit/nutrition/information")
    public String bulkEdit(@AuthenticationPrincipal AppUserPrincipal me,
                           @ModelAttribute CalorieInformationForm calorieInformationForm, RedirectAttributes redirect) {
        int updated = nutrition.updateEntries(me.getId(), calorieInformationForm.getCalorieInformationList());
        Flash.success(redirect, updated + (updated == 1 ? " entry" : " entries") + " saved");
        return "redirect:/view/Nutrition";
    }

    @PostMapping("/nutrition/{id}/delete")
    public String delete(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id,
                         RedirectAttributes redirect) {
        nutrition.deleteEntry(me.getId(), id);
        Flash.success(redirect, "Entry deleted");
        return "redirect:/view/Nutrition";
    }

    @GetMapping("/download/nutrition")
    public void downloadCsv(@AuthenticationPrincipal AppUserPrincipal me, HttpServletResponse response)
            throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=nutrition-log.csv");
        List<List<Object>> rows = nutrition.entriesFor(me.getId()).stream()
                .map(c -> List.<Object>of(nullToEmpty(c.getItemName()), c.getDate() == null ? "" : c.getDate().toString(),
                        c.getCalories(), c.getProteins(), c.getFats(), c.getCarbohydrates(), c.getFiber(),
                        c.getSugars(), c.getSodium(), c.getCholesterol(), nullToEmpty(c.getMealType())))
                .toList();
        CsvExporter.write(response.getWriter(), List.of("Item", "Date", "Calories", "Protein", "Fat", "Carbs",
                "Fiber", "Sugars", "Sodium", "Cholesterol", "MealType"), rows);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
