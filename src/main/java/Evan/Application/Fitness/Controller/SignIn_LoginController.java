package Evan.Application.Fitness.Controller;

import Evan.Application.Fitness.Model.Roles;
import Evan.Application.Fitness.Model.UserMacroInformation;
import Evan.Application.Fitness.Model.UserInformation;
import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Model.WorkoutInformation;
import Evan.Application.Fitness.Repositorys.CalorieInformationRepository;
import Evan.Application.Fitness.Repositorys.RoleRepository;
import Evan.Application.Fitness.Repositorys.UserInformationRepository;
import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import Evan.Application.Fitness.Repositorys.UserMacroInformationRepository;
import Evan.Application.Fitness.Repositorys.WorkoutInformationRepository;
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
    @Autowired
    CalorieInformationRepository calorieInformationRepository;
    @Autowired
    UserMacroInformationRepository userMacroInformationRepository;
    @Autowired
    WorkoutInformationRepository workoutInformationRepository;

    @GetMapping("/home")
    public String getSignUpPage(Model model, Principal principal) {
        double dailyGoal = 0;
        double caloriesConsumed = 0;
        double remainingCalories = 0;
        double calorieProgress = 0;
        int mealCount = 0;
        int workoutCount = 0;
        double trainingVolume = 0;
        UserMacroInformation macroInfo = null;

        if (principal != null) {
            UserLoginDetails user = userLoginDetailsRepository.findByUsername(principal.getName());
            macroInfo = userMacroInformationRepository.findByUserLoginDetails(user);

            dailyGoal = macroInfo != null ? macroInfo.getDailyCalories() : 0;
            caloriesConsumed = calorieInformationRepository.sumCaloriesTodayByUser(user);
            remainingCalories = dailyGoal - caloriesConsumed;
            calorieProgress = dailyGoal > 0 ? Math.min((caloriesConsumed / dailyGoal) * 100, 100) : 0;
            mealCount = calorieInformationRepository.findAllByUserLoginDetails(user).size();
            java.util.List<WorkoutInformation> workouts = workoutInformationRepository.findAllByUserLoginDetails(user);
            workoutCount = workouts.size();
            trainingVolume = workouts.stream()
                    .mapToDouble(workout -> workout.getSets() * workout.getReps() * workout.getWeight())
                    .sum();
        }

        model.addAttribute("dailyGoal", dailyGoal);
        model.addAttribute("caloriesConsumed", caloriesConsumed);
        model.addAttribute("remainingCalories", remainingCalories);
        model.addAttribute("calorieProgress", calorieProgress);
        model.addAttribute("mealCount", mealCount);
        model.addAttribute("workoutCount", workoutCount);
        model.addAttribute("trainingVolume", trainingVolume);
        model.addAttribute("macroInfo", macroInfo);

        return "home";
    }

    @GetMapping("/login")
    public String getLoginPage(){
        return "login";
    }
    @GetMapping("/")
    private String getIndex(){
        return "index";
    }

    @GetMapping("/signup")
    public String getSignUpPage(Model model) {
        model.addAttribute("userLoginDetails", new UserLoginDetails());
        model.addAttribute("userInformation", new UserInformation());
        return "Signin";
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
