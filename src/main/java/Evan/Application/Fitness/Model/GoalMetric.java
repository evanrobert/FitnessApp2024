package Evan.Application.Fitness.Model;

/** What a goal measures; the current value is read from the matching tracked data. */
public enum GoalMetric {
    BODY_WEIGHT("Body weight", "lb"),
    BODY_FAT("Body fat", "%"),
    WAIST("Waist", "in"),
    EXERCISE_1RM("Exercise estimated 1RM", "lb"),
    WORKOUTS_PER_WEEK("Workouts per week", "sessions"),
    CUSTOM_METRIC("Custom metric", "");

    private final String label;
    private final String unit;

    GoalMetric(String label, String unit) {
        this.label = label;
        this.unit = unit;
    }

    public String getLabel() {
        return label;
    }

    public String getUnit() {
        return unit;
    }
}
