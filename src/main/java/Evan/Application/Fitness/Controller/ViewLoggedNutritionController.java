package Evan.Application.Fitness.Controller;

import Evan.Application.Fitness.Model.CalorieInformation;
import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Model.UserMacroInformation;
import Evan.Application.Fitness.Repositorys.CalorieInformationRepository;
import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import Evan.Application.Fitness.Service.LoggedNutritionService;
import org.hibernate.validator.constraints.ParameterScriptAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;

@Controller
public class ViewLoggedNutritionController {
    @Autowired
    CalorieInformationRepository calorieInformationRepository;
    @Autowired
    UserLoginDetailsRepository userLoginDetailsRepository;
    @Autowired
    LoggedNutritionService loggedNutritionService;

    @GetMapping("/view/Nutrition")
    public String getAllNutritionInfo(Model model, Principal principal) {
        try {
            String username = principal.getName();
            UserLoginDetails userLoginDetails = userLoginDetailsRepository.findByUsername(username);
            List<CalorieInformation> calorieInformation = calorieInformationRepository.findAllByUserLoginDetails(userLoginDetails);
            model.addAttribute("calorieInformation", calorieInformation);
            return "nutritionLog";
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "error";
    }

    @GetMapping("/filter/by/nutrition")
    public String filterByMealType(@RequestParam("mealtype") String mealtype, Model model, RedirectAttributes redirectAttributes) {
        List<CalorieInformation> filteredNutrition = calorieInformationRepository.findByMealType(mealtype);

        if (filteredNutrition.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "No entries found for this meal type.");
            return "redirect:/view/Nutrition";
        }

        model.addAttribute("calorieInformation", filteredNutrition);
        return "nutritionLog";
    }
    @PostMapping("/edit/nutrition/information")
    public String updateDailyCalorieIntake(
            Principal principal,
            @ModelAttribute CalorieInformation calorieInformation,
            Model model) {
        String result = loggedNutritionService.editLoggedNutritionInfo(principal,model,calorieInformation);
        return result.equals("error") ? "error" : "redirect:/view/Nutrition";
    }

}

