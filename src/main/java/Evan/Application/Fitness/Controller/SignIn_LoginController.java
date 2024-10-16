package Evan.Application.Fitness.Controller;

import Evan.Application.Fitness.Model.Roles;
import Evan.Application.Fitness.Model.UserInformation;
import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Repositorys.RoleRepository;
import Evan.Application.Fitness.Repositorys.UserInformationRepository;
import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.security.Principal;

@Controller
public class SignIn_LoginController {
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    UserInformationRepository userInformationRepository;
    @Autowired
    UserLoginDetailsRepository userLoginDetailsRepository;
    @Autowired
    RoleRepository roleRepository;

    @GetMapping("/home")
    public String getSignUpPage() {
        return "home";
    }

    @GetMapping("/signup")
    public String getSignUpPage(Model model) {
        model.addAttribute("userLoginDetails", new UserLoginDetails());
        model.addAttribute("userInformation", new UserInformation());
        return "signin"; // return the signup.html page
    }

    @PostMapping("/submitSignup")
    public String SubmissionForSignUp(
            @ModelAttribute UserLoginDetails userLoginDetails,
            @ModelAttribute UserInformation userInformation,
            Principal principal, String username, String password) {


        userLoginDetails.setUsername(username);
        userLoginDetails.setPassword(passwordEncoder.encode(password));


        userInformation.setName(userInformation.getName());
        userInformation.setAge(userInformation.getAge());
        userInformation.setWeight(userInformation.getWeight());


        userInformation.setUserLoginDetails(userLoginDetails);
        userLoginDetails.setUserInformation(userInformation);

        // Handle roles
        Roles defaultRole = roleRepository.findByName("ROLE_USER");
        if (defaultRole == null) {
            defaultRole = new Roles();
            defaultRole.setName("ROLE_USER");
            defaultRole = roleRepository.save(defaultRole);
        }

        // Assign role and save user
        userLoginDetails.getRoles().add(defaultRole);
        userLoginDetailsRepository.save(userLoginDetails);
        userInformationRepository.save(userInformation);

        return "index";
    }
}

