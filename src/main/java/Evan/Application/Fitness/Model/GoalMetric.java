package Evan.Application.Fitness.Model;

/** What a goal measures; the current value is read from the matching tracked data. */
public enum GoalMetric {
    BODY_WEIGHT("Body weight", "lb"),
    BODY_FAT("Body fat", "%"),
    WAIST("Waist", "in"),
    EXERCISE_WEIGHT("Lift a weight on an exercise", "lb"),
    EXERCISE_1RM("Estimated max on an exercise", "lb"),
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

    /** Goals measured from one exercise's sets. */
    public boolean usesExercise() {
        return this == EXERCISE_WEIGHT || this == EXERCISE_1RM;
    }
}
