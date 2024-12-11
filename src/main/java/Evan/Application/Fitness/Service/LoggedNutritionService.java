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

    public String editLoggedNutritionInfo(Principal principal, Model model, List<CalorieInformation> calorieInformationList) {
        String username = principal.getName();
        UserLoginDetails userLoginDetails = userLoginDetailsRepository.findByUsername(username);

        if (userLoginDetails == null) {
            model.addAttribute("error", "User login credentials not found");
            return "error";
        }

        // Fetch all matching nutrition records for the user
        List<CalorieInformation> nutritionList = calorieInformationRepository.findByUserLoginDetails(userLoginDetails);

        // Iterate through the calorieInformationList and update each record
        for (CalorieInformation calorieInformation : calorieInformationList) {
            // Check if the entry already exists, otherwise create a new one
            Optional<CalorieInformation> existingNutrition = nutritionList.stream()
                    .filter(item -> item.getId().equals(calorieInformation.getId()))
                    .findFirst();

            CalorieInformation nutritionToSave;
            if (existingNutrition.isPresent()) {
                nutritionToSave = existingNutrition.get();  // Update the existing record
            } else {
                nutritionToSave = new CalorieInformation();  // Create a new one
                nutritionToSave.setUserLoginDetails(userLoginDetails);
            }

            // Update the fields
            nutritionToSave.setProteins(calorieInformation.getProteins());
            nutritionToSave.setFats(calorieInformation.getFats());
            nutritionToSave.setDate(calorieInformation.getDate());
            nutritionToSave.setCalories(calorieInformation.getCalories());
            nutritionToSave.setCholesterol(calorieInformation.getCholesterol());
            nutritionToSave.setFiber(calorieInformation.getFiber());
            nutritionToSave.setItemName(calorieInformation.getItemName());
            nutritionToSave.setSodium(calorieInformation.getSodium());
            nutritionToSave.setMealType(calorieInformation.getMealType());
            nutritionToSave.setCarbohydrates(calorieInformation.getCarbohydrates());

            // Save the updated or newly created entity
            calorieInformationRepository.save(nutritionToSave);
        }

        return "redirect:/view/Nutrition";  // Redirect after saving
    }


}
