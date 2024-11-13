package Evan.Application.Fitness.Controller;

import Evan.Application.Fitness.Model.CalorieInformation;
import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Repositorys.CalorieInformationRepository;
import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.security.Principal;
import java.util.Date;

@Controller
public class LogCustomMealController {
    @Autowired
    CalorieInformationRepository calorieInformationRepository;
    @Autowired
    UserLoginDetailsRepository userLoginDetailsRepository;
    @GetMapping("/add/custom/meal")
    private String addCustomMealOption(Model model){
        model.addAttribute("calorieInformation",new CalorieInformation());
        return "CustomMeal";
    }
    @PostMapping("/Post/Custom/Meal")
    private String postCustomMealOption(Principal principal, @ModelAttribute CalorieInformation calorieInformation){
        String username = principal.getName();
        UserLoginDetails userLoginDetails = userLoginDetailsRepository.findByUsername(username);
        calorieInformation.setUserLoginDetails(userLoginDetails);
        calorieInformation.setDate(new Date());
       try{
        calorieInformationRepository.save(calorieInformation);
        return "home";
       } catch (Exception e){
           e.printStackTrace();
       }
        return "redirect:/error";
    }
}
