package Evan.Application.Fitness.Model;

import java.util.List;

public class CalorieInformationForm {
    private List<CalorieInformation> calorieInformationList;

    // Must have getter and setter!
    public List<CalorieInformation> getCalorieInformationList() {
        return calorieInformationList;
    }

    public void setCalorieInformationList(List<CalorieInformation> calorieInformationList) {
        this.calorieInformationList = calorieInformationList;
    }
}

