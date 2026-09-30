package Evan.Application.Fitness.Form;

import Evan.Application.Fitness.Model.CardioActivity;
import Evan.Application.Fitness.Model.LocationType;
import Evan.Application.Fitness.Model.MuscleGroup;
import Evan.Application.Fitness.Model.SessionFocus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything logged for one session in a single submit: exercise blocks of sets
 * plus cardio. Blank rows are ignored, so the builder can always show spare rows.
 */
@Data
public class SessionForm {
    @NotNull(message = "Pick a date")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate sessionDate;

    @Size(max = 120)
    private String title;

    private SessionFocus focus;
    private LocationType locationType;

    @Min(value = 0, message = "Check the duration") @Max(value = 600, message = "Check the duration")
    private Integer durationMin;

    @Min(1) @Max(10)
    private Integer sessionRpe;

    @Size(max = 2000)
    private String notes;

    @Valid
    private List<SetRow> sets = new ArrayList<>();

    @Valid
    private List<CardioRow> cardio = new ArrayList<>();

    @Data
    public static class SetRow {
        private Long exerciseId;
        /** Free-form name: matched to the library or the member's exercises, else created as a personal one. */
        @Size(max = 100, message = "Exercise names are up to 100 characters")
        private String exerciseName;
        /** Muscle group for a newly typed exercise (ignored when the name already exists). */
        private MuscleGroup muscleGroup;
        /** Exercise block this set belongs to (keeps order when an exercise repeats). */
        private Integer block;
        @Min(value = 0, message = "Reps can't be negative") @Max(value = 1000, message = "Reps are 0–1000")
        private Integer reps;
        @DecimalMin(value = "0", message = "Load can't be negative") @DecimalMax(value = "3000", message = "Load is up to 3000 lb")
        private Double weightLb;
        @DecimalMin(value = "1", message = "RPE is 1–10 (10 = nothing left)") @DecimalMax(value = "10", message = "RPE is 1–10 (10 = nothing left)")
        private Double rpe;
        private boolean warmup;
        @Size(max = 500, message = "Set notes are up to 500 characters")
        private String notes;

        public boolean isBlank() {
            boolean noExercise = exerciseId == null && (exerciseName == null || exerciseName.isBlank());
            return noExercise || (reps == null && weightLb == null);
        }
    }

    @Data
    public static class CardioRow {
        private CardioActivity activity;
        @DecimalMin(value = "0.1", message = "Add a duration") @DecimalMax("1440")
        private Double durationMin;
        @DecimalMin(value = "0", message = "Distance can't be negative") @DecimalMax(value = "500", message = "Distance is up to 500 mi")
        private Double distanceMi;
        @Min(value = 30, message = "Heart rate is 30–230") @Max(value = 230, message = "Heart rate is 30–230")
        private Integer avgHr;
        @Min(value = 0, message = "Calories can't be negative") @Max(value = 10000, message = "Calories are up to 10,000")
        private Integer calories;
        @Size(max = 500)
        private String notes;

        public boolean isBlank() {
            return activity == null || durationMin == null;
        }
    }

    public boolean hasContent() {
        return sets.stream().anyMatch(s -> s != null && !s.isBlank()) || cardio.stream().anyMatch(c -> c != null && !c.isBlank());
    }
}
