package Evan.Application.Fitness.Model;

public enum SessionFocus {
    STRENGTH("Strength"),
    HYPERTROPHY("Hypertrophy"),
    CONDITIONING("Conditioning"),
    ACCESSORY("Accessory"),
    CARDIO("Cardio"),
    MOBILITY("Mobility"),
    SPORT("Sport");

    private final String label;

    SessionFocus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
