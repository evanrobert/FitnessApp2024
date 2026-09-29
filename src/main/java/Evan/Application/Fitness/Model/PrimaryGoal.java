package Evan.Application.Fitness.Model;

public enum PrimaryGoal {
    LOSE_FAT("Lose fat"),
    BUILD_MUSCLE("Build muscle"),
    GET_STRONGER("Get stronger"),
    ENDURANCE("Build endurance"),
    MAINTAIN("Maintain"),
    GENERAL_HEALTH("General health");

    private final String label;

    PrimaryGoal(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
