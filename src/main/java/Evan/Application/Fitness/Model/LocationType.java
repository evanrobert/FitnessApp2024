package Evan.Application.Fitness.Model;

/** Where a session happened; GYM sessions count as gym attendance. */
public enum LocationType {
    GYM("Gym"),
    HOME("Home"),
    OUTDOORS("Outdoors"),
    OTHER("Other");

    private final String label;

    LocationType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
