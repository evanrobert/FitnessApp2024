package Evan.Application.Fitness.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LogCustomMealController {
    @Autowired

    @GetMapping("/add/custom/meal")
    private String addCustomMealOption(Model model){
        model.addAttribute()
    }
}
