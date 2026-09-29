package Evan.Application.Fitness.Controller;

import Evan.Application.Fitness.Model.WorkoutInformation;
import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import Evan.Application.Fitness.Repositorys.WorkoutInformationRepository;
import Evan.Application.Fitness.Security.AppUserPrincipal;
import Evan.Application.Fitness.Service.TodayService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

@Controller
public class WorkoutInformationController {
    private final WorkoutInformationRepository workouts;
    private final UserLoginDetailsRepository users;
    private final TodayService todayService;

    public WorkoutInformationController(WorkoutInformationRepository workouts, UserLoginDetailsRepository users,
                                        TodayService todayService) {
        this.workouts = workouts;
        this.users = users;
        this.todayService = todayService;
    }

    @GetMapping("/workouts")
    public String view(@AuthenticationPrincipal AppUserPrincipal me, Model model) {
        if (!model.containsAttribute("workoutInformation")) {
            model.addAttribute("workoutInformation", new WorkoutInformation());
        }
        List<WorkoutInformation> history = workouts.findAllByUserIdOrderByDateDescIdDesc(me.getId());
        model.addAttribute("workouts", history);
        model.addAttribute("totalWorkouts", history.size());
        model.addAttribute("totalVolume", history.stream().mapToDouble(w -> w.getSets() * w.getReps() * w.getWeight()).sum());
        return "WorkoutLog";
    }

    @PostMapping("/workouts")
    public String log(@AuthenticationPrincipal AppUserPrincipal me,
                      @Valid @ModelAttribute WorkoutInformation workoutInformation, BindingResult result, Model model) {
        if (result.hasErrors()) {
            return view(me, model);
        }
        WorkoutInformation entry = new WorkoutInformation();
        entry.setExerciseName(workoutInformation.getExerciseName().trim());
        entry.setWorkoutType(workoutInformation.getWorkoutType());
        entry.setSets(workoutInformation.getSets());
        entry.setReps(workoutInformation.getReps());
        entry.setWeight(workoutInformation.getWeight());
        entry.setNotes(workoutInformation.getNotes());
        entry.setDate(workoutInformation.getDate() != null ? workoutInformation.getDate() : todayService.today(me.getId()));
        entry.setUser(users.getReferenceById(me.getId()));
        workouts.save(entry);
        return "redirect:/workouts";
    }
}
