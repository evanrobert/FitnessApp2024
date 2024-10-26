package Evan.Application.Fitness.Controller;

import Evan.Application.Fitness.Model.CalorieInformation;
import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Repositorys.CalorieInformationRepository;
import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import org.hibernate.validator.constraints.ParameterScriptAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;
import java.util.List;

@Controller
public class ViewLoggedNutritionController {
    @Autowired
    CalorieInformationRepository calorieInformationRepository;
    @Autowired
    UserLoginDetailsRepository userLoginDetailsRepository;

    @GetMapping("/view/Nutrition")
    public String getAllNutritionInfo(Model model, Principal principal) {
        try {
            String username = principal.getName();
            UserLoginDetails userLoginDetails = userLoginDetailsRepository.findByUsername(username);
            List<CalorieInformation> calorieInformation = calorieInformationRepository.findByUserLoginDetails(userLoginDetails);
            model.addAttribute("calorieInformation", calorieInformation);
            return "nutritionLog";
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "error";
    }
}
//    @GetMapping("/filter/by/nutrition")
//    public String getAllMealTypes(String mealtype){
//        List<CalorieInformation> calorieInformation = calorieInformationRepository.findAllByMealType(mealtype);
//        return "nutritionlog";
//    }
//}
