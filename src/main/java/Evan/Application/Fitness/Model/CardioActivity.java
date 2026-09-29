package Evan.Application.Fitness.Model;

public enum CardioActivity {
    RUN("Run"),
    WALK("Walk"),
    BIKE("Bike"),
    ROW("Row"),
    SWIM("Swim"),
    ELLIPTICAL("Elliptical"),
    STAIRS("Stairs"),
    HIIT("HIIT"),
    OTHER("Other");

    private final String label;

    CardioActivity(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
