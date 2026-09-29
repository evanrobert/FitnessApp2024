package Evan.Application.Fitness.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.format.annotation.DateTimeFormat;

/** A cardio block within a session. Pace is derived from duration and distance. */
@Entity
@Table(name = "cardio_entry")
@Getter
@Setter
@NoArgsConstructor
public class CardioEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id")
    private WorkoutSession session;

    @NotNull
    @Enumerated(EnumType.STRING)
    private CardioActivity activity;

    @Positive
    private double durationMin;

    private Double distanceMi;
    private Integer avgHr;
    private Integer calories;

    @Size(max = 500)
    private String notes;

    /** Minutes per mile, or null without a distance. */
    public Double paceMinPerMile() {
        return distanceMi == null || distanceMi <= 0 ? null : durationMin / distanceMi;
    }
}
