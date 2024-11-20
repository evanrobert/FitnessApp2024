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

    public String editLoggedNutritionInfo(Principal principal, Model model, CalorieInformation calorieInformation){
        String username = principal.getName();
        UserLoginDetails userLoginDetails = userLoginDetailsRepository.findByUsername(username);

        if (userLoginDetails == null) {
            model.addAttribute("error", "User login credentials not found");
            return "error";
        }
        CalorieInformation existingNutritionInformation = calorieInformationRepository.findByUserLoginDetails(userLoginDetails);
        existingNutritionInformation.setProteins(existingNutritionInformation.getProteins());
        existingNutritionInformation.setFats(existingNutritionInformation.getFats());
        existingNutritionInformation.setDate(existingNutritionInformation.getDate());
        existingNutritionInformation.setCalories(existingNutritionInformation.getCalories());
        existingNutritionInformation.setCholesterol(existingNutritionInformation.getCholesterol());
        existingNutritionInformation.setFiber(existingNutritionInformation.getFiber());
        existingNutritionInformation.setItemName(existingNutritionInformation.getItemName());
        existingNutritionInformation.setSodium(existingNutritionInformation.getSodium());
        existingNutritionInformation.setMealType(existingNutritionInformation.getMealType());
        existingNutritionInformation.setCarbohydrates(existingNutritionInformation.getCarbohydrates());
        return "home";
    }
}
