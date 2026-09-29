package Evan.Application.Fitness.Model;

/** Everyday activity outside training; drives the maintenance-calorie estimate. */
public enum ActivityLevel {
    SEDENTARY("Mostly seated", 1.2),
    LIGHT("Lightly active", 1.375),
    MODERATE("Moderately active", 1.55),
    VERY("Very active", 1.725),
    ATHLETE("Athlete / physical job", 1.9);

    private final String label;
    private final double multiplier;

    ActivityLevel(String label, double multiplier) {
        this.label = label;
        this.multiplier = multiplier;
    }

    public String getLabel() {
        return label;
    }

    /** Multiplier applied to BMR for total daily energy expenditure. */
    public double getMultiplier() {
        return multiplier;
    }
}
