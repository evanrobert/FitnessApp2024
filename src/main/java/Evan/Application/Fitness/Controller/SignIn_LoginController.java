package Evan.Application.Fitness.Controller;

import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.security.Principal;

@Controller
public class SignIn_LoginController {
    @Autowired
    UserDetailsService userDetailsService;
    @Autowired
    UserLoginDetailsRepository userLoginDetailsRepository;
    @GetMapping("/home")
    public String getSignUpPage(){
        return "home";
    }
   
}
