package Evan.Application.Fitness.Model;

public enum ExperienceLevel {
    BEGINNER("Beginner (< 1 yr)"),
    INTERMEDIATE("Intermediate (1-3 yrs)"),
    ADVANCED("Advanced (3+ yrs)");

    private final String label;

    ExperienceLevel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
