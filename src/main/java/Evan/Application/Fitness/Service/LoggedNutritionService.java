package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Model.CalorieInformation;
import Evan.Application.Fitness.Model.Nutrition;
import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Repositorys.CalorieInformationRepository;

import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import java.security.Principal;


@Service
public class LoggedNutritionService {
    @Autowired
    UserLoginDetailsRepository userLoginDetailsRepository;
    @Autowired
    CalorieInformationRepository calorieInformationRepository;

    public String editLoggedNutritionInfo(Principal principal, Model model, CalorieInformation calorieInformation) {
        String username = principal.getName();
        UserLoginDetails userLoginDetails = userLoginDetailsRepository.findByUsername(username);

        if (userLoginDetails == null) {
            model.addAttribute("error", "User login credentials not found");
            return "error";
        }

        CalorieInformation existingNutritionInformation = calorieInformationRepository.findByUserLoginDetails(userLoginDetails);

        if (existingNutritionInformation == null) {
            // Handle the case when no existing nutrition information is found
            existingNutritionInformation = new CalorieInformation();
            existingNutritionInformation.setUserLoginDetails(userLoginDetails); // Set the user
        }

        // Set the new values
        existingNutritionInformation.setProteins(calorieInformation.getProteins());
        existingNutritionInformation.setFats(calorieInformation.getFats());
        existingNutritionInformation.setDate(calorieInformation.getDate());
        existingNutritionInformation.setCalories(calorieInformation.getCalories());
        existingNutritionInformation.setCholesterol(calorieInformation.getCholesterol());
        existingNutritionInformation.setFiber(calorieInformation.getFiber());
        existingNutritionInformation.setItemName(calorieInformation.getItemName());
        existingNutritionInformation.setSodium(calorieInformation.getSodium());
        existingNutritionInformation.setMealType(calorieInformation.getMealType());
        existingNutritionInformation.setCarbohydrates(calorieInformation.getCarbohydrates());

        // Save the entity
        calorieInformationRepository.save(existingNutritionInformation);
        return "redirect:/view/Nutrition";  // Redirect to the view page after saving
    }
}
