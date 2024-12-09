package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Model.CalorieInformation;
import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Model.UserMacroInformation;
import Evan.Application.Fitness.Repositorys.CalorieInformationRepository;
import Evan.Application.Fitness.Repositorys.UserMacroInformationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CaloriesLeftService {

    @Autowired
    private CalorieInformationRepository calorieInformationRepository;

    public double getAllotedCalorieRemainder() {
        // Retrieve all calorie information for today
        List<CalorieInformation> todaysCalories = calorieInformationRepository.findAllByToday();


        // Sum up the calories for all meals
        double totalCalories = todaysCalories.stream()
                .mapToDouble(CalorieInformation::getCalories)
                .sum();

        // Print the total calories
        System.out.println("Total Calories Consumed Today: " + totalCalories);

        // Return the total calories
        return totalCalories;
    }
}


