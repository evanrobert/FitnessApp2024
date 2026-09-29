package Evan.Application.Fitness.Form;

import Evan.Application.Fitness.Model.CardioActivity;
import Evan.Application.Fitness.Model.LocationType;
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
        /** Exercise block this set belongs to (keeps order when an exercise repeats). */
        private Integer block;
        @Min(value = 0, message = "Reps can't be negative") @Max(value = 1000)
        private Integer reps;
        @DecimalMin(value = "0", message = "Load can't be negative") @DecimalMax("3000")
        private Double weightLb;
        @DecimalMin("1") @DecimalMax("10")
        private Double rpe;
        private boolean warmup;
        @Size(max = 500)
        private String notes;

        public boolean isBlank() {
            return exerciseId == null || (reps == null && weightLb == null);
        }
    }

    @Data
    public static class CardioRow {
        private CardioActivity activity;
        @DecimalMin(value = "0.1", message = "Add a duration") @DecimalMax("1440")
        private Double durationMin;
        @DecimalMin("0") @DecimalMax("500")
        private Double distanceMi;
        @Min(30) @Max(230)
        private Integer avgHr;
        @Min(0) @Max(10000)
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
