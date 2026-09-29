package Evan.Application.Fitness.Model;

/** Used only for energy-expenditure estimates. */
public enum Sex {
    FEMALE("Female"),
    MALE("Male"),
    UNSPECIFIED("Prefer not to say");

    private final String label;

    Sex(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
