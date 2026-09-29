package Evan.Application.Fitness.Model;

public enum GoalStatus {
    ACTIVE("Active"),
    ACHIEVED("Achieved"),
    ARCHIVED("Archived");

    private final String label;

    GoalStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
