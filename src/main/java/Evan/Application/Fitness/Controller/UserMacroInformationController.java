package Evan.Application.Fitness.Controller;

import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Model.UserMacroInformation;
import Evan.Application.Fitness.Repositorys.CalorieInformationRepository;
import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import Evan.Application.Fitness.Repositorys.UserMacroInformationRepository;
import Evan.Application.Fitness.Service.CaloriesLeftService;
import Evan.Application.Fitness.Service.DailyIntakeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.security.Principal;
import java.util.Optional;

@Controller
public class UserMacroInformationController {
    @Autowired
    CaloriesLeftService caloriesLeftService;
    @Autowired
    UserLoginDetailsRepository userLoginDetailsRepository;
    @Autowired
    UserMacroInformationRepository userMacroInformationRepository;
    @Autowired
    CalorieInformationRepository calorieInformationRepository;
    @Autowired
    DailyIntakeService dailyIntakeService;
    @GetMapping("/user/macro/information")
    public String logDailyMacroInformation(Model model, Principal principal){
        String username = principal.getName();
        UserLoginDetails userLoginDetails = userLoginDetailsRepository.findByUsername(username);
        boolean ifDataIsLogged = userMacroInformationRepository.existsByUserLoginDetails(userLoginDetails);
        if(ifDataIsLogged){
            return "redirect:/ModifyDailyIntake";
        }

        model.addAttribute("macros",new UserMacroInformation());
        return "dailyMacro";

    }
    @GetMapping("/ModifyDailyIntake")
    public String modifyDailyIntake(Model model, Principal principal) {
        String username = principal.getName();
        UserLoginDetails userLoginDetails = userLoginDetailsRepository.findByUsername(username);

        UserMacroInformation macroInfo = userMacroInformationRepository.findByUserLoginDetails(userLoginDetails);
        if (macroInfo == null) {
            macroInfo = new UserMacroInformation(); // create empty if not present
        }

        model.addAttribute("userMacroInformation", macroInfo);

        return "ModifyDailyIntake";
    }



    @PostMapping("/log/custom/macro/information")
    public String setCustomMacroInformation(Principal principal, @ModelAttribute UserMacroInformation userMacroInformation){
        String username = principal.getName();
        UserLoginDetails userLoginDetails = userLoginDetailsRepository.findByUsername(username);
       userMacroInformation.setUserLoginDetails(userLoginDetails);
        try {
            userMacroInformationRepository.save(userMacroInformation);
            return "home";
        } catch (Exception e){
            e.printStackTrace();
        }
        return "error";
    }


    @PostMapping("/edit/macro/information")
    public String updateDailyCalorieIntake(
            Principal principal,
            @ModelAttribute UserMacroInformation userMacroInformation,
            Model model) {
        String result = dailyIntakeService.modifyDailyIntakeTracker(userMacroInformation, principal, model);
        return result.equals("error") ? "error" : "redirect:/home";
    }



    @GetMapping("/total")
    public String getTotalCalories(Model model, Principal principal) {

        // 1️⃣ Get logged-in user
        String username = principal.getName();
        UserLoginDetails user = userLoginDetailsRepository.findByUsername(username);

        // 2️⃣ Get calories consumed today
        double caloriesConsumed = calorieInformationRepository.sumCaloriesTodayByUser(user);

        // 3️⃣ Get daily goal
        UserMacroInformation macroInfo = userMacroInformationRepository.findByUserLoginDetails(user);
        double dailyGoal = macroInfo != null ? macroInfo.getDailyCalories() : 0;

        // 4️⃣ Calculate remaining
        double remainingCalories = dailyGoal - caloriesConsumed;

        // 5️⃣ Add to model
        model.addAttribute("dailyGoal", dailyGoal);
        model.addAttribute("caloriesConsumed", caloriesConsumed);
        model.addAttribute("remainingCalories", remainingCalories);

        return "CalorieSummary";
    }




}



