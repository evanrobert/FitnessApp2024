package Evan.Application.Fitness.Controller;

import Evan.Application.Fitness.Model.CalorieInformation;
import Evan.Application.Fitness.Model.CalorieInformationForm;
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
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;


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

            // Wrap in CalorieInformationForm
            CalorieInformationForm form = new CalorieInformationForm();
            form.setCalorieInformationList(calorieInformation);

            model.addAttribute("calorieInformationForm", form);

            return "nutritionLog";
        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }
    }


    @GetMapping("/filter/by/nutrition")
    public String filterByMealType(@RequestParam("mealtype") String mealtype, Model model, RedirectAttributes redirectAttributes) {
        List<CalorieInformation> filteredNutrition = calorieInformationRepository.findByMealType(mealtype);

        if (filteredNutrition.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "No entries found for this meal type.");
            return "redirect:/view/Nutrition";
        }

        // Wrap the list in CalorieInformationForm
        CalorieInformationForm form = new CalorieInformationForm();
        form.setCalorieInformationList(filteredNutrition);

        model.addAttribute("calorieInformationForm", form);

        return "nutritionLog";
    }

    @PostMapping("/edit/nutrition/information")
    public String updateDailyCalorieIntake(
            Principal principal,
            @ModelAttribute("calorieInformationForm") CalorieInformationForm form,
            Model model) {

        List<CalorieInformation> calorieInformation = form.getCalorieInformationList();
        String result = loggedNutritionService.editLoggedNutritionInfo(principal, model, calorieInformation);

        return result.equals("error") ? "error" : "redirect:/home";
    }

    @GetMapping("/download/nutrition")
    public void downloadNutritionCsv(Principal principal,
                                     HttpServletResponse response) {
        try {
            String username = principal.getName();
            UserLoginDetails user =
                    userLoginDetailsRepository.findByUsername(username);

            List<CalorieInformation> entries =
                    calorieInformationRepository.findAllByUserLoginDetails(user);

            response.setContentType("text/csv");
            response.setHeader(
                    "Content-Disposition",
                    "attachment; filename=nutrition-log.csv"
            );

            PrintWriter writer = response.getWriter();

            // CSV header
            writer.println("Item,Date,Calories,Protein,Fat,Carbs,Fiber,Sugars,Sodium,Cholesterol,MealType");

            // CSV rows
            for (CalorieInformation c : entries) {
                writer.printf(
                        "%s,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%s%n",
                        c.getItemName(),
                        c.getDate(),
                        c.getCalories(),
                        c.getProteins(),
                        c.getFats(),
                        c.getCarbohydrates(),
                        c.getFiber(),
                        c.getSugars(),
                        c.getSodium(),
                        c.getCholesterol(),
                        c.getMealType()
                );
            }

            writer.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }





}

