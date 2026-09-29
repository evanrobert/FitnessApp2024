package Evan.Application.Fitness.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.format.annotation.DateTimeFormat;

/** A single set. Estimated 1RM uses the Epley formula. */
@Entity
@Table(name = "exercise_set")
@Getter
@Setter
@NoArgsConstructor
public class ExerciseSet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id")
    private WorkoutSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id")
    private Exercise exercise;

    /** Order of the exercise block within the session. */
    private int sortOrder;
    private int setNumber;
    private Integer reps;
    private Double weightLb;
    private Double rpe;
    private boolean warmup;

    @Size(max = 500)
    private String notes;

    public double volume() {
        return reps == null || weightLb == null ? 0 : reps * weightLb;
    }

    public double estimatedOneRepMax() {
        if (reps == null || weightLb == null || reps <= 0 || weightLb <= 0) {
            return 0;
        }
        return reps == 1 ? weightLb : weightLb * (1 + reps / 30.0);
    }
}
