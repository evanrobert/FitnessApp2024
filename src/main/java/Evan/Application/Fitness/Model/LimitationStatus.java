package Evan.Application.Fitness.Model;

public enum LimitationStatus {
    ACTIVE("Active"),
    MANAGING("Managing"),
    RESOLVED("Resolved");

    private final String label;

    LimitationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
