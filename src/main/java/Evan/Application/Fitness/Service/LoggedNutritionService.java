package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Model.CalorieInformation;
import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Repositorys.CalorieInformationRepository;

import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import java.security.Principal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;


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

        // Fetch all matching nutrition records for the user
        List<CalorieInformation> nutritionList = calorieInformationRepository.findAllByUserLoginDetails(userLoginDetails);

        CalorieInformation existingNutritionInformation;
        if (nutritionList.isEmpty()) {
            // No existing record, create a new one
            existingNutritionInformation = new CalorieInformation();
            existingNutritionInformation.setUserLoginDetails(userLoginDetails);
        } else if (nutritionList.size() == 1) {
            // Only one record exists
            existingNutritionInformation = nutritionList.get(0);
        } else {
            // Handle multiple records (e.g., select the most recent record)
            existingNutritionInformation = nutritionList.stream()
                    .max(Comparator.comparing(CalorieInformation::getDate)) // Choose the latest by date
                    .orElseThrow(() -> new IllegalStateException("Unexpected error while selecting the most recent record."));
        }

        // Update the entity with new values
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

        // Save the updated or newly created entity
        calorieInformationRepository.save(existingNutritionInformation);
        return "redirect:/view/Nutrition";  // Redirect after saving
    }
}
