package Evan.Application.Fitness.Model;

public enum MetricCategory {
    ASSESSMENT("Fitness assessment"),
    HEALTH("Health marker"),
    HABIT("Habit"),
    PERFORMANCE("Performance"),
    OTHER("Other");

    private final String label;

    MetricCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
