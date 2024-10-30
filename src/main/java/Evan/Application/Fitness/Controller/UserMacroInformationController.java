package Evan.Application.Fitness.Controller;

import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Model.UserMacroInformation;
import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import Evan.Application.Fitness.Repositorys.UserMacroInformationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.security.Principal;

@Controller
public class UserMacroInformationController {
    @Autowired
    UserLoginDetailsRepository userLoginDetailsRepository;
    @Autowired
    UserMacroInformationRepository userMacroInformationRepository;
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
    public String modifyDailyIntake() {
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
}
