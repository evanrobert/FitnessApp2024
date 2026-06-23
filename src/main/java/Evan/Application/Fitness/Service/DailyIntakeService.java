package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Model.UserMacroInformation;
import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import Evan.Application.Fitness.Repositorys.UserMacroInformationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import java.security.Principal;

@Service
public class DailyIntakeService {
    @Autowired
    UserMacroInformationRepository userMacroInformationRepository;
    @Autowired
    UserLoginDetailsRepository userLoginDetailsRepository;
    public String modifyDailyIntakeTracker(UserMacroInformation oldMacroInformation, Principal principal, Model model){
        String username = principal.getName();
        UserLoginDetails userLoginDetails = userLoginDetailsRepository.findByUsername(username);

        if (userLoginDetails == null) {
            model.addAttribute("error", "User login credentials not found");
            return "error";
        }
UserMacroInformation existingUserMacroInformation = userMacroInformationRepository.findByUserLoginDetails(userLoginDetails);
    if (existingUserMacroInformation == null) {
        existingUserMacroInformation = new UserMacroInformation();
        existingUserMacroInformation.setUserLoginDetails(userLoginDetails);
    }
    existingUserMacroInformation.setDailyCalories(oldMacroInformation.getDailyCalories());
    existingUserMacroInformation.setDailyProtein(oldMacroInformation.getDailyProtein());
    existingUserMacroInformation.setDailyFat(oldMacroInformation.getDailyFat());
    existingUserMacroInformation.setDailyCarbohydrates(oldMacroInformation.getDailyCarbohydrates());

    userMacroInformationRepository.save(existingUserMacroInformation);

    return "redirect:/home";



    }
}
