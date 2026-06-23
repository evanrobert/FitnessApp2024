package Evan.Application.Fitness.Controller;

import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Model.WorkoutInformation;
import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import Evan.Application.Fitness.Repositorys.WorkoutInformationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.security.Principal;
import java.util.Date;
import java.util.List;

@Controller
public class WorkoutInformationController {
    @Autowired
    WorkoutInformationRepository workoutInformationRepository;
    @Autowired
    UserLoginDetailsRepository userLoginDetailsRepository;

    @GetMapping("/workouts")
    public String viewWorkouts(Model model, Principal principal) {
        UserLoginDetails user = userLoginDetailsRepository.findByUsername(principal.getName());
        List<WorkoutInformation> workouts = workoutInformationRepository.findAllByUserLoginDetails(user);

        model.addAttribute("workoutInformation", new WorkoutInformation());
        model.addAttribute("workouts", workouts);
        model.addAttribute("totalWorkouts", workouts.size());
        model.addAttribute("totalVolume", workouts.stream()
                .mapToDouble(workout -> workout.getSets() * workout.getReps() * workout.getWeight())
                .sum());

        return "WorkoutLog";
    }

    @PostMapping("/workouts")
    public String logWorkout(Principal principal, @ModelAttribute WorkoutInformation workoutInformation) {
        UserLoginDetails user = userLoginDetailsRepository.findByUsername(principal.getName());
        workoutInformation.setUserLoginDetails(user);

        if (workoutInformation.getDate() == null) {
            workoutInformation.setDate(new Date());
        }

        workoutInformationRepository.save(workoutInformation);
        return "redirect:/workouts";
    }
}
